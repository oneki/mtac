package net.oneki.mtac.framework.cache;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.introspect.ClassType;
import net.oneki.mtac.framework.introspect.ReflectorContext;
import net.oneki.mtac.framework.introspect.ResourceDesc;
import net.oneki.mtac.framework.introspect.ResourceField;
import net.oneki.mtac.framework.introspect.ResourceReflector;
import net.oneki.mtac.framework.repository.ResourceRepositorySync;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.util.exception.NotFoundException;
import net.oneki.mtac.model.core.util.exception.UnexpectedException;
import net.oneki.mtac.model.core.util.introspect.annotation.ApiRequest;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.ResourceRequest;
import net.oneki.mtac.model.resource.Tenant;
import net.oneki.mtac.model.resource.schema.Schema;

@Data
@RequiredArgsConstructor
@Component
public class ResourceRegistry {
    protected static final Cache cache = new Cache();
    protected final static boolean isReactive;
    static {
        var reactive = false;
        try {
            Class.forName("net.oneki.mtac.reactive.MtacConfig.java");
            reactive = true;
        } catch(Exception e){
        } finally {
            isReactive = reactive;
        }
    }

    protected final static Map<String, Class<?>> classIndex = new HashMap<>();
    protected final static Map<Class<?>, String> schemaIndex = new HashMap<>();
    protected final static Map<String, ResourceDesc> resourceDescIndex = new HashMap<>();
    // protected final static Map<String, PermissionSet> permissions = new
    // HashMap<>();
    protected final static Map<Class<?>, Set<ResourceField>> relations = new HashMap<>();
    protected static ResourceRepositorySync resourceRepository;

    public static void init(String scanBasePackage) throws ClassNotFoundException {
        cache.addSchema(Schema.builder()
                .id(Constants.SCHEMA_SCHEMA_ID)
                .urn(String.format("urn:%s:%s:%s", Constants.TENANT_ROOT_LABEL, Constants.SCHEMA_SCHEMA_LABEL,
                        Constants.SCHEMA_SCHEMA_LABEL))
                .pub(true)
                .build());
        cache.addTenant(Tenant.builder()
                .id(Constants.TENANT_ROOT_ID)
                .urn(String.format("urn::%s:%s", Constants.TENANT_ROOT_SCHEMA_LABEL, Constants.TENANT_ROOT_LABEL))
                .pub(false)
                .build());
        var reflectorContext = ReflectorContext.builder()
                .classIndex(classIndex)
                .schemaIndex(schemaIndex)
                .resourceDescIndex(resourceDescIndex)
                .basePackage(scanBasePackage)
                .classType(ClassType.Entity)
                .build();
        scanClasses(reflectorContext);
        reflectorContext.setBasePackage("net.oneki.mtac.model.resource");
        scanClasses(reflectorContext);
        reflectorContext.setBasePackage(scanBasePackage);
        reflectorContext.setClassType(ClassType.ApiRequest);
        scanClasses(reflectorContext);
        reflectorContext.setBasePackage("net.oneki.mtac.model.resource");
        scanClasses(reflectorContext);
        var resourceClasses = new HashSet<>(schemaIndex.keySet());
        for (var resourceClass : resourceClasses) {
            ResourceReflector.reflect(resourceClass, reflectorContext);
        }

        for (var resourceDesc : resourceDescIndex.values()) {
            setFieldOwnerClass(resourceDesc);
            // if (resourceDesc.isInterface()) {
            // for (var resourceField : resourceDesc.getFields()) {
            // if (resourceField.getField() == null) {
            // // find the field in the child classes
            // for (var childClass : resourceDesc.getChildClasses()) {
            // var childResourceDesc = getResourceDesc(childClass);
            // var childResourceField =
            // childResourceDesc.getField(resourceField.getLabel());
            // if (childResourceField != null && childResourceField.getField() != null) {
            // childResourceField.setOwnerClass(resourceDesc.getClazz());
            // resourceField.setField(childResourceField.getField());
            // break;
            // }
            // }
            // for (var childClass : resourceDesc.getChildClasses()) {
            // var childResourceDesc = getResourceDesc(childClass);
            // childResourceDesc.setFieldOwnerOrAddField(resourceField,
            // resourceDesc.getClazz());
            // }

            // }
            // }
            // }
        }
        handleFieldType();
    }

    public static Cache getCache() {
        return cache;
    }

    protected static void setFieldOwnerClass(ResourceDesc resourceDesc) {
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
        cache.addTenant(tenant);
    }

    public static void deleteTenant(Integer tenantId) {
        cache.deleteTenant(tenantId);
    }


    public static Schema getSchemaById(Integer id) {
        var result = cache.getSchemaById(id);
        if (result == null && resourceRepository != null) {
            result = resourceRepository.getByIdUnsecureSync(id, Schema.class);
            if (result != null) {
                cache.addSchema(result);
            }
        }
        return result;
    }

