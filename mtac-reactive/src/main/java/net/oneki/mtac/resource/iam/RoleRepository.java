package net.oneki.mtac.resource.iam;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.ResourceRepository;
import net.oneki.mtac.core.util.sql.ReactiveSqlUtils;
import net.oneki.mtac.model.resource.iam.Role;
import reactor.core.publisher.Flux;

@Repository
@RequiredArgsConstructor
public class RoleRepository {
    private final ResourceRepository resourceRepository;

    public Flux<Role> listTenantRoles(int tenantId) {
        var sql = ReactiveSqlUtils.getReactiveSQL("role/role_get_direct_by_tenant.sql");
        return resourceRepository.list(tenantId, Role.class, sql);
    }
}
