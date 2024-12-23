package net.oneki.mtac.util.cache;

import java.util.HashMap;
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
import net.oneki.mtac.model.api.ResourceRequest;
import net.oneki.mtac.model.entity.Resource;
import net.oneki.mtac.model.entity.Schema;
import net.oneki.mtac.model.entity.Tenant;
import net.oneki.mtac.model.entity.iam.Role;
import net.oneki.mtac.repository.FieldRepository;
import net.oneki.mtac.repository.FieldSchemaRepository;
import net.oneki.mtac.repository.ResourceRepository;
import net.oneki.mtac.repository.SchemaRepository;
import net.oneki.mtac.repository.framework.InitRepository;
import net.oneki.mtac.repository.framework.SchemaDbSynchronizer;
import net.oneki.mtac.util.StringUtils;
import net.oneki.mtac.util.cache.ResourceRegistry;
import net.oneki.mtac.util.exception.NotFoundException;
import net.oneki.mtac.util.exception.UnexpectedException;
import net.oneki.mtac.util.introspect.RelationField;
import net.oneki.mtac.util.introspect.ResourceDesc;
import net.oneki.mtac.util.introspect.ResourceField;
import net.oneki.mtac.util.introspect.ResourceReflector;
import net.oneki.mtac.util.introspect.annotation.ApiRequest;
import net.oneki.mtac.util.introspect.annotation.Entity;
import net.oneki.mtac.util.introspect.annotation.EntityInterface;
import net.oneki.mtac.util.query.Query;

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
    @Value("${mtac.scan.package.entity}")
    private String entityPackage;
    @Value("${mtac.scan.package.apiRequest}")
    private String apiRequestPackage;

    private final static Map<String, Class<?>> classIndex = new HashMap<>();
    private final static Map<Class<?>, String> schemaIndex = new HashMap<>();
    private final static Map<String, ResourceDesc> resourceDescIndex = new HashMap<>();
    // private final static Map<String, PermissionSet> permissions = new
    // HashMap<>();
    private final static Map<Class<?>, Set<RelationField>> relations = new HashMap<>();

    @PostConstruct
    public void initResourceRegistry() throws ClassNotFoundException {
        instance = this;
        initRepository.initSchema();
        scanEntities("net.oneki.mtac.model.entity");
        scanEntities(entityPackage);
        scanApiRequests("net.oneki.mtac.model.api");
        scanApiRequests(apiRequestPackage);
        handleFieldType();
        List<Schema> schemas = schemaRepository.listAllSchemasUnsecure();
        cache.setSchemas(schemas);
        schemaDbSynchronizer.syncSchemaToDb();
        schemaDbSynchronizer.syncFieldsToDb();

        List<Tenant> tenants = resourceRepository.listByTypeUnsecure(Tenant.class,
                Query.fromRest(Map.of("sortBy", "id"), Tenant.class));
        cache.setTenants(tenants);

        List<Role> roles = resourceRepository.listByTypeUnsecure(Role.class,
                Query.fromRest(Map.of("sortBy", "id"), Role.class));
        cache.setRoles(roles);

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

    public static Set<RelationField> getRelations(Class<?> resourceClass) {
        var result = relations.get(resourceClass);
        if (result == null) {
            var resourceDesc = getResourceDesc(resourceClass);
            relations.put(resourceClass, resourceDesc.getFields().stream().filter(ResourceField::isRelation)
                    .map(f -> (RelationField) f).collect(Collectors.toSet()));
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

    public static Schema getSchemaByLabel(String schemaLabel) {
        return instance.getCache().getSchemaByLabel(schemaLabel);
    }

    public static Integer getTenantId(String tenantLabel) {
        return instance.getCache().getTenantId(tenantLabel);
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
    private void scanEntities(String basePackage) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider entityProvider = new ClassPathScanningCandidateComponentProvider(
                false);
        entityProvider.addIncludeFilter(new AnnotationTypeFilter(EntityInterface.class));
        entityProvider.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        var beanDefs = entityProvider.findCandidateComponents(basePackage);

        for (BeanDefinition bd : beanDefs) {
            if (bd instanceof AnnotatedBeanDefinition) {
                Map<String, Object> annotAttributeMap = ((AnnotatedBeanDefinition) bd)
                        .getMetadata()
                        .getAnnotationAttributes(Entity.class.getCanonicalName());
                var schemaLabel = (String) annotAttributeMap.get("value");
                if (schemaLabel == null || "".equals(schemaLabel)) {
                    schemaLabel = bd.getBeanClassName().substring(basePackage.length() + 1);
                    var substrLength = 0;
                    if (schemaLabel.endsWith("Entity")) {
                        substrLength = 6;
                    } 
                    var tokens = schemaLabel.split("\\.");
                    tokens[tokens.length - 1] = StringUtils.pascalToCamel(
                            tokens[tokens.length - 1].substring(0, tokens[tokens.length - 1].length() - substrLength));
                    schemaLabel = String.join(".", tokens);
                    schemaLabel = StringUtils.pascalToSnake(schemaLabel);
                    
                }
                var resourceClass = (Class<?>) Class.forName(bd.getBeanClassName());
                addResourceClass(schemaLabel.toString(), resourceClass);
                addResourceDesc(schemaLabel, ResourceReflector.reflect(resourceClass, annotAttributeMap));
            }
        }
    }

    private void handleFieldType() {
        // During the first pass of reflection, it could happen that the type of a field is set to object
        // instead of the real type. This is because the class of the field is not yet loaded.
        // Therefore we do a second pass to set the correct type (after evrything is loaded).
        for(ResourceDesc resourceDesc : resourceDescIndex.values()) {
            for(ResourceField field : resourceDesc.getFields()) {
                if("object".equals(field.getType())) {
                    var type = ResourceReflector.getType(field.getFieldClass());
                    field.setType(type);
                }
            }
        }
    }

    private void scanApiRequests(String basePackage) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider entityProvider = new ClassPathScanningCandidateComponentProvider(
                false);
        entityProvider.addIncludeFilter(new AnnotationTypeFilter(ApiRequest.class));

        var beanDefs = entityProvider.findCandidateComponents(basePackage);

        for (BeanDefinition bd : beanDefs) {
            if (bd instanceof AnnotatedBeanDefinition) {
                Map<String, Object> annotAttributeMap = ((AnnotatedBeanDefinition) bd)
                        .getMetadata()
                        .getAnnotationAttributes(ApiRequest.class.getCanonicalName());
                var schemaLabel = (String) annotAttributeMap.get("value");
                if (schemaLabel == null || "".equals(schemaLabel)) {
                    var tokens = bd.getBeanClassName().split("\\.");
                    var dtoName = tokens[tokens.length - 2]; // e.g: "user"
                    var requestType = tokens[tokens.length - 1]; // e.g: "UserCreateRequest"
                    if (!requestType.endsWith("Request")) {
                        throw new UnexpectedException("INVALID_ANNOTATION", "The class " + bd.getBeanClassName()
                                + " annoted with @ApiRequest must end with 'Request'.");
                    }
                    var action = requestType.substring(dtoName.length(), requestType.length() - 7); // e.g: "Create"

                    schemaLabel = bd.getBeanClassName().substring(basePackage.length() + 1,
                            bd.getBeanClassName().lastIndexOf("."));
                    schemaLabel = "req." + schemaLabel + ":" + StringUtils.pascalToCamel(action);
                    schemaLabel = StringUtils.pascalToSnake(schemaLabel);
                }
                var resourceClass = (Class<?>) Class.forName(bd.getBeanClassName());
                addResourceClass(schemaLabel.toString(), resourceClass);
                addResourceDesc(schemaLabel, ResourceReflector.reflect(resourceClass, annotAttributeMap));
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
