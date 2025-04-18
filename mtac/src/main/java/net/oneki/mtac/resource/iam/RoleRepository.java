package net.oneki.mtac.resource.iam;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.repository.AbstractRepository;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.framework.util.sql.SqlUtils;
import net.oneki.mtac.model.core.security.TenantRole;
import net.oneki.mtac.model.core.security.TenantWithHierarchy;
import net.oneki.mtac.model.core.util.json.JsonUtil;
import net.oneki.mtac.model.resource.iam.Role;

@Repository
@RequiredArgsConstructor
public class RoleRepository extends AbstractRepository {
    private final ResourceRepository resourceRepository;

    // List all roles for a tenant
    public List<Role> listCompiledRolesByTenant(int tenantId) {
        var sql = SqlUtils.getSQL("role/role_compiled_by_tenant.sql");
        return resourceRepository.list(tenantId, Role.class, sql);
    }

    public List<TenantRole> listAssignedTenantRolesByIdentity(Collection<Integer> identityIds) {
        var sql = SqlUtils.getSQL("role/role_assigned_by_user.sql");
        var args = Map.of(
            "identityIds", identityIds
        );

        var tenantRoles = jdbcTemplate.query(sql, args, (rs, rowNum) -> {
            var tenantRole = TenantRole.builder()
                .tenant(TenantWithHierarchy.builder()
                    .id(rs.getInt("id"))
                    .urn(rs.getString("urn"))
                    .name(rs.getString("name"))
                    .build())
                .roles(JsonUtil.json2Set(rs.getString("roles"), RoleAssigned.class).stream()
                    .filter(r -> r.isAssigned() == true)
                    .map(r -> r.getRole())
                    .collect(Collectors.toSet()))
                .build();
            return tenantRole;
        });
        return tenantRoles;

    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoleAssigned {
        private Role role;
        private boolean assigned;
    }

}
