package net.oneki.mtac.core.repository.acl;

import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import net.oneki.mtac.framework.repository.AbstractRepository;
import net.oneki.mtac.framework.util.sql.SqlUtils;

@Repository
public class AceRepository extends AbstractRepository {
    public void create(Integer roleId, Integer resourceId, Integer identityId) {
        var sql = SqlUtils.getSQL("ace/ace_insert.sql");
        Map<String, Integer> parameters = Map.of(
            "roleId", roleId,
            "resourceId", resourceId,
            "identityId", identityId
        );
        try {
            jdbcTemplate.update(sql, parameters);
        } catch (DuplicateKeyException e) {}
    }
}