    public static Tenant getTenantById(Integer id) {
        var result = cache.getTenantById(id);
        if (result == null && resourceRepository != null) {
            result = resourceRepository.getByIdUnsecureSync(id, Tenant.class);
            if (result != null) {
                cache.addTenant(result);
            }
        }
        return result;
    }

    public static Tenant getTenantByLabel(String tenantLabel) {
        var result = cache.getTenantByLabel(tenantLabel);
        if (result == null && resourceRepository != null) {
            result = resourceRepository.getByUniqueLabelUnsecureSync(tenantLabel, Tenant.class);
            if (result != null) {
                cache.addTenant(result);
            }
        }
        return result;
    }

    public static Integer getSchemaId(String schemaLabel) {
        return cache.getSchemaId(schemaLabel);
    }

    public static Integer getSchemaId(Class<?> resourceClass) {
        return cache.getSchemaId(getSchemaByClass(resourceClass));
    }

    public static String getSchemaLabel(Integer schemaId) {
        var schema = cache.getSchemaById(schemaId);
        if (schema == null)
            return null;
        return schema.getLabel();
    }

    public static Schema getSchemaByLabel(String schemaLabel) {
        return cache.getSchemaByLabel(schemaLabel);
    }

    public static Integer getTenantId(String tenantLabel) {
        var tenant = getTenantByLabel(tenantLabel);
        if (tenant == null)
            return null;
        return tenant.getId();
    }

    public static String getTenantLabel(Integer tenantId) {
        var tenant = getTenantById(tenantId);
        if (tenant == null)
            return null;
        return tenant.getLabel();
    }

    public static Map<Integer, Schema> getSchemas() {
        return cache.getSchemas();
    }

    public static Map<Integer, Tenant> getTenants() {
        return cache.getTenants();
    }

    // ------------------------------------------------- protected methods
    // protected void scanEntities(String basePackage, ReflectorContext
    // reflectorContext) throws ClassNotFoundException {
    // ClassPathScanningCandidateComponentProvider entityProvider = new
    // ClassPathScanningCandidateComponentProvider(
    // false);
    // // entityProvider.addIncludeFilter(new
    // // AnnotationTypeFilter(EntityInterface.class));
    // entityProvider.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

    // var beanDefs = entityProvider.findCandidateComponents(basePackage);

    // for (BeanDefinition bd : beanDefs) {
    // if (bd instanceof AnnotatedBeanDefinition) {
    // Map<String, Object> annotAttributeMap = ((AnnotatedBeanDefinition) bd)
    // .getMetadata()
    // .getAnnotationAttributes(Entity.class.getCanonicalName());
    // var schemaLabel = (String) annotAttributeMap.get("value");
    // var resourceClass = (Class<?>) Class.forName(bd.getBeanClassName());
    // if (schemaLabel == null || "".equals(schemaLabel)) {
    // schemaLabel = ResourceReflector.buildSchemaLabel(resourceClass,
    // reflectorContext);
    // }

    // addResourceClass(schemaLabel.toString(), resourceClass);
    // }
    // }

    // }

    // protected void scanApiRequests(String basePackage, ReflectorContext
    // reflectorContext) throws ClassNotFoundException {
    // ClassPathScanningCandidateComponentProvider entityProvider = new
    // ClassPathScanningCandidateComponentProvider(
    // false);
    // entityProvider.addIncludeFilter(new AnnotationTypeFilter(ApiRequest.class));

    // var beanDefs = entityProvider.findCandidateComponents(basePackage);

    // for (BeanDefinition bd : beanDefs) {
    // if (bd instanceof AnnotatedBeanDefinition) {
    // Map<String, Object> annotAttributeMap = ((AnnotatedBeanDefinition) bd)
    // .getMetadata()
    // .getAnnotationAttributes(ApiRequest.class.getCanonicalName());
    // var schemaLabel = (String) annotAttributeMap.get("value");
    // var resourceClass = (Class<?>) Class.forName(bd.getBeanClassName());
    // if (schemaLabel == null || "".equals(schemaLabel)) {
    // schemaLabel = ResourceReflector.buildSchemaLabel(resourceClass,
    // reflectorContext);
    // }

    // addResourceClass(schemaLabel.toString(), resourceClass);
    // addResourceDesc(schemaLabel, ResourceReflector.reflect(resourceClass,
    // annotAttributeMap));
    // }
    // }
    // }

    protected static void scanClasses(ReflectorContext reflectorContext) throws ClassNotFoundException {
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

    protected static void handleFieldType() {
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

    protected static void addResourceClass(String schemaLabel, Class<?> resourceClass) {
        var clazz = classIndex.get(schemaLabel);
        if (clazz != null && !clazz.equals(resourceClass) && resourceClass.getName().startsWith("net.oneki.mtac")) {
            classIndex.put("mtac." + schemaLabel, resourceClass);
            schemaIndex.put(resourceClass, "mtac." + schemaLabel);
        } else {
            classIndex.put(schemaLabel, resourceClass);
            schemaIndex.put(resourceClass, schemaLabel);
        }

    }

    protected static void addResourceDesc(String schemaLabel, ResourceDesc resourceDesc) {
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
