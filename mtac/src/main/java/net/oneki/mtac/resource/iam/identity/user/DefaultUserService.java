package net.oneki.mtac.resource.iam.identity.user;

import org.springframework.stereotype.Component;

import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.model.resource.iam.identity.user.UserUpsertRequest;

@Component
public class DefaultUserService extends UserService<UserUpsertRequest, User> {

    @Override
    public Class<User> getEntityClass() {
        return User.class;
    }

    @Override
    public Class<UserUpsertRequest> getRequestClass() {
        return UserUpsertRequest.class;
    }

    
}
