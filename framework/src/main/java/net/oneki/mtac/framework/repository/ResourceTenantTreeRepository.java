package net.oneki.mtac.framework.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Repository
public class ResourceTenantTreeRepository extends AbstractRepository {


    public Map<Integer, Set<Integer>> listTenantAncestors() {
        String sql = "SELECT rtt.descendant_id, array_agg(DISTINCT rtt.ancestor_id) AS ancestor_ids FROM resource_tenant_tree rtt, resource r, resource s WHERE rtt.descendant_id = r.id AND r.schema_id = s.id AND s.label LIKE 'tenant.%' AND rtt.depth > 0  GROUP BY descendant_id";
        Map<String, Object> parameters = new HashMap<>();
        List<TenantAncestor> schemaDescendants = jdbcTemplate.query(sql, parameters,
                new TenantTreeRowMapper());

        return schemaDescendants.stream()
                .collect(Collectors.toMap(TenantAncestor::getTenantId, TenantAncestor::getAncestors));
    }

    public Set<Integer> listTenantAncestors(Integer tenantId) {
        String sql = "SELECT DISTINCT ancestor_id FROM resource_tenant_tree WHERE descendant_id = :tenantId AND depth > 0";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", tenantId);
        List<Integer> tenantAncestors = jdbcTemplate.query(sql, parameters, new SingleColumnRowMapper<Integer>());

        return new HashSet<Integer>(tenantAncestors);
    }

    public static class TenantTreeRowMapper implements RowMapper<TenantAncestor> {

        public TenantAncestor mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TenantAncestor(Integer.valueOf(rs.getInt("descendant_id")),
                    Set.of((Integer[]) rs.getArray("ancestor_ids").getArray()));
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TenantAncestor {
        private Integer tenantId;
        private Set<Integer> ancestors;

    }

}
