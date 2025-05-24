package net.oneki.mtac.framework.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.ResourceRequest;
import net.oneki.mtac.model.resource.UpsertRequest;

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
                    var tenantUid = (String) requestField.getField().get(request);
                    if (tenantUid == null) {
                        entity.setTenantId(null);
                        entity.setTenantLabel(null);
                    } else {
                        var tenant = ResourceRegistry.getTenantById(Resource.fromUid(tenantUid));
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

                entityField.getField().set(entity, value);
            }

            // if (request instanceof UpsertRequest) {
            //     var upsertRequest = (UpsertRequest) request;
            //     // var label = entity.labelize();
            //     // entity.setLabel(label);
            //     entity.setUrn(UrnUtils.of(upsertRequest.getTenant(), entity.getSchemaLabel(), entity.getLabel()).toString());
            // }
        } catch (IllegalArgumentException e) {
            throw new BusinessException("BAD_REQUEST", "Invalid request", e);
        } catch (IllegalAccessException e) {
            throw new BusinessException("BAD_REQUEST", "Invalid request", e);
        }

        return entity;
    }

    public<E extends Resource, U extends UpsertRequest> U toUpsertRequest(U request, E entity) {
        try {
            var requestFields = ResourceRegistry.getResourceDesc(request.getClass());
            var entityFields = ResourceRegistry.getResourceDesc(entity.getClass());
            var schemaLabel = ResourceRegistry.getSchemaByClass(entity.getClass());
            entity.setSchemaLabel(schemaLabel);
            entity.setSchemaId(ResourceRegistry.getSchemaId(schemaLabel));
           
            for (var entityField : entityFields.getFields()) {

                entityField.getField().setAccessible(true);
                var requestField = requestFields.getField(entityField.getLabel());

                if ("tenantId".equals(entityField.getLabel())) {
                    var tenantId = (Integer) entityField.getField().get(entity);
                    request.setTenant(Resource.toUid(tenantId));
                    continue;
                }

                if (requestField == null)
                    continue;

                requestField.getField().setAccessible(true);
                if (!entityField.getType().equals(requestField.getType()))
                    continue;
                if (!entityField.isMultiple() && requestField.isMultiple())
                    continue;

                var value = entityField.getField().get(entity);
                if (value == null && requestField.isSecret()) {
                    continue;
                }

                requestField.getField().set(request, value);
            }

        } catch (IllegalArgumentException e) {
            throw new BusinessException("BAD_ENTITY", "Invalid entity", e);
        } catch (IllegalAccessException e) {
            throw new BusinessException("BAD_ENTITY", "Invalid entity", e);
        }

        return request;
    }

}
