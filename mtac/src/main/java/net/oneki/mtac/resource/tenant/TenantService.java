package net.oneki.mtac.resource.tenant;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.oneki.mtac.framework.cache.PgNotifierService;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.exception.NotFoundException;
import net.oneki.mtac.model.resource.Tenant;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.model.resource.iam.identity.group.Group;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.iam.identity.group.DefaultGroupService;

@Service
public abstract class TenantService<U extends UpsertRequest, E extends Tenant> extends ResourceService<U, E> {
    @Autowired
    protected ResourceRepository resourceRepository;

    @Autowired
    protected DefaultGroupService groupService;

    @Autowired
    protected PgNotifierService pgNotifierService;

    @Override
    public void delete(E tenant) {
        if (tenant == null) {
            return;
        }
        // Verify if the user has the permission to delete the resource
        if (!permissionService.hasPermission(tenant.getId(), "delete")) {
            throw new BusinessException("FORBIDDEN", "Forbidden access");
        }

        var resources = resourceRepository.listByTenantUnsecure(tenant.getId(), Query.builder()
                .forceTenant(true) // we don't want to retrieve resource with pub=true
                .noPublic(true)
                .build());
        var links = resources.stream()
                .filter(r -> r.isLink())
                .map(r -> r.getLinkId())
                .collect(Collectors.toList());

        if (resources.size() > links.size()) {
            throw new BusinessException("BSN_TENANT_NOT_EMPTY",
                    "Can't delete tenant with id:" + tenant.getId() + " because it's not empty");
        }

        // unlink resources
        deleteByIdsUnsecure(links);

        resourceRepository.delete(tenant.getId());
    }

    protected Group createGroup(String displayName, Integer tenantId, Integer groupRefTenantId) {
        return createGroup(displayName, tenantId, groupRefTenantId, false);
    }

    protected Group createGroup(String displayName, Integer tenantId, Integer groupRefTenantId, boolean unsecure) {
        var group = Group.builder()
                .label(displayName)
                .tenantId(tenantId)
                .build();
        return groupService.create(group, groupRefTenantId, unsecure);
    }

    protected void deleteGroup(Tenant tenant, String rgName, String groupName) {
        try {
            // TODO
            // groupService.deleteByLabel(rgName + "@" +
            // Resource.toSuffix(tenant.getLabel()), groupName);
        } catch (NotFoundException e) {
        }
    }

}
