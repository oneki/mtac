package net.oneki.mtac.resource.tenant;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.oneki.mtac.core.repository.ResourceRepository;
import net.oneki.mtac.core.util.exception.BusinessException;
import net.oneki.mtac.core.util.query.Query;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.UpsertRequest;
import net.oneki.mtac.resource.iam.tenant.Tenant;

@Service
public class TenantService<U extends UpsertRequest, E extends Tenant> extends ResourceService<U, E> {
    @Autowired protected ResourceRepository resourceRepository;

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
            .build()
        );
        var links = resources.stream()
            .filter(r -> r.isLink())
            .map(r -> r.getLinkId())
            .collect(Collectors.toList());

        if (resources.size() > links.size()) {
            throw new BusinessException("BSN_TENANT_NOT_EMPTY", "Can't delete tenant with id:" + tenant.getId() + " because it's not empty");
        }

        // unlink resources
        deleteByIdsUnsecure(links);

        resourceRepository.delete(tenant.getId());
    }
}
