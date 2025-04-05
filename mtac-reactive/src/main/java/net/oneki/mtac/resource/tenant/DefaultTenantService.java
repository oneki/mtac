package net.oneki.mtac.resource.tenant;

import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.ResourceRepository;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.model.resource.iam.tenant.Tenant;
import net.oneki.mtac.resource.ResourceService;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DefaultTenantService extends ResourceService<UpsertRequest, Tenant> {
    private final ResourceRepository resourceRepository;

    public Mono<Void> deleteTenant(Integer tenantId) {
       return resourceRepository.listByTenantUnsecure(tenantId, Query.builder()
                .forceTenant(true) // we don't want to retrieve resource with pub=true
                .build()
            )
            .collectList()
            .map(resources -> {
                var links = resources.stream()
                    .filter(r -> r.isLink())
                    .map(r -> r.getLinkId())
                    .collect(Collectors.toList());
                if (resources.size() >= links.size()) {
                    throw new BusinessException("BSN_TENANT_NOT_EMPTY", "Can't delete tenant with id:" + tenantId + " because it's not empty");
                }
                return links;
            })
            .flatMap(links -> {
                // unlink resources
                return deleteByIdsUnsecure(links);
            })
            .then(deleteById(tenantId));
    }

    @Override
    public Class<Tenant> getEntityClass() {
        return Tenant.class;
    }

    @Override
    public Class<UpsertRequest> getRequestClass() {
        return UpsertRequest.class;
    }
}
