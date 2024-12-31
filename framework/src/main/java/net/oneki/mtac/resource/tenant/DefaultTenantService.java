package net.oneki.mtac.resource.tenant;

import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.ResourceRepository;
import net.oneki.mtac.core.util.exception.BusinessException;
import net.oneki.mtac.core.util.query.Query;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.UpsertRequest;
import net.oneki.mtac.resource.iam.tenant.Tenant;

@Service
@RequiredArgsConstructor
public class DefaultTenantService extends ResourceService<UpsertRequest, Tenant> {
    private final ResourceRepository resourceRepository;

    public void deleteTenant(Integer tenantId) {
        var resources = resourceRepository.listByTenantUnsecure(tenantId, Query.builder()
            .forceTenant(true) // we don't want to retrieve resource with pub=true
            .build()
        );
        var links = resources.stream()
            .filter(r -> r.isLink())
            .map(r -> r.getLinkId())
            .collect(Collectors.toList());
        if (resources.size() >= links.size()) {
            throw new BusinessException("BSN_TENANT_NOT_EMPTY", "Can't delete tenant with id:" + tenantId + " because it's not empty");
        }

        // unlink resources
        deleteByIdsUnsecure(links);

        // delete tenant
        deleteById(tenantId, Tenant.class);
    }
}
