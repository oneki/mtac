package net.oneki.mtac.core.cache;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.postgresql.PGNotification;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.Constants;
import net.oneki.mtac.core.util.cache.Cache;
import net.oneki.mtac.core.util.cache.ResourceRegistry;
import net.oneki.mtac.resource.iam.tenant.Tenant;
import net.oneki.mtac.resource.schema.Schema;


@Component
@Slf4j
@RequiredArgsConstructor
public class PgNotificationConsumer implements Consumer<PGNotification> {

    private final Cache cache;

    @Override
    public void accept(PGNotification message) {
        try {
            log.debug("Notification received: channel={}, value={}", message.getName(), message.getParameter());
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
                            .of("tenant.root", "tenant.company", "tenant.site", "tenant.rg").stream()
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
                                        Schema.builder().id(id).urn(Schema.asUrn(label)).build());
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
                                        Tenant.builder().id(id).urn(String.format("urn:%s:%s:%s", parentTenantLabel, schema.getLabel(), label)).build());
                            }
                            break;

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
