package net.oneki.mtac.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.model.api.ResourceRequest;
import net.oneki.mtac.model.api.UpsertRequest;
import net.oneki.mtac.model.entity.Resource;
import net.oneki.mtac.util.cache.Cache;
import net.oneki.mtac.util.cache.ResourceRegistry;
import net.oneki.mtac.util.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class MapperService {
    public <T extends Resource, R extends ResourceRequest> T toEntity(R request, T entity)  {

        var requestFields = ResourceRegistry.getResourceDesc(request.getClass());
        var entityFields = ResourceRegistry.getResourceDesc(entity.getClass());

        for (var requestField : requestFields.getFields()) {
            try {
                requestField.getField().setAccessible(true);
                var entityField = entityFields.getField(requestField.getLabel());
                
                if (entityField == null)
                    continue;
                
                entityField.getField().setAccessible(true);
                if ("tenant".equals(requestField.getLabel())) {
                    var tenantLabel = (String) requestField.getField().get(request);
                    if (tenantLabel == null) {
                        entityField.getField().set(entity, null);
                    } else {
                        var tenant = Cache.getInstance().getTenantByLabel(tenantLabel);
                        entityField.getField().set(entity, tenant.toRef());
                    }
                    continue;
                }

                if (!requestField.getType().equals(entityField.getType()))
                    continue;
                if (!requestField.isMultiple() && entityField.isMultiple())
                    continue;
                
                var value = requestField.getField().get(request);
                if (value == null && entityField.isSecret()) {
                    continue;
                }

                entityField.getField().set(entity, requestField.getField().get(request));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("BAD_REQUEST", "Invalid request", e);
            } catch (IllegalAccessException e) {
                throw new BusinessException("BAD_REQUEST", "Invalid request", e);
            }
        }

        if (request instanceof UpsertRequest) {
            var upsertRequest = (UpsertRequest) request;
            entity.setUrn("urn:" + upsertRequest.getTenant() + ":" + entity.getSchema() + ":" + entity.labelize());
        }

        return entity;
    }

}
