package net.oneki.mtac.resource.tenant;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.oneki.mtac.core.repository.ResourceReactiveRepository;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.Tenant;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.resource.ResourceService;
import reactor.core.publisher.Mono;

@Service
public abstract class TenantService<U extends UpsertRequest, E extends Tenant> extends ResourceService<U, E> {
    @Autowired protected ResourceReactiveRepository resourceRepository;

    @Override
    public Mono<Void> delete(E tenant) {
        if (tenant == null) {
            return Mono.empty();
        }
        // Verify if the user has the permission to delete the resource
        return permissionService.hasPermission(tenant.getId(), "delete")
            .flatMap(hasPermission -> {
                if (!hasPermission) {
                    return Mono.error(new BusinessException("FORBIDDEN", "Forbidden access"));
                }
                return resourceRepository.listByTenantUnsecure(tenant.getId(), Query.builder()
                    .forceTenant(true) // we don't want to retrieve resource with pub=true
                    .noPublic(true)
                    .build()
                ).collectList();
            })
            .flatMap(resources -> {
                var links = resources.stream()
                    .filter(r -> r.isLink())
                    .map(r -> r.getLinkId())
                    .collect(Collectors.toList());

                if (resources.size() > links.size()) {
                    return Mono.error(new BusinessException("BSN_TENANT_NOT_EMPTY", "Can't delete tenant with id:" + tenant.getId() + " because it's not empty"));
                }

                // unlink resources
                return deleteByIdsUnsecure(links);
            })
            .then(resourceRepository.delete(tenant.getId()));
    }
}
