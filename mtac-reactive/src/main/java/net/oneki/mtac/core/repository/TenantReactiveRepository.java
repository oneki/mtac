package net.oneki.mtac.core.repository;

import java.util.Set;

import org.springframework.stereotype.Repository;

import net.oneki.mtac.core.repository.framework.AbstractRepository;
import net.oneki.mtac.core.util.sql.ReactiveSqlUtils;
import reactor.core.publisher.Flux;

@Repository
public class TenantReactiveRepository extends AbstractRepository {

    public Flux<Integer> listUserTenantSidsUnsecure(Set<Integer> userSids) {
        return ReactiveSqlUtils.getReactiveSQL("tenant/tenant_get_by_user_unsecure.sql")
                .flatMapMany(sql -> {
                    return db.sql(sql)
                            .bind("userSids", userSids)
                            .map((row, rowMetadata) -> row.get(0, Integer.class))
                            .all();
                });

    }
}
