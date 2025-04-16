package net.oneki.mtac.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.ResourceReactiveRepository;
import net.oneki.mtac.core.service.RelationService;
import net.oneki.mtac.core.service.security.PermissionService;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.introspect.ResourceField;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.framework.service.MapperService;
import net.oneki.mtac.framework.util.resource.R;
import net.oneki.mtac.model.core.framework.Urn;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.exception.ForbiddenException;
import net.oneki.mtac.model.core.util.exception.NotFoundException;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.framework.Page;
import net.oneki.mtac.model.resource.LinkType;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.UpsertRequest;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public abstract class ResourceService<U extends UpsertRequest, E extends Resource> {
    public enum Action {
        Create, Update, Delete
    }

    @Autowired
    protected ResourceReactiveRepository resourceRepository;
    @Autowired
    protected PermissionService permissionService;
    @Autowired
    protected DefaultMapperService mapperService;
    @Autowired
    protected SecurityContext securityContext;
    @Autowired
    protected RelationService relationService;

    public abstract Class<E> getEntityClass();

    public abstract Class<U> getRequestClass();

    /**
     * Base method to convert a create request to an actual resource
     * This method
     * - tries to map the request to an entity (actually only fields shared between
     * the request and the entity)
     * - checks if the requester can create this kind of entity in the tenant
     * - labelizes the entity
     * - checks if the entity label is unique (in the scope defined by the schema)
     *
     * Any custom mapper should first call this base method and then map manually
     * additional fields that are not
     * common to both the request and the entity
     *
     * @param request:     The create request object
     * @param entityClass: The target entity class
     * @return The entity
     */
    public Mono<E> toCreateEntity(U request) {
        var entityClass = getEntityClass();
        try {
            final E entity = getMapperService().toEntity(request, entityClass.getDeclaredConstructor().newInstance());
            return permissionService.hasCreatePermission(entity.getTenantLabel(), entity.getSchemaLabel())
                    .map(hasPermission -> {
                        if (!hasPermission) {
                            throw new ForbiddenException(
                                    "The creation of the entity " + entityClass.getSimpleName() + " with label "
                                            + entity.getLabel()
                                            + " in tenant " + entity.getTenantLabel() + " is forbidden for user "
                                            + securityContext.getUsername());
                        }
                        entity.labelize();

                        // check uniqueness
                        var uniqueInScope = ResourceRegistry.getUniqueInScope(entityClass);
                        if (!resourceRepository.isUnique(entity.getLabel(), entity.getTenantLabel(), entityClass,
                                uniqueInScope)) {
                            throw new BusinessException("RESOURCE_ALREADY_EXISTS",
                                    "Resource with label " + entity.getLabel() + " already exists");
                        }
                        return entity;
                    });

        } catch (Exception e) {
            throw new BusinessException("INTERNAL_ERROR",
                    "Error creating entity " + entityClass.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Base method to convert an update request to an actual entity
     * This method
     * - get the current entity from the DB (and checks if the requester can access
     * it)
     * - checks if the requester can update this entity
     * - tries to map the request to the entity (actually only fields shared between
     * the request and the entity)
     * if a field is not defined in the request, the field from the entity is kept
     *
     * Any custom mapper should first call this base method and then map manually
     * additional fields that are not
     * common to both the request and the entity
     *
     * @param request:     The update request object
     * @param entityClass: The target entity class
     * @return The entity
     */
    public Mono<E> toUpdateEntity(String urn, U request) {
        var entityClass = getEntityClass();
        try {
            var urnRecord = Urn.of(urn);
            return resourceRepository.getByLabel(urnRecord.label(), urnRecord.tenant(), entityClass)
                    .switchIfEmpty(Mono.error(new NotFoundException("Cannot find resource with tenant"
                            + urnRecord.tenant() + ", schema " + entityClass.getSimpleName()
                            + " and label " + urnRecord.label())))
                    .flatMap(entity -> {
                        final var result = getMapperService().toEntity(request, entity);
                        return permissionService.hasPermission(Mono.just(result), "update")
                                .map(hasPermission -> {
                                    if (!hasPermission) {
                                        throw new ForbiddenException(
                                                "The update of the entity " + entityClass.getSimpleName()
                                                        + " with label " + urnRecord.label()
                                                        + " in tenant " + urnRecord.tenant() + " is forbidden for user "
                                                        + securityContext.getUsername());
                                    }
                                    return result;
                                });
                    });

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("INTERNAL_ERROR",
                    "Error updating entity " + entityClass.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    // public <T extends ResourceEntity> T toDeleteEntity(String tenantLabel, String
    // label, Class<T> entityClass) {
    // try {
    // var entity = resourceRepository.getByLabel(label, tenantLabel, entityClass);
    // if (entity == null) {
    // return null;
    // }
    // if (!permissionService.hasPermission(entity, "delete")) {
    // throw new ForbiddenException(
    // "The deletion of the entity " + entityClass.getSimpleName() + " with label "
    // + entity.getLabel()
    // + " in tenant " + entity.getTenant().getLabel() + " is forbidden for user "
    // + securityContext.getUsername());
    // }
    // return entity;
    // } catch (BusinessException e) {
    // throw e;
    // } catch (Exception e) {
    // throw new BusinessException("INTERNAL_ERROR",
    // "Error updating entity " + entityClass.getSimpleName() + ": " +
    // e.getMessage(), e);
    // }
    // }

    /**
     * Persists a new entity in the DB
     *
     * @param tenantLabel:    the label of the tenant containing the entity
     * @param resourceEntity: The entity to persist
     * @return The entity containing the auto generated id
     */
    // public <T extends Resource> T create(String tenantLabel, T resourceEntity) {
    // var tenant = Cache.getInstance().getTenantByLabel(tenantLabel);
    // resourceEntity.setTenant(tenantLabel);
    // return create(resourceEntity);
    // }

    public Mono<E> create(U request) {
        return toCreateEntity(request)
                .flatMap(entity -> {
                    return create(entity);
                });
    }

    /**
     * Persists a new entity in the DB
     *
     * This method verifies that the requester has the permission to create this
     * kind
     * of entity in the selected tenant
     *
     * @param resourceEntity: The entity to persist
     * @return The entity containing the auto generated id
     */
    public Mono<E> create(E resourceEntity) {
        if (resourceEntity.getTenantId() != null) {
            R.inject(resourceEntity, resourceEntity.getTenantId());
        } else if (resourceEntity.getTenantLabel() != null) {
            R.inject(resourceEntity, resourceEntity.getTenantLabel());
        } else if (resourceEntity.getUrn() != null) {
            R.fillMeta(resourceEntity);
        } else {
            throw new BusinessException("INVALID_ENTITY", "The entity " + resourceEntity + " must have a tenant");
        }
        if (resourceEntity.getCreatedBy() == null) {
            resourceEntity.setCreatedBy(securityContext.getUsername());
        }
        // Verify if the user has the permission to create the resource
        return permissionService.hasCreatePermission(resourceEntity.getTenantLabel(),
                resourceEntity.getSchemaLabel())
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        throw new ForbiddenException(
                                "The creation of the entity " + resourceEntity.getClass().getSimpleName()
                                        + " with label " + resourceEntity.getLabel()
                                        + " in tenant " + resourceEntity.getTenantLabel() + " is forbidden for user "
                                        + securityContext.getUsername());
                    }
                    return resourceRepository.create(resourceEntity);
                });

    }

    public Mono<Integer> createLink(String sourceUrn, Integer targetTenantId, LinkType linkType, boolean pub) {
        return resourceRepository.getByUrn(sourceUrn, Resource.class)
                .switchIfEmpty(Mono.error(new NotFoundException("Cannot find resource with urn " + sourceUrn)))
                .flatMap(sourceEntity -> {
                    return createLink(sourceEntity, targetTenantId, linkType, pub);
                });
    }

    public Mono<Integer> createLink(Resource sourceEntity, Integer targetTenantId, LinkType linkType, boolean pub) {
        // Do we need to check permission ?
        return resourceRepository.createLink(sourceEntity, targetTenantId, linkType, pub,
                securityContext.getUsername());
    }

    /**
     * Persists the changes made on an entity in the DB
     *
     * This method verifies that the requester has the permission to update the
     * entity
     *
     * @param resourceEntity: The entity to persist
     * @return void
     */
    public Mono<E> update(E resourceEntity) {
        if (resourceEntity.getUpdatedBy() == null) {
            resourceEntity.setUpdatedBy(securityContext.getUsername());
        }
        return permissionService.hasPermission(resourceEntity.getId(), "update")
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        throw new ForbiddenException(
                                "The update of the entity " + resourceEntity.getClass().getSimpleName() + " with label "
                                        + resourceEntity.getLabel()
                                        + " in tenant " + resourceEntity.getTenantLabel() + " is forbidden for user "
                                        + securityContext.getUsername());
                    }
                    return resourceRepository.update(resourceEntity);
                });
    }

    /**
     * Delte an entity by its internal id
     *
     *
     * @param id:          The id of the entity
     * @param entityClass: the class of the entity
     * @return void
     */
    public Mono<Void> deleteById(Integer id) {
        return resourceRepository.getById(id, getEntityClass())
                .switchIfEmpty(Mono.error(new NotFoundException("Resource with id=" + id + " not found")))
                .flatMap(entity -> {
                    return delete(entity);
                });
    }

    public void deleteByIdUnsecure(Integer id) {
        resourceRepository.delete(id);
    }

    public Mono<Void> deleteByIdsUnsecure(List<Integer> ids) {
        return resourceRepository.delete(ids);
    }

    /**
     * Delte an entity by its public id
     *
     *
     * @param uuid:        the public ID of the entity
     * @param entityClass: the class of the entity
     * @return void
     */
    public Mono<Void> deleteByLabelOrUrn(String labelOrUrn) {
        return resourceRepository.getByLabelOrUrn(labelOrUrn, getEntityClass())
                .switchIfEmpty(
                        Mono.error(new NotFoundException("Resource with labelOrUrn=" + labelOrUrn + " not found")))
                .flatMap(entity -> {
                    return delete(entity);
                });
    }

    public Mono<Void> deleteByUrn(String urn) {
        return resourceRepository.getByUrn(urn, getEntityClass())
                .switchIfEmpty(Mono.error(new NotFoundException("Resource with urn=" + urn + " not found")))
                .flatMap(entity -> {
                    return delete(entity);
                });
    }

    /**
     * Delte an entity by its label, tenant, schema
     *
     *
     * @param tenantLabel: the label of the tenant
     * @param label:       the label of the entity
     * @param entityClass: the class of the entity
     * @return void
     */
    public Mono<Void> deleteByLabel(String tenantLabel, String label) {
        return resourceRepository.getByLabel(label, tenantLabel, getEntityClass())
                .switchIfEmpty(Mono
                        .error(new NotFoundException("Resource " + label + " in tenant " + tenantLabel + " not found")))
                .flatMap(entity -> {
                    return delete(entity);
                });
    }

    /**
     * Delete an entity
     *
     * This method verifies first that the requester can delete the entity
     *
     * @param resourceEntity: the entity to delete
     * @return void
     */
    public Mono<Void> delete(E resourceEntity) {
        if (resourceEntity == null) {
            return Mono.empty();
        }
        // Verify if the user has the permission to delete the resource
        return permissionService.hasPermission(resourceEntity.getId(), "delete")
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        throw new ForbiddenException(
                                "The deletion of the entity " + resourceEntity.getClass().getSimpleName()
                                        + " with label " + resourceEntity.getLabel()
                                        + " in tenant " + resourceEntity.getTenantLabel() + " is forbidden for user "
                                        + securityContext.getUsername());
                    }
                    return resourceRepository.delete(resourceEntity.getId());
                });
    }

    /**
     * Get an entity by its id
     *
     * @param id:                 the id of the entity
     * @param resultContentClass: the class of the entity
     * @return the entity
     */
    public Mono<E> getById(Integer id) {
        return getById(id, null);
    }

    public Mono<E> getById(Integer id, Set<String> relations) {
        return resourceRepository.getById(id, getEntityClass())
                .switchIfEmpty(Mono.error(new NotFoundException("Resource with id=" + id + " not found")))
                .flatMap(result -> {
                    if (relations != null) {
                        result = relationService.populateSingleResourceRelations(result, relations);
                    }
                    return Mono.just(result);
                });
    }

    /**
     * Get an entity by its tenant, schema, label
     *
     * @param tenantLabel:        the label of the tenant
     * @param label:              the label of the entity
     * @param resultContentClass: the class of the entity
     * @return the entity
     */
    public Mono<E> getByLabel(String label, String tenantLabel) {
        return getByLabel(label, tenantLabel, null);
    }

    public Mono<E> getByLabel(String label, String tenantLabel, Set<String> relations) {
        return resourceRepository.getByLabel(label, tenantLabel, getEntityClass())
                .switchIfEmpty(Mono
                        .error(new NotFoundException("Resource " + label + " in tenant " + tenantLabel + " not found")))
                .flatMap(result -> {
                    if (relations != null) {
                        result = relationService.populateSingleResourceRelations(result, relations);
                    }
                    return Mono.just(result);
                });
    }

    public Mono<E> getByLabelOrUrn(String label) {
        return getByLabelOrUrn(label, null);
    }

    public Mono<E> getByLabelOrUrn(String label, Set<String> relations) {
        return resourceRepository.getByLabelOrUrn(label, getEntityClass())
                .switchIfEmpty(Mono.error(new NotFoundException("Resource with labelOrUrn=" + label + " not found")))
                .flatMap(result -> {
                    if (relations != null) {
                        result = relationService.populateSingleResourceRelations(result, relations);
                    }
                    return Mono.just(result);
                });
    }

    /**
     * Get a list of entities
     *
     * @param query:              query used to filter / sort / limit the result
     * @param resultContentClass: the class of the entity
     * @return an entity page
     */

    public Mono<Page<E>> list(Query query) {
        return resourceRepository.listByTenantAndType(query.getTenant(), getEntityClass(), query)
                .collectList()
                .map(resources -> {
                    var relationNames = query.getRelations();
                    if (relationNames != null) {
                        relationService.populateRelations(resources, relationNames);
                    }
                    return Page.<E>builder()
                            .data(resources)
                            .limit(query.getLimit())
                            .offset(query.getOffset())
                            .hasNext(query.getLimit() != null && resources.size() >= query.getLimit())
                            .build();
                });

    }

    @SuppressWarnings("unchecked")
    public U loadRequestRelations(U upsertRequest)
            throws IllegalArgumentException, IllegalAccessException {
        var relationFields = ResourceRegistry.getRelations(upsertRequest.getClass());
        for (var relationField : relationFields) {
            relationField.getField().setAccessible(true);
            if (relationField.isMultiple()) {
                var relations = (List<Resource>) relationField.getField().get(upsertRequest);
                var relationEntities = new ArrayList<Resource>();
                for (var relation : relations) {
                    var relationEntity = getRelationEntity(upsertRequest, relationField, relation).block();
                    relationEntities.add(relationEntity);
                }
                relationField.getField().set(upsertRequest, relationEntities);
            } else {
                var relation = (Resource) relationField.getField().get(upsertRequest);
                var relationEntity = getRelationEntity(upsertRequest, relationField, relation);
                relationField.getField().set(upsertRequest, relationEntity);
            }
        }

        return upsertRequest;
    }

    public MapperService getMapperService() {
        return mapperService;
    }

    private Mono<? extends Resource> getRelationEntity(UpsertRequest upsertRequest, ResourceField relationField,
            Resource relation) throws IllegalArgumentException, IllegalAccessException {
        if (relation != null) {
            Mono<? extends Resource> relationEntity = null;
            if (relation.getUrn() != null) {
                relationEntity = resourceRepository.getByUrn(relation.getUrn(), relationField.getRelationClass());
            } else if (relation.getLabel() != null) {
                relationEntity = resourceRepository.getByLabel(relation.getLabel(), upsertRequest.getTenant(),
                        relationField.getRelationClass());
            }

            if (relationEntity == null) {
                throw new BusinessException("RESOURCE_NOT_FOUND",
                        "Cannot find resource with tenant" + upsertRequest.getTenant() + ", schema "
                                + relationField.getType() + " and label " + relation.getLabel());
            }

            return relationEntity.switchIfEmpty(Mono
                    .error(new NotFoundException("Cannot find resource with tenant" + upsertRequest.getTenant()
                            + ", schema " + relationField.getType() + " and label " + relation.getLabel())));
        }
        return Mono.empty();
    }

}
