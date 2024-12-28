package net.oneki.mtac.resource;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.util.cache.Cache;
import net.oneki.mtac.core.util.cache.ResourceRegistry;
import net.oneki.mtac.core.util.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class MapperService {
    public<E extends Resource> E toEntity(ResourceRequest request, E entity) {
        try {
            var requestFields = ResourceRegistry.getResourceDesc(request.getClass());
            var entityFields = ResourceRegistry.getResourceDesc(entity.getClass());
            var schemaLabel = ResourceRegistry.getSchemaByClass(entity.getClass());
            entity.setSchemaLabel(schemaLabel);
            entity.setSchemaId(ResourceRegistry.getSchemaId(schemaLabel));
           
            for (var requestField : requestFields.getFields()) {

                requestField.getField().setAccessible(true);
                var entityField = entityFields.getField(requestField.getLabel());

                if ("tenant".equals(requestField.getLabel())) {
                    var tenantLabel = (String) requestField.getField().get(request);
                    if (tenantLabel == null) {
                        entity.setTenantId(null);
                        entity.setTenantLabel(null);
                    } else {
                        var tenant = Cache.getInstance().getTenantByLabel(tenantLabel);
                        entity.setTenantId(tenant.getId());
                        entity.setTenantLabel(tenant.getLabel());

                    }
                    continue;
                }

                if (entityField == null)
                    continue;

                entityField.getField().setAccessible(true);
                if (!requestField.getType().equals(entityField.getType()))
                    continue;
                if (!requestField.isMultiple() && entityField.isMultiple())
                    continue;

                var value = requestField.getField().get(request);
                if (value == null && entityField.isSecret()) {
                    continue;
                }

                entityField.getField().set(entity, requestField.getField().get(request));
            }

            if (request instanceof UpsertRequest) {
                var upsertRequest = (UpsertRequest) request;
                var label = entity.labelize();
                entity.setLabel(label);
                entity.setUrn(
                        "urn:" + upsertRequest.getTenant() + ":" + entity.getSchemaLabel() + ":" + label);
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException("BAD_REQUEST", "Invalid request", e);
        } catch (IllegalAccessException e) {
            throw new BusinessException("BAD_REQUEST", "Invalid request", e);
        }

        return entity;
    }

}
