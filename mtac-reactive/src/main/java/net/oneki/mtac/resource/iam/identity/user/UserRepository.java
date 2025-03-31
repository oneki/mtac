package net.oneki.mtac.resource.iam.identity.user;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.framework.BaseRepository;
import net.oneki.mtac.framework.util.sql.SqlUtils;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.iam.identity.group.GroupMembershipRepository;

@Repository
@RequiredArgsConstructor
public class UserRepository extends BaseRepository<User> {
    protected final GroupMembershipRepository groupMembershipRepository;
    
    public User getUserUnsecure(String username) {
        var sql = SqlUtils.getSQL("user/user_get_by_label_unsecure.sql");
        return resourceRepository.getByLabel(username, null, User.class, sql);
    }

    // Create a new user
    @Transactional
    public User create(User userEntity) {
        final var result =  resourceRepository.create(userEntity);
        userEntity.getMemberOf().forEach(group -> {
            groupMembershipRepository.create(group.toRef(), userEntity.toRef());
        });

        return result;
    }

    // update user
    public User update(User userEntity) {
        var groups = userEntity.getMemberOf();
        resourceRepository.update(userEntity);
        if (groups != null) {
            Set<Ref> groupsRef = groups.stream().map(Resource::toRef).collect(Collectors.toSet());
            groupMembershipRepository.updateByRight(userEntity.toRef(), groupsRef);
        }
        return userEntity;
    }

    @Override
    public Class<User> getResourceContentClass() {
        return User.class;
    }
}
