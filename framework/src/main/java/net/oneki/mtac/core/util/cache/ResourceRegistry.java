package net.oneki.mtac.core.util.cache;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.FieldRepository;
import net.oneki.mtac.core.repository.FieldSchemaRepository;
import net.oneki.mtac.core.repository.ResourceRepository;
import net.oneki.mtac.core.repository.SchemaRepository;
import net.oneki.mtac.core.repository.framework.InitRepository;
import net.oneki.mtac.core.repository.framework.SchemaDbSynchronizer;
import net.oneki.mtac.core.util.exception.NotFoundException;
import net.oneki.mtac.core.util.exception.UnexpectedException;
import net.oneki.mtac.core.util.introspect.ClassType;
import net.oneki.mtac.core.util.introspect.ReflectorContext;
import net.oneki.mtac.core.util.introspect.ResourceDesc;
import net.oneki.mtac.core.util.introspect.ResourceField;
import net.oneki.mtac.core.util.introspect.ResourceReflector;
import net.oneki.mtac.core.util.introspect.annotation.ApiRequest;
import net.oneki.mtac.core.util.introspect.annotation.Entity;
import net.oneki.mtac.core.util.query.Query;
import net.oneki.mtac.resource.Resource;
import net.oneki.mtac.resource.ResourceRequest;
import net.oneki.mtac.resource.iam.Role;
import net.oneki.mtac.resource.iam.tenant.Tenant;
import net.oneki.mtac.resource.schema.Schema;

@Data
@RequiredArgsConstructor
@Component
public class ResourceRegistry {
    private static ResourceRegistry instance;
    protected final Cache cache;
    protected final ResourceRepository resourceRepository;
    protected final SchemaRepository schemaRepository;
    protected final FieldRepository fieldRepository;
    protected final FieldSchemaRepository fieldSchemaRepository;
    protected final SchemaDbSynchronizer schemaDbSynchronizer;
    protected final InitRepository initRepository;
    @Value("${mtac.scan.package}")
    private String scanBasePackage;

    private final static Map<String, Class<?>> classIndex = new HashMap<>();
    private final static Map<Class<?>, String> schemaIndex = new HashMap<>();
    private final static Map<String, ResourceDesc> resourceDescIndex = new HashMap<>();
    // private final static Map<String, PermissionSet> permissions = new
    // HashMap<>();
    private final static Map<Class<?>, Set<ResourceField>> relations = new HashMap<>();

    @PostConstruct
    public void initResourceRegistry() throws ClassNotFoundException {
        instance = this;
        initRepository.initSchema();
        var reflectorContext = ReflectorContext.builder()
                .classIndex(classIndex)
                .schemaIndex(schemaIndex)
                .resourceDescIndex(resourceDescIndex)
                .basePackage("net.oneki.mtac.resource")
                .classType(ClassType.Entity)
                .build();
        scanClasses(reflectorContext);
        reflectorContext.setBasePackage(scanBasePackage);
        scanClasses(reflectorContext);
        reflectorContext.setBasePackage("net.oneki.mtac.resource");
        reflectorContext.setClassType(ClassType.ApiRequest);
        scanClasses(reflectorContext);
        reflectorContext.setBasePackage(scanBasePackage);
        scanClasses(reflectorContext);
        var resourceClasses = new HashSet<>(schemaIndex.keySet());
        for (var resourceClass : resourceClasses) {
            ResourceReflector.reflect(resourceClass, reflectorContext);
        }

        for (var resourceDesc : resourceDescIndex.values()) {
            setFieldOwnerClass(resourceDesc);
            // if (resourceDesc.isInterface()) {
            //     for (var resourceField : resourceDesc.getFields()) {
            //         if (resourceField.getField() == null) {
            //             // find the field in the child classes
            //             for (var childClass : resourceDesc.getChildClasses()) {
            //                 var childResourceDesc = getResourceDesc(childClass);
            //                 var childResourceField = childResourceDesc.getField(resourceField.getLabel());
            //                 if (childResourceField != null && childResourceField.getField() != null) {
            //                     childResourceField.setOwnerClass(resourceDesc.getClazz());
            //                     resourceField.setField(childResourceField.getField());
            //                     break;
            //                 }
            //             }
            //             for (var childClass : resourceDesc.getChildClasses()) {
            //                 var childResourceDesc = getResourceDesc(childClass);
            //                 childResourceDesc.setFieldOwnerOrAddField(resourceField, resourceDesc.getClazz());
            //             }

            //         }
            //     }
            // }
        }
        handleFieldType();
        List<Schema> schemas = schemaRepository.listAllSchemasUnsecure();
        cache.setSchemas(schemas);
        schemaDbSynchronizer.syncSchemasToDb();
        schemaDbSynchronizer.syncFieldsToDb();

        List<Tenant> tenants = resourceRepository.listByTypeUnsecure(Tenant.class,
                Query.fromRest(Map.of("sortBy", "id"), Tenant.class));
        cache.setTenants(tenants);

        List<Role> roles = resourceRepository.listByTypeUnsecure(Role.class,
                Query.fromRest(Map.of("sortBy", "id"), Role.class));
        cache.setRoles(roles);

    }

