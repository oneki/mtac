package net.oneki.mtac.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.service.RelationService;
import net.oneki.mtac.core.service.security.PermissionService;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.introspect.ResourceField;
import net.oneki.mtac.framework.query.FilterCriteria;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.framework.service.MapperService;
import net.oneki.mtac.framework.util.resource.R;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.framework.Urn;
import net.oneki.mtac.model.core.resource.SearchDto;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.exception.ForbiddenException;
import net.oneki.mtac.model.core.util.exception.NotFoundException;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.framework.Page;
import net.oneki.mtac.model.resource.LinkType;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.Tenant;
import net.oneki.mtac.model.resource.UpsertRequest;

@RequiredArgsConstructor
public abstract class ResourceService<U extends UpsertRequest, E extends Resource> {
    public enum Action {
        Create, Update, Delete
    }

    @Autowired
    protected ResourceRepository resourceRepository;
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
    public E toCreateEntity(U request) {
        var entityClass = getEntityClass();
        try {
            E entity = getMapperService().toEntity(request, entityClass.getDeclaredConstructor().newInstance());
            if (!permissionService.hasCreatePermission(entity.getTenantLabel(),
                    entity.getSchemaLabel())) {
                throw new ForbiddenException(
                        "The creation of the entity " + entityClass.getSimpleName() + " with label " + entity.getLabel()
                                + " in tenant " + entity.getTenantLabel() + " is forbidden for user "
                                + securityContext.getUsername());
            }

            // check uniqueness
            var uniqueInScope = ResourceRegistry.getUniqueInScope(entityClass);
            if (!resourceRepository.isUnique(entity.getLabel(), entity.getTenantLabel(), entityClass, uniqueInScope)) {
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
    public E toUpdateEntity(String uid, U request) {
        var entityClass = getEntityClass();
        try {
            E entity = getByUid(uid);
            if (entity == null) {
                throw new NotFoundException(
                        "Cannot find resource with uid " + uid);
            }

            entity = getMapperService().toEntity(request, entity);
            if (!permissionService.hasPermission(entity, "update")) {
                throw new ForbiddenException(
                        "The update of the entity " + entityClass.getSimpleName() + " with label " + entity.getLabel()
                                + " in tenant " + entity.getTenantLabel() + " is forbidden for user "
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

    public U toUpsertRequest(E entity) {
        if (entity == null) {
            return null;
        }
        var requestClass = getRequestClass();
        try {
            return getMapperService().toUpsertRequest(requestClass.getDeclaredConstructor().newInstance(), entity);
        } catch (Exception e) {
            throw new BusinessException("INTERNAL_ERROR",
                    "Error creating entity " + requestClass.getSimpleName() + ": " + e.getMessage(), e);
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

    @Transactional
    public E create(U request) {
        E entity = toCreateEntity(request);
        return create(entity);
    }

    @Transactional
    public E update(String uid, U request) {
        E entity = toUpdateEntity(uid, request);
        update(entity);
        return entity;
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
    @Transactional
    public E create(E resourceEntity) {
        if (resourceEntity.getTenantId() != null || resourceEntity.getTenantLabel() != null) {
            R.fillMeta(resourceEntity);
        } else {
            throw new BusinessException("INVALID_ENTITY", "The entity " + resourceEntity + " must have a tenant");
        }
        // Verify if the user has the permission to create the resource
        if (!permissionService.hasCreatePermission(resourceEntity.getTenantLabel(),
                resourceEntity.getSchemaLabel())) {
            throw new BusinessException("FORBIDDEN", "Forbidden access");
        }

        return resourceRepository.create(resourceEntity);
    }

    @Transactional
    public E createUnsecure(E resourceEntity) {
        if (resourceEntity.getTenantId() != null || resourceEntity.getTenantLabel() != null) {
            R.fillMeta(resourceEntity);
        } else {
            throw new BusinessException("INVALID_ENTITY", "The entity " + resourceEntity + " must have a tenant");
        }

        return resourceRepository.create(resourceEntity);
    }

    @Transactional
    public Integer createLink(Integer sourceId, Integer targetTenantId, LinkType linkType, boolean pub) {
        var sourceEntity = resourceRepository.getById(sourceId, Resource.class);
        if (sourceEntity == null) {
            throw new ForbiddenException(
                    "Failed to create link because source with id=" + sourceId + " was not found");
        }
        return createLink(sourceEntity, targetTenantId, linkType, pub);
    }

    @Transactional
    public Integer createLink(Resource sourceEntity, Integer targetTenantId, LinkType linkType, boolean pub) {
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
    @Transactional
    public void update(E resourceEntity) {
        if (resourceEntity.getUpdatedBy() == null) {
            resourceEntity.setUpdatedBy(securityContext.getUsername());
        }
        // Verify if the user has the permission to update the resource
        if (!permissionService.hasPermission(resourceEntity.getId(), "update")) {
            throw new BusinessException("FORBIDDEN", "Forbidden access");
        }
        resourceRepository.update(resourceEntity);
    }

    public void updateUnsecure(E resourceEntity) {
        if (resourceEntity.getUpdatedBy() == null) {
            resourceEntity.setUpdatedBy(securityContext != null ? securityContext.getUsername() : "");
        }
        resourceRepository.update(resourceEntity);
    }

    /**
     * Delete an entity by its internal id
     *
     *
     * @param id:          The id of the entity
     * @param entityClass: the class of the entity
     * @return void
     */
    @Transactional
    public void deleteById(Integer id) {
        E entity = resourceRepository.getById(id, getEntityClass());
        if (entity == null) {
            throw new NotFoundException("Resource with id=" + id + " not found");
        }
        delete(entity);
    }

    @Transactional
    public void deleteByIdUnsecure(Integer id) {
        resourceRepository.delete(id);
    }

    @Transactional
    public void deleteByIdsUnsecure(List<Integer> ids) {
        resourceRepository.delete(ids);
    }

    @Transactional
    public void deleteByUid(String uid) {
        deleteById(Resource.fromUid(uid));
    }

    @Transactional
    public void deleteByUidUnsecure(String uid) {
        deleteByIdUnsecure(Resource.fromUid(uid));
    }

    @Transactional
    public void deleteByUidsUnsecure(List<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return;
        }
        deleteByIdsUnsecure(uids.stream().map(Resource::fromUid).toList());
    }

    /**
     * Delte an entity by its public id
     *
     *
     * @param uuid:        the public ID of the entity
     * @param entityClass: the class of the entity
     * @return void
     */
    // public void deleteByLabelOrUrn(String labelOrUrn) {
    // E entity = resourceRepository.getByLabelOrUrn(labelOrUrn, getEntityClass());
    // if (entity == null) {
    // throw new NotFoundException("Resource " + labelOrUrn + " not found");
    // }
    // delete(entity);
    // }

    // public void deleteByUrn(String urn) {
    // E entity = resourceRepository.getByUrn(urn, getEntityClass());
    // if (entity == null) {
    // throw new NotFoundException("Resource " + urn + " not found");
    // }
    // delete(entity);
    // }

    // /**
    // * Delte an entity by its label, tenant, schema
    // *
    // *
    // * @param tenantLabel: the label of the tenant
    // * @param label: the label of the entity
    // * @param entityClass: the class of the entity
    // * @return void
    // */
    // public void deleteByLabel(String tenantLabel, String label) {
    // E entity = resourceRepository.getByLabel(label, tenantLabel,
    // getEntityClass());
    // if (entity == null) {
    // throw new NotFoundException("Resource " + label + " in tenant " + tenantLabel
    // + " not found");
    // }
    // delete(entity);
    // }

    /**
     * Delete an entity
     *
     * This method verifies first that the requester can delete the entity
     *
     * @param resourceEntity: the entity to delete
     * @return void
     */
    @Transactional
    public void delete(E resourceEntity) {
        if (resourceEntity == null) {
            return;
        }
        // Verify if the user has the permission to delete the resource
        if (!permissionService.hasPermission(resourceEntity.getId(), "delete")) {
            throw new BusinessException("FORBIDDEN", "Forbidden access");
        }
        resourceRepository.delete(resourceEntity.getId());
    }

    /**
     * Get an entity by its id
     *
     * @param id:                 the id of the entity
     * @param resultContentClass: the class of the entity
     * @return the entity
     */
    public E getById(Integer id) {
        return getById(id, null);
    }

    public E getByIdUnsecure(Integer id) {
        return getByIdUnsecure(id, null);
    }

    public E getById(Integer id, Set<String> relations) {
        E result = resourceRepository.getById(id, getEntityClass());
        if (result == null) {
            throw new BusinessException("NOT_FOUND", "Resource not found");
        }

        if (relations != null) {
            relationService.populateSingleResourceRelations(result, relations);
        }

        return result;
    }

    public E getByIdUnsecure(Integer id, Set<String> relations) {
        E result = resourceRepository.getByIdUnsecure(id, getEntityClass());
        if (result == null) {
            throw new BusinessException("NOT_FOUND", "Resource not found");
        }

        if (relations != null) {
            relationService.populateSingleResourceRelationsUnsecure(result, relations);
        }

        return result;
    }

    /**
     * Get an entity by its tenant, schema, label
     *
     * @param tenantLabel:        the label of the tenant
     * @param label:              the label of the entity
     * @param resultContentClass: the class of the entity
     * @return the entity
     */
    public E getByLabel(String label, String tenantLabel) {
        return getByLabel(label, tenantLabel, null);
    }

    public E getByUniqueLabel(String label) {
        E result = resourceRepository.getByUniqueLabel(label, getEntityClass());
        if (result == null) {
            throw new BusinessException("NOT_FOUND", "Resource not found");
        }
        return result;
    }

    public E getByUniqueLabelOrReturnNull(String label) {
        return resourceRepository.getByUniqueLabel(label, getEntityClass());
    }

    public <Result extends Resource> Result getByUniqueLabelUnsecureOrReturnNull(String label,
            Class<Result> resultContentClass) {
        return resourceRepository.getByUniqueLabelUnsecure(label, resultContentClass);
    }

    public E getByUniqueLabelUnsecureOrReturnNull(String label) {
        return resourceRepository.getByUniqueLabelUnsecure(label, getEntityClass());
    }

    public E getByLabel(String label, String tenantLabel, Set<String> relations) {
        E result = resourceRepository.getByLabel(label, tenantLabel, getEntityClass());
        if (result == null) {
            throw new BusinessException("NOT_FOUND", "Resource not found");
        }

        if (relations != null) {
            relationService.populateSingleResourceRelations(result, relations);
        }

        return result;
    }

    public E getByUid(String uid) {
        return getById(Resource.fromUid(uid), null);
    }

    public E getByUid(String uid, Set<String> relations) {
        return getById(Resource.fromUid(uid), relations);
    }

    public E getByUidUnsecure(String uid) {
        return getByIdUnsecure(Resource.fromUid(uid), null);
    }

    public E getByUidUnsecure(String uid, Set<String> relations) {
        return getByIdUnsecure(Resource.fromUid(uid), relations);
    }

    public U getRequestById(Integer id) {
        return getRequestById(id, null);
    }

    public U getRequestById(Integer id, Set<String> relations) {
        E entity = getById(id, relations);
        return toUpsertRequest(entity);
    }

    public U getRequestByUid(String uid) {
        return getRequestById(Resource.fromUid(uid), null);
    }

    public U getRequestByUid(String uid, Set<String> relations) {
        return getRequestById(Resource.fromUid(uid), relations);
    }

    // public E getByLabelOrUrn(String label) {
    // return getByLabelOrUrn(label, null);
    // }
    // public E getByLabelOrUrn(String label, Set<String> relations) {
    // E result = resourceRepository.getByLabelOrUrn(label, getEntityClass());
    // if (result == null) {
    // throw new BusinessException("NOT_FOUND", "Resource not found");
    // }

    // if (relations != null) {
    // relationService.populateSingleResourceRelations(result, relations);
    // }

    // return result;
    // }

    // public E getByLabelOrUrnUnsecure(String label) {
    // E result = resourceRepository.getByLabelOrUrnUnsecure(label,
    // getEntityClass());
    // if (result == null) {
    // throw new BusinessException("NOT_FOUND", "Resource not found");
    // }
    // return result;
    // }

    // public E getByUrn(String urn) {
    // return getByUrn(urn, null);
    // }
    // public E getByUrn(Urn urn) {
    // return getByUrn(urn.toString(), null);
    // }
    // public E getByUrn(String urn, Set<String> relations) {
    // E result = resourceRepository.getByUrn(urn, getEntityClass());
    // if (result == null) {
    // throw new BusinessException("NOT_FOUND", "Resource not found");
    // }

    // if (relations != null) {
    // relationService.populateSingleResourceRelations(result, relations);
    // }

    // return result;
    // }
    // public E getByUrn(Urn urn, Set<String> relations) {
    // return getByUrn(urn.toString(), relations);
    // }

    // public E getByUrnUnsecure(String urn) {
    // E result = resourceRepository.getByUrnUnsecure(urn, getEntityClass());
    // if (result == null) {
    // throw new BusinessException("NOT_FOUND", "Resource not found");
    // }
    // return result;
    // }
    // public E getByUrnUnsecure(Urn urn) {
    // return getByUrnUnsecure(urn.toString());
    // }

    /**
     * Get a list of entities
     *
     * @param query:              query used to filter / sort / limit the result
     * @param resultContentClass: the class of the entity
     * @return an entity page
     */

    public Page<E> list(Query query) {
        var resources = resourceRepository.listByTenantAndType(query.getTenant(), getEntityClass(), query);
        return asPage(resources, query, false);
    }

    public <C extends Resource> Page<C> list(Query query, Class<C> resultContentClass) {
        var resources = resourceRepository.listByTenantAndType(query.getTenant(), resultContentClass, query);
        return asPage(resources, query, false);
    }

    public List<E> listAll(Query query) {
        if (query.getLimit() == null) {
            query.setLimit(Constants.LIST_ALL_LIMIT);
        }
        var resources = list(query);
        return resources.getData() != null ? resources.getData() : List.of();
    }

    public Page<E> listUnsecure(Query query) {
        var resources = resourceRepository.listByTenantAndTypeUnsecure(query.getTenant(), getEntityClass(), query);
        return asPage(resources, query, true);
    } 
    
    public <C extends Resource> Page<C> listUnsecure(Query query, Class<C> resultContentClass) {
        var resources = resourceRepository.listByTenantAndTypeUnsecure(query.getTenant(), resultContentClass, query);
        return asPage(resources, query, true);
    }     

    public List<E> listAllUnsecure(Query query) {
        if (query.getLimit() == null) {
            query.setLimit(Constants.LIST_ALL_LIMIT);
        }

        var resources = listUnsecure(query);
        return resources.getData() != null ? resources.getData() : List.of();
    }

    protected <C extends Resource> Page<C> asPage(List<C> resources, Query query, boolean unsecure) {
        var relationNames = query.getRelations();
        if (relationNames != null) {
            if (unsecure) {
                resources = relationService.populateRelationsUnsecure(resources, relationNames);
            } else {
                resources =  relationService.populateRelations(resources, relationNames);
            }
        }
        resources = resources.stream()
                .map(resource -> mapperService.filterFields(query.getFields(), resource))
                .collect(Collectors.toList());
        return Page.<C>builder()
                .data(resources)
                .limit(query.getLimit())
                .offset(query.getOffset())
                .hasNext(query.getLimit() != null && resources.size() >= query.getLimit())
                .build();
    }

    protected List<E> asAllList(List<E> resources, Query query) {
        var relationNames = query.getRelations();
        if (relationNames != null) {
            relationService.populateRelations(resources, relationNames);
        }
        return resources.stream()
                .map(resource -> mapperService.filterFields(query.getFields(), resource))
                .collect(Collectors.toList());
    }

    public Page<SearchDto> search(Query query) {
        // Add a filter to remove resources with type >= 1000
        query.getFilter().addCriteria(FilterCriteria.builder()
                .field("resource_type")
                .operator(FilterCriteria.Operator.LESSER)
                .value("1000")
                .build());
        query.getFilter().addCriteria(FilterCriteria.builder()
                .field("link_type")
                .operator(FilterCriteria.Operator.EQUALS)
                .value("0")
                .build());
        var resources = resourceRepository.search(query);

        // map resources to List of searchDto
        var dtos = resources.stream()
                .map(resource -> SearchDto.builder()
                        .uid(resource.getUid())
                        .label(resource.getLabel())
                        .resourceType(resource.getResourceType())
                        .tenantUid(resource.getTenantId() != null ? Resource.toUid(resource.getTenantId()) : null)
                        .tenantLabel(resource.getTenantLabel())
                        .schema(resource.getSchemaLabel())
                        .build())
                .toList();

        return Page.<SearchDto>builder()
                .data(dtos)
                .limit(query.getLimit())
                .offset(query.getOffset())
                .hasNext(query.getLimit() != null && resources.size() >= query.getLimit())
                .build();
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
                if (relations != null) {
                    for (var relation : relations) {
                        var relationEntity = getRelationEntity(upsertRequest, relationField, relation);
                        relationEntities.add(relationEntity);
                    }
                }
                relationField.getField().set(upsertRequest, relationEntities);
            } else {
                var relation = (Resource) relationField.getField().get(upsertRequest);
                if (relation != null) {
                    var relationEntity = getRelationEntity(upsertRequest, relationField, relation);
                    relationField.getField().set(upsertRequest, relationEntity);
                } else {
                    relationField.getField().set(upsertRequest, null);
                }
            }
        }

        return upsertRequest;
    }

    public MapperService getMapperService() {
        return mapperService;
    }

    private Resource getRelationEntity(UpsertRequest upsertRequest, ResourceField relationField,
            Resource relation) throws IllegalArgumentException, IllegalAccessException {
        if (relation != null) {
            Resource relationEntity = null;
            if (relation.getId() != null) {
                relationEntity = resourceRepository.getById(relation.getId(), relationField.getRelationClass());
            } else if (relation.getLabel() != null) {
                relationEntity = resourceRepository.getByLabel(relation.getLabel(), upsertRequest.getTenant(),
                        relationField.getRelationClass());
            }
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
