package net.oneki.mtac.resource.iam;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.ResourceRepository;
import net.oneki.mtac.framework.util.sql.SqlUtils;
import net.oneki.mtac.model.resource.iam.Role;

@Repository
@RequiredArgsConstructor
public class RoleRepository {
    private final ResourceRepository resourceRepository;

    public List<Role> listTenantRoles(int tenantId) {
        var sql = SqlUtils.getSQL("role/role_get_direct_by_tenant.sql");
        return resourceRepository.list(tenantId, Role.class, sql);
    }
}