    private void setFieldOwnerClass(ResourceDesc resourceDesc) {
        for (var resourceField : resourceDesc.getFields()) {
            if (resourceField.getField() == null) {
                // find the field in the child classes
                for (var childClass : resourceDesc.getChildClasses()) {
                    var childResourceDesc = getResourceDesc(childClass);
                    var childResourceField = childResourceDesc.getField(resourceField.getLabel());
                    if (childResourceField != null && childResourceField.getField() != null) {
                        childResourceField.setOwnerClass(resourceDesc.getClazz());
                        resourceField.setField(childResourceField.getField());
                        break;
                    }
                }
            }
            if (resourceField.getImpClasses().isEmpty()) {
                resourceField.getImpClasses().add(resourceDesc.getClazz());
                for (var childClass : resourceDesc.getChildClasses()) {
                    var childResourceDesc = getResourceDesc(childClass);
                    childResourceDesc.setFieldOwnerOrAddField(resourceField, resourceDesc.getClazz());
                }
            }
        }
    }

    // ------------------------------------------------- Registry ------------
    public static Class<?> getClassBySchema(String schemaLabel) {
        return classIndex.get(schemaLabel);
    }

    public static String getSchemaByClass(Class<?> resourceClass) {
        return schemaIndex.get(resourceClass);
    }

    public static ResourceDesc getResourceDesc(String schemaLabel) {
        var result = resourceDescIndex.get(schemaLabel);
        if (result == null) {
            throw new UnexpectedException("INVALID_SCHEMA_LABEL",
                    "The schema label " + schemaLabel + " is not valid (no Java class found for this schema)");
        }
        return result;
    }

    public static Set<ResourceField> getRelations(Class<?> resourceClass) {
        var result = relations.get(resourceClass);
        if (result == null) {
            var resourceDesc = getResourceDesc(resourceClass);
            relations.put(resourceClass, resourceDesc.getFields().stream().filter(ResourceField::isRelation)
                    .collect(Collectors.toSet()));
            result = relations.get(resourceClass);
        }

        return result;
    }

    public static ResourceDesc getResourceDesc(Class<?> resourceClass) {
        var schemaLabel = getSchemaByClass(resourceClass);
        ResourceDesc result = null;
        if (schemaLabel == null && (resourceClass.isAssignableFrom(Resource.class) ||
                resourceClass.isAssignableFrom(ResourceRequest.class))) {
            throw new NotFoundException("INVALID_RESOURCE_CLASS",
                    "The class " + resourceClass.getCanonicalName()
                            + " is not a valid resource class (check if it is annotated with @Entity or @ApiRequest)");
        } else if (schemaLabel != null) {
            result = getResourceDesc(schemaLabel);
        }
        return result;
    }

