package net.oneki.mtac.resource.iam.identity.user;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.framework.BaseRepository;
import net.oneki.mtac.core.util.sql.ReactiveSqlUtils;
import net.oneki.mtac.framework.util.sql.SqlUtils;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.iam.identity.group.GroupMembershipRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class UserRepository extends BaseRepository<User> {
    protected final GroupMembershipRepository groupMembershipRepository;
    
    public Mono<User> getUserUnsecure(String username) {
        var sql = ReactiveSqlUtils.getReactiveSQL("user/user_get_by_label_unsecure.sql");
        return resourceRepository.getByLabel(username, null, User.class, sql);
    }

    // Create a new user
    @Transactional
    public Mono<User> create(User userEntity) {
        return resourceRepository.create(userEntity)
            .doOnNext(user -> {
                Flux.fromIterable(userEntity.getMemberOf())
                    .flatMap(group -> {
                        return groupMembershipRepository.create(group.toRef(), userEntity.toRef());
                    })
                    .subscribe();
            });
    }

    // update user
    public Mono<User> update(User userEntity) {
        final var groups = userEntity.getMemberOf();
        final var groupsRef = groups.stream().map(Resource::toRef).collect(Collectors.toSet());
        return resourceRepository.update(userEntity)
            .doOnNext(user -> {
                if (groups != null) {
                    Flux.fromIterable(groups)
                        .flatMap(group -> {
                            return groupMembershipRepository.updateByRight(userEntity.toRef(), groupsRef);
                        })
                        .subscribe();
                }
            });
    }

    @Override
    public Class<User> getResourceContentClass() {
        return User.class;
    }
}
