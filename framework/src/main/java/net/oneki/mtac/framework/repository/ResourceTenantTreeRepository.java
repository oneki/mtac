package net.oneki.mtac.framework.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.model.core.util.json.JsonUtil;

@Repository
public class ResourceTenantTreeRepository extends AbstractRepository {


    public Map<Integer, List<Integer>> listTenantAncestors() {
        String sql = """
                SELECT rtt.descendant_id, array_agg(json_build_object('ancestorId', rtt.ancestor_id, 'depth', rtt.depth)) AS ancestor_ids 
                FROM resource_tenant_tree rtt, resource r, resource s 
                WHERE rtt.descendant_id = r.id AND r.schema_id = s.id AND s.label LIKE 'tenant.%' AND rtt.depth > 0  GROUP BY descendant_id """;
        Map<String, Object> parameters = new HashMap<>();
        List<TenantAncestor> tenantAncestors = jdbcTemplate.query(sql, parameters,
                new TenantTreeRowMapper());

        Map<Integer, List<Integer>> tenantAncestorMap = new HashMap<>();
        for (TenantAncestor ta : tenantAncestors) {
            List<Integer> ancestorIds = ta.getAncestors().stream()
                    .map(TenantAncestorWithDepth::getAncestorId)
                    .collect(Collectors.toList());
            tenantAncestorMap.put(ta.getTenantId(), ancestorIds);
        }
        return tenantAncestorMap;
    }

    public List<Integer> listTenantAncestors(Integer tenantId) {
        String sql = "SELECT DISTINCT ancestor_id, depth FROM resource_tenant_tree WHERE descendant_id = :tenantId AND depth > 0 ORDER BY depth";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", tenantId);
        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> {
            return rs.getInt("ancestor_id");
        });
    }

    public static class TenantTreeRowMapper implements RowMapper<TenantAncestor> {

        public TenantAncestor mapRow(ResultSet rs, int rowNum) throws SQLException {
            var ancestorsArr = (String[]) rs.getArray("ancestor_ids").getArray();
            var ancestors = Arrays.asList(ancestorsArr).stream()
                    .map(ta -> JsonUtil.json2Object(ta, TenantAncestorWithDepth.class))
                    .collect(Collectors.toList());
            ancestors.sort((a, b) -> a.getDepth().compareTo(b.getDepth()));
            return new TenantAncestor(Integer.valueOf(rs.getInt("descendant_id")), ancestors);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TenantAncestor {
        private Integer tenantId;
        private List<TenantAncestorWithDepth> ancestors;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TenantAncestorWithDepth {
        private Integer ancestorId;
        private Integer depth;
    }

}