    public static Map<String, Class<?>> getClassindex() {
        return classIndex;
    }

    public static Map<Class<?>, String> getSchemaIndex() {
        return schemaIndex;
    }

    public static Map<String, ResourceDesc> getResourceDescs() {
        return resourceDescIndex;
    }

    public static String getUniqueInScope(Class<?> resourceClass) {
        var resourceDesc = getResourceDesc(resourceClass);
        return resourceDesc.getUniqueInScope();
    }

    // ------------------------------------------------- Cache facade ------------
    public static void addTenant(Tenant tenant) {
        instance.getCache().addTenant(tenant);
    }

    public static void deleteTenant(Integer tenantId) {
        instance.getCache().deleteTenant(tenantId);
    }

    public static Schema getSchemaById(Integer id) {
        var result = instance.getCache().getSchemaById(id);
        if (result == null) {
            result = instance.getResourceRepository().getByIdUnsecure(id, Schema.class);
            if (result != null) {
                instance.getCache().addSchema(result);
            }
        }
        return result;
    }

    public static Integer getSchemaId(String schemaLabel) {
        return instance.getCache().getSchemaId(schemaLabel);
    }

    public static Integer getSchemaId(Class<?> resourceClass) {
        return instance.getCache().getSchemaId(getSchemaByClass(resourceClass));
    }

    public static String getSchemaLabel(Integer schemaId) {
        var schema = instance.getCache().getSchemaById(schemaId);
        if (schema == null)
            return null;
        return schema.getLabel();
    }

    public static Schema getSchemaByLabel(String schemaLabel) {
        return instance.getCache().getSchemaByLabel(schemaLabel);
    }

    public static Integer getTenantId(String tenantLabel) {
        return instance.getCache().getTenantId(tenantLabel);
    }

    public static String getTenantLabel(Integer tenantId) {
        var tenant = instance.getCache().getTenantById(tenantId);
        if (tenant == null)
            return null;
        return tenant.getLabel();
    }

    public static Tenant getTenantById(Integer id) {
        var result = instance.getCache().getTenantById(id);
        if (result == null) {
            result = instance.getResourceRepository().getByIdUnsecure(id, Tenant.class);
            if (result != null) {
                instance.getCache().addTenant(result);
            }
        }
        return result;
    }

    public static Tenant getTenantByLabel(String tenantLabel) {
        return instance.getCache().getTenantByLabel(tenantLabel);
    }

    public static Map<Integer, Schema> getSchemas() {
        return instance.getCache().getSchemas();
    }

    public static Map<Integer, Tenant> getTenants() {
        return instance.getCache().getTenants();
    }

    // ------------------------------------------------- private methods
    // private void scanEntities(String basePackage, ReflectorContext reflectorContext) throws ClassNotFoundException {
    //     ClassPathScanningCandidateComponentProvider entityProvider = new ClassPathScanningCandidateComponentProvider(
    //             false);
    //     // entityProvider.addIncludeFilter(new
    //     // AnnotationTypeFilter(EntityInterface.class));
    //     entityProvider.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

    //     var beanDefs = entityProvider.findCandidateComponents(basePackage);

    //     for (BeanDefinition bd : beanDefs) {
    //         if (bd instanceof AnnotatedBeanDefinition) {
    //             Map<String, Object> annotAttributeMap = ((AnnotatedBeanDefinition) bd)
    //                     .getMetadata()
    //                     .getAnnotationAttributes(Entity.class.getCanonicalName());
    //             var schemaLabel = (String) annotAttributeMap.get("value");
    //             var resourceClass = (Class<?>) Class.forName(bd.getBeanClassName());
    //             if (schemaLabel == null || "".equals(schemaLabel)) {
    //                 schemaLabel = ResourceReflector.buildSchemaLabel(resourceClass, reflectorContext);
    //             }

    //             addResourceClass(schemaLabel.toString(), resourceClass);
    //         }
    //     }


    // }

