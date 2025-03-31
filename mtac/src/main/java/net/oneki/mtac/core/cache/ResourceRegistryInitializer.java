package net.oneki.mtac.core.cache;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.framework.repository.InitRepository;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.framework.repository.SchemaDbSynchronizer;
import net.oneki.mtac.framework.repository.SchemaRepository;
import net.oneki.mtac.model.resource.iam.Role;
import net.oneki.mtac.model.resource.iam.tenant.Tenant;
import net.oneki.mtac.model.resource.schema.Schema;

@Data
@RequiredArgsConstructor
@Component
public class ResourceRegistryInitializer {
    protected final ResourceRepository resourceRepository;
    protected final SchemaRepository schemaRepository;
    protected final SchemaDbSynchronizer schemaDbSynchronizer;
    protected final InitRepository initRepository;

    @Value("${mtac.scan.package}")
    private String scanBasePackage;

    @PostConstruct
    public void initResourceRegistry() throws ClassNotFoundException {
        ResourceRegistry.init(scanBasePackage);
        initRepository.initSchema();
        
        List<Schema> schemas = schemaRepository.listAllSchemasUnsecure();
        var cache = ResourceRegistry.getCache();
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
}
