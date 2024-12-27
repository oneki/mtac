package net.oneki.mtac.repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;

import net.oneki.mtac.repository.framework.AbstractRepository;
import net.oneki.mtac.util.sql.SqlUtils;

@Repository
public class TenantRepository extends AbstractRepository {

    public Set<Integer> listUserTenantSidsUnsecure(Set<Integer> userSids) {
        String sql = SqlUtils.getSQL("tenant/tenant_get_by_user_unsecure.sql");
        Map<String, Object> args = new HashMap<>();
        args.put("userSids", userSids);
        return new HashSet<>(jdbcTemplate.query(sql, args, new SingleColumnRowMapper<Integer>()));

    }
}