    // private void scanApiRequests(String basePackage, ReflectorContext reflectorContext) throws ClassNotFoundException {
    //     ClassPathScanningCandidateComponentProvider entityProvider = new ClassPathScanningCandidateComponentProvider(
    //             false);
    //     entityProvider.addIncludeFilter(new AnnotationTypeFilter(ApiRequest.class));

    //     var beanDefs = entityProvider.findCandidateComponents(basePackage);

    //     for (BeanDefinition bd : beanDefs) {
    //         if (bd instanceof AnnotatedBeanDefinition) {
    //             Map<String, Object> annotAttributeMap = ((AnnotatedBeanDefinition) bd)
    //                     .getMetadata()
    //                     .getAnnotationAttributes(ApiRequest.class.getCanonicalName());
    //             var schemaLabel = (String) annotAttributeMap.get("value");
    //             var resourceClass = (Class<?>) Class.forName(bd.getBeanClassName());
    //             if (schemaLabel == null || "".equals(schemaLabel)) {
    //                 schemaLabel = ResourceReflector.buildSchemaLabel(resourceClass, reflectorContext);
    //             }

    //             addResourceClass(schemaLabel.toString(), resourceClass);
    //             addResourceDesc(schemaLabel, ResourceReflector.reflect(resourceClass, annotAttributeMap));
    //         }
    //     }
    // }

    private void scanClasses(ReflectorContext reflectorContext) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider entityProvider = new ClassPathScanningCandidateComponentProvider(
                false);
        Class<? extends Annotation> clazz = switch (reflectorContext.getClassType()) {
            case Entity -> Entity.class;
            case ApiRequest -> ApiRequest.class;
            default -> null;
        };
        entityProvider.addIncludeFilter(new AnnotationTypeFilter(clazz));
        var beanDefs = entityProvider.findCandidateComponents(reflectorContext.getBasePackage());
        for (BeanDefinition bd : beanDefs) {
            if (bd instanceof AnnotatedBeanDefinition) {
                Map<String, Object> annotAttributeMap = ((AnnotatedBeanDefinition) bd)
                        .getMetadata()
                        .getAnnotationAttributes(clazz.getCanonicalName());
                var schemaLabel = (String) annotAttributeMap.get("value");
                var resourceClass = (Class<?>) Class.forName(bd.getBeanClassName());
                if (schemaLabel == null || "".equals(schemaLabel)) {
                    schemaLabel = ResourceReflector.buildSchemaLabel(resourceClass, reflectorContext);
                }

                addResourceClass(schemaLabel.toString(), resourceClass);
            }
        }
    }

    private void handleFieldType() {
        // During the first pass of reflection, it could happen that the type of a field
        // is set to object
        // instead of the real type. This is because the class of the field is not yet
        // loaded.
        // Therefore we do a second pass to set the correct type (after evrything is
        // loaded).
        for (ResourceDesc resourceDesc : resourceDescIndex.values()) {
            for (ResourceField field : resourceDesc.getFields()) {
                if ("object".equals(field.getType())) {
                    var type = ResourceReflector.getType(field.getFieldClass());
                    field.setType(type);
                }
            }
        }
    }

    private void addResourceClass(String schemaLabel, Class<?> resourceClass) {
        classIndex.put(schemaLabel, resourceClass);
        schemaIndex.put(resourceClass, schemaLabel);
    }

    private void addResourceDesc(String schemaLabel, ResourceDesc resourceDesc) {
        resourceDescIndex.put(schemaLabel, resourceDesc);
    }

    // public static Map<String, PermissionSet> getPermissions() {
    // return permissions;
    // }

    // public static PermissionSet getPermissionSet(String schemaLabel) {
    // return permissions.get(schemaLabel);
    // }

    // public static PermissionSet getPermissionSet(Class<?> resourceClass) {
    // var schemaLabel = getSchemaLabel(resourceClass);
    // return getPermissionSet(schemaLabel);
    // }

}
