package net.oneki.mtac.test.api.resource.iam.identity.user;

import org.springframework.stereotype.Service;

import net.oneki.mtac.resource.ResourceService;


@Service
public class UserService extends ResourceService<UserUpsertRequest, User> {

    @Override
    public Class<User> getEntityClass() {
        return User.class;
    }

    @Override
    public Class<UserUpsertRequest> getRequestClass() {
        return UserUpsertRequest.class;
    }

}
