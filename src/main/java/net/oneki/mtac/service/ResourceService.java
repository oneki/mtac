package net.oneki.mtac.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.model.api.UpsertRequest;
import net.oneki.mtac.model.entity.Ref;
import net.oneki.mtac.model.entity.ResourceEntity;
import net.oneki.mtac.model.framework.Page;
import net.oneki.mtac.repository.ResourceRepository;
import net.oneki.mtac.service.security.PermissionService;
import net.oneki.mtac.util.cache.Cache;
import net.oneki.mtac.util.cache.ResourceRegistry;
import net.oneki.mtac.util.exception.BusinessException;
import net.oneki.mtac.util.exception.ForbiddenException;
import net.oneki.mtac.util.exception.NotFoundException;
import net.oneki.mtac.util.introspect.RelationField;
import net.oneki.mtac.util.query.Query;
import net.oneki.mtac.util.security.SecurityContext;

@Service
@RequiredArgsConstructor
public class ResourceService {
    public enum Action {
        Create, Update, Delete
    }

    private final ResourceRepository resourceRepository;
    private final PermissionService permissionService;
    private final MapperService mapperService;
    private final SecurityContext securityContext;
    private final RelationService relationService;

    public <T extends ResourceEntity, R extends UpsertRequest> T toCreateEntity(R request, Class<T> entityClass) {
        try {
            var entity = mapperService.toEntity(request, entityClass.getDeclaredConstructor().newInstance());
            if (!permissionService.hasCreatePermission(entity.getTenant(),
                    entity.getSchema())) {
                throw new ForbiddenException(
                        "The creation of the entity " + entityClass.getSimpleName() + " with label " + entity.getLabel()
                                + " in tenant " + entity.getTenant() + " is forbidden for user "
                                + securityContext.getUsername());
            }
            entity.labelize();

            // check uniqueness
            var uniqueInScope = ResourceRegistry.getUniqueInScope(entityClass);
            if (!resourceRepository.isUnique(entity.getLabel(), entity.getTenant(), entityClass, uniqueInScope)) {
                throw new BusinessException("RESOURCE_ALREADY_EXISTS",
                        "Resource with label " + entity.getLabel() + " already exists");
            }
            return entity;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("INTERNAL_ERROR",
                    "Error creating entity " + entityClass.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public <T extends ResourceEntity, R extends UpsertRequest> T toUpdateEntity(R request, Class<T> entityClass) {
        try {
            var entity = resourceRepository.getByLabel(request.getLabel(), request.getTenant(), entityClass);
            if (entity == null) {
                throw new NotFoundException(
                        "Cannot find resource with tenant" + request.getTenant() + ", schema " + entityClass.getSimpleName()
                                + " and label " + request.getLabel());
            }

            entity = mapperService.toEntity(request, entity);
            if (!permissionService.hasPermission(entity, "update")) {
                throw new ForbiddenException(
                        "The update of the entity " + entityClass.getSimpleName() + " with label " + entity.getLabel()
                                + " in tenant " + entity.getTenant() + " is forbidden for user "
                                + securityContext.getUsername());
            }
            return entity;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("INTERNAL_ERROR",
                    "Error updating entity " + entityClass.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    // public <T extends ResourceEntity> T toDeleteEntity(String tenantLabel, String label, Class<T> entityClass) {
    //     try {
    //         var entity = resourceRepository.getByLabel(label, tenantLabel, entityClass);
    //         if (entity == null) {
    //             return null;
    //         }
    //         if (!permissionService.hasPermission(entity, "delete")) {
    //             throw new ForbiddenException(
    //                     "The deletion of the entity " + entityClass.getSimpleName() + " with label " + entity.getLabel()
    //                             + " in tenant " + entity.getTenant().getLabel() + " is forbidden for user "
    //                             + securityContext.getUsername());
    //         }
    //         return entity;
    //     } catch (BusinessException e) {
    //         throw e;
    //     } catch (Exception e) {
    //         throw new BusinessException("INTERNAL_ERROR",
    //                 "Error updating entity " + entityClass.getSimpleName() + ": " + e.getMessage(), e);
    //     }
    // }

    public <T extends ResourceEntity> T create(String tenantLabel, T resourceEntity) {
        var tenant = Cache.getInstance().getTenantByLabel(tenantLabel);
        resourceEntity.setTenant(tenantLabel);
        return create(resourceEntity);
    }

    public <T extends ResourceEntity> T create(T resourceEntity) {
        // Verify if the user has the permission to create the resource
        if (!permissionService.hasCreatePermission(resourceEntity.getTenant(),
                resourceEntity.getSchema())) {
            throw new BusinessException("FORBIDDEN", "Forbidden access");
        }
        return resourceRepository.create(resourceEntity);
    }

    public <T extends ResourceEntity> void update(T resourceEntity) {

        // Verify if the user has the permission to update the resource
        if (!permissionService.hasPermission(resourceEntity.getId(), "update")) {
            throw new BusinessException("FORBIDDEN", "Forbidden access");
        }
        resourceRepository.update(resourceEntity);
    }

    public <T extends ResourceEntity> void deleteById(Integer id, Class<T> entityClass) {
        var entity = resourceRepository.getById(id, entityClass);
        delete(entity);
    }

    public  <T extends ResourceEntity> void deleteByPublicId(UUID publicId, Class<T> entityClass) {
        var entity = resourceRepository.getByPublicId(publicId, entityClass);
        delete(entity);
    }

    public <T extends ResourceEntity> void deleteByLabel(String tenantLabel, String label, Class<T> entityClass) {
        var entity = resourceRepository.getByLabel(label, tenantLabel, entityClass);
        delete(entity);
    }

    public <T extends ResourceEntity> void delete(T resourceEntity) {
        if (resourceEntity == null) {
            return;
        }
        // Verify if the user has the permission to delete the resource
        if (!permissionService.hasPermission(resourceEntity.getId(), "delete")) {
            throw new BusinessException("FORBIDDEN", "Forbidden access");
        }
        resourceRepository.delete(resourceEntity.getId());
    }

    public <T extends ResourceEntity> T getById(Integer id, Class<T> resultContentClass) {
        var result = resourceRepository.getById(id, resultContentClass);
        if (result == null) {
            throw new BusinessException("NOT_FOUND", "Resource not found");
        }

        return result;
    }

    public <T extends ResourceEntity> T getByLabel(String tenantLabel, String label, Class<T> resultContentClass) {
        var result = resourceRepository.getByLabel(tenantLabel, label, resultContentClass);
        if (result == null) {
            throw new BusinessException("NOT_FOUND", "Resource not found");
        }

        return result;
    }

    public <T extends ResourceEntity> Page<T> list(Query query, Class<T> resultContentClass) {
        var resources = resourceRepository.listByTenantAndType(query.getTenant(), resultContentClass, query);
        var relationNames = query.getRelations();
        if (relationNames != null) {
            relationService.populateRelations(resources, relationNames);
        }
        return Page.<T>builder()
                .data(resources)
                .limit(query.getLimit())
                .offset(query.getOffset())
                .hasNext(query.getLimit() != null && resources.size() >= query.getLimit())
                .build();
    }

    @SuppressWarnings("unchecked")
    public <T extends UpsertRequest> T loadRequestRelations(T upsertRequest)
            throws IllegalArgumentException, IllegalAccessException {
        var relationFields = ResourceRegistry.getRelations(upsertRequest.getClass());
        for (var relationField : relationFields) {
            relationField.getField().setAccessible(true);
            if (relationField.isMultiple()) {
                var relations = (List<ResourceEntity>) relationField.getField().get(upsertRequest);
                var relationEntities = new ArrayList<ResourceEntity>();
                for (var relation : relations) {
                    var relationEntity = getRelationEntity(upsertRequest, relationField, relation);
                    relationEntities.add(relationEntity);
                }
                relationField.getField().set(upsertRequest, relationEntities);
            } else {
                var relation = (ResourceEntity) relationField.getField().get(upsertRequest);
                var relationEntity = getRelationEntity(upsertRequest, relationField, relation);
                relationField.getField().set(upsertRequest, relationEntity);
            }
        }

        return upsertRequest;
    }

    private ResourceEntity getRelationEntity(UpsertRequest upsertRequest, RelationField relationField,
            ResourceEntity relation) throws IllegalArgumentException, IllegalAccessException {
        if (relation != null) {
            ResourceEntity relationEntity = null;
            
            relationEntity = resourceRepository.getByLabel(relation.getLabel(), upsertRequest.getTenant(),
                    relationField.getRelationClass());
            if (relationEntity == null) {
                throw new BusinessException("RESOURCE_NOT_FOUND",
                        "Cannot find resource with tenant" + upsertRequest.getTenant() + ", schema "
                                + relationField.getType() + " and label " + relation.getLabel());
            }
            return relationEntity;
        }
        return null;
    }




}
