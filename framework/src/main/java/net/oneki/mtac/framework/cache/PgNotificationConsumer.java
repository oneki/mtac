package net.oneki.mtac.framework.cache;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.postgresql.PGNotification;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.framework.repository.ResourceTenantTreeRepository;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.Tenant;
import net.oneki.mtac.model.resource.schema.Schema;


@Component
@Slf4j
@RequiredArgsConstructor
public class PgNotificationConsumer implements Consumer<PGNotification> {
    private final TokenRegistry tokenRegistry;
    private final TokenRepository tokenRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceTenantTreeRepository resourceTenantTreeRepository;

    @Override
    public void accept(PGNotification message) {
        var cache = ResourceRegistry.getCache();
        try {
            log.info("Notification received: channel={}, value={}", message.getName(), message.getParameter());
            if (message.getName().equals(PgNotifierService.RESOURCE_CHANNEL)) {
                String value = message.getParameter();
                if (value != null) {
                    String[] tokens = value.split(",");
                    String action = tokens[0];
                    Integer id = Integer.valueOf(tokens[1]);
                    Integer tenantId = Integer.valueOf(tokens[2]);
                    Integer schemaId = Integer.valueOf(tokens[3]);
                    String label = String.join(",", Arrays.copyOfRange(tokens, 4, tokens.length));
                    List<Integer> tenantSchemaIds = List
                            .of("tenant.root", "tenant.company.partner", "tenant.company.customer", "tenant.site", "tenant.rg").stream()
                            .map(s -> cache.getSchemaId(s)).collect(Collectors.toList());
                    boolean isSchema = schemaId.equals(Constants.SCHEMA_SCHEMA_ID);
                    boolean isTenant = tenantSchemaIds.contains(schemaId);

                    switch (action) {
                        case "delete":
                            if (isSchema) {
                                log.info("Delete schema id={}, label={} from cache", id,
                                        cache.getSchemaById(id).getLabel());
                                cache.deleteSchema(id);
                            }
                            if (isTenant) {
                                log.info("Delete tenant id={}, label={} from cache", id,
                                        cache.getTenantById(id).getLabel());
                                cache.deleteTenant(id);
                            }
                            break;
                        case "create":
                            if (isSchema) {
                                log.info("Add schema id={}, label={} to cache", id, label);
                                cache.addSchema(
                                        Schema.builder()
                                            .id(id)
                                            .uid(Resource.toUid(id))
                                            .label(label)
                                            .schemaId(Constants.SCHEMA_SCHEMA_ID)
                                            .schemaLabel(Constants.SCHEMA_SCHEMA_LABEL)
                                            .tenantId(Constants.TENANT_ROOT_ID)
                                            .tenantLabel(Constants.TENANT_ROOT_LABEL)
                                            .build());
                            }
                            if (isTenant) {
                                log.info("Add tenant id={}, label={} to cache", id, label);
                                var parentTenantLabel = "";
                                if (tenantId != null) {
                                    var parentTenant = ResourceRegistry.getTenantById(tenantId);
                                    if (parentTenant != null) {
                                        parentTenantLabel = parentTenant.getLabel();
                                    }
                                }
                                var schema = ResourceRegistry.getSchemaById(schemaId);
                                
                                cache.addTenant(
                                        Tenant.builder()
                                            .id(id)
                                            .uid(Resource.toUid(id))
                                            .label(label)
                                            .schemaLabel(schema.getLabel())
                                            .schemaId(schemaId)
                                            .tenantLabel(parentTenantLabel)
                                            .tenantId(tenantId)
                                            .build());
                            }
                            break;

                    }
                }
            } else if (message.getName().equals(PgNotifierService.TOKEN_CHANNEL)) {
                String value = message.getParameter();
                if (value != null) {
                    var tokens = value.split(",");
                    var action = tokens[0];
                    var sub = tokens[1];
                    switch (action) {
                        case "delete":
                            log.info("Delete token id={} from cache", sub);
                            tokenRegistry.remove(sub);
                            break;
                        case "create":
                        case "update":
                            log.info(action + " token id={} to cache", sub);
                            var claims = tokenRepository.getToken(sub);
                            if (claims != null) {
                                tokenRegistry.put(sub, claims);
                            }
                            break;
                    }
                }

            } else if (message.getName().equals(PgNotifierService.RESOURCE_TENANT_TREE_CHANNEL)) {
                String value = message.getParameter();
                if (value != null) {
                    String[] tokens = value.split(",");
                    Integer descendantId = Integer.valueOf(tokens[2]);
                    Resource resource = resourceRepository.getByIdUnsecure(descendantId, Resource.class);
                    if (resource != null) {
                        boolean isTenant = resource.getSchemaLabel().startsWith("tenant.");
                        if (isTenant) {
                            var ancestors = resourceTenantTreeRepository.listTenantAncestors(descendantId);
                            log.info("Add ancestors ids <{}> of tenant {} to cache", ancestors, resource.getLabel());
                            cache.setTenantAncestor(descendantId, ancestors);
                        }
                    }
                }
            }

        } catch (Exception e) {
            if (message != null) {
                log.error("Failed to parse PostgreSQL notification: channel={}, value={} with error={}",
                        message.getName(), message.getParameter(), e.getMessage());
            }

        }

    }

}
