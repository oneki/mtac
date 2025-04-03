package net.oneki.mtac.core.repository.acl;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import net.oneki.mtac.core.repository.framework.AbstractRepository;
import net.oneki.mtac.core.util.sql.ReactiveSqlUtils;
import reactor.core.publisher.Mono;

@Repository
public class AceRepository extends AbstractRepository {
    public Mono<Void> create(Integer roleId, Integer resourceId, Integer identityId) {
        var sql = ReactiveSqlUtils.getReactiveSQL("ace/ace_insert.sql");
        
        return sql
            .flatMap(sqlQuery -> {
                try {
                    return db.sql(sqlQuery)
                        .bind("roleId", roleId)
                        .bind("resourceId", resourceId)
                        .bind("identityId", identityId)
                        .fetch()
                        .rowsUpdated()
                        .then();
                } catch (DuplicateKeyException e) {
                    return Mono.empty();
                }
            });
    }
}
