package net.oneki.mtac.repository.iam;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.model.entity.iam.RoleEntity;
import net.oneki.mtac.repository.ResourceRepository;
import net.oneki.mtac.util.sql.SqlUtils;

@Repository
@RequiredArgsConstructor
public class RoleRepository {
    private final ResourceRepository resourceRepository;

    public List<RoleEntity> listTenantRoles(int tenantId) {
        var sql = SqlUtils.getSQL("role/role_get_direct_by_tenant.sql");
        return resourceRepository.list(tenantId, RoleEntity.class, sql);
    }
}
