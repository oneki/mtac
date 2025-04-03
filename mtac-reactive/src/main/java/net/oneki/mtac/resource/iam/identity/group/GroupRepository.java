package net.oneki.mtac.resource.iam.identity.group;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import net.oneki.mtac.core.repository.framework.AbstractRepository;
import net.oneki.mtac.core.util.sql.ReactiveSqlUtils;
import reactor.core.publisher.Mono;

@Repository
public class GroupRepository extends AbstractRepository {

    public Mono<Set<Integer>> listNestedGroupSidsUnsecure(Set<Integer> groupSids) {
        return ReactiveSqlUtils.getReactiveSQL("group/group_sids.sql")
            .flatMapMany(sql -> {
                return db.sql(sql)
                    .bind("groupIds", groupSids)
                    .fetch()
                    .all()
                    .map(row -> (Integer) row.get("group_id")); 
            })
            .collect(Collectors.toSet());

    }
}
