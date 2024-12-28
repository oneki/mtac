package net.oneki.mtac.resource.iam.identity.group;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;

import net.oneki.mtac.core.repository.framework.AbstractRepository;
import net.oneki.mtac.core.util.sql.SqlUtils;

@Repository
public class GroupRepository extends AbstractRepository {

    public Set<Integer> listNestedGroupSidsUnsecure(Set<Integer> groupSids) {
        String sql = SqlUtils.getSQL("group/group_sids.sql");
        Map<String, Object> args = new HashMap<>();
        args.put("groupIds", groupSids);
        return new HashSet<>(jdbcTemplate.query(sql, args, new SingleColumnRowMapper<Integer>()));

    }
}
