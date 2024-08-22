package net.oneki.mtac.repository.iam.identity;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.model.entity.Ref;
import net.oneki.mtac.model.entity.ResourceEntity;
import net.oneki.mtac.model.entity.iam.identity.DefaultUserEntity;
import net.oneki.mtac.repository.framework.BaseRepository;
import net.oneki.mtac.util.sql.SqlUtils;

@Repository
@RequiredArgsConstructor
public class UserRepository extends BaseRepository<DefaultUserEntity> {
    protected final GroupMembershipRepository groupMembershipRepository;
    
    public DefaultUserEntity getUserUnsecure(String username) {
        var sql = SqlUtils.getSQL("user/user_get_by_label_unsecure.sql");
        return resourceRepository.getByLabel(username, null, DefaultUserEntity.class, sql);
    }

    // Create a new user
    @Transactional
    public DefaultUserEntity create(DefaultUserEntity userEntity) {
        final var result =  resourceRepository.create(userEntity);
        userEntity.getMemberOf().forEach(group -> {
            groupMembershipRepository.create(group.toRef(), userEntity.toRef());
        });

        return result;
    }

    // update user
    public DefaultUserEntity update(DefaultUserEntity userEntity) {
        var groups = userEntity.getMemberOf();
        resourceRepository.update(userEntity);
        if (groups != null) {
            Set<Ref> groupsRef = groups.stream().map(ResourceEntity::toRef).collect(Collectors.toSet());
            groupMembershipRepository.updateByRight(userEntity.toRef(), groupsRef);
        }
        return userEntity;
    }

    @Override
    public Class<DefaultUserEntity> getResourceContentClass() {
        return DefaultUserEntity.class;
    }
}
