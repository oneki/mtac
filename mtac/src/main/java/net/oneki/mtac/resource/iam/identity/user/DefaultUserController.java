package net.oneki.mtac.resource.iam.identity.user;

import org.springframework.beans.factory.annotation.Autowired;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.api.ResourceController;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.model.resource.iam.identity.user.UserUpsertRequest;

@RequiredArgsConstructor
public class DefaultUserController extends ResourceController<UserUpsertRequest, User, DefaultUserService> {
    @Autowired private DefaultUserService userService;

    @Override
    protected Class<UserUpsertRequest> getRequestClass() {
        return UserUpsertRequest.class;
    }

    @Override
    protected Class<User> getResourceClass() {
        return User.class;
    }

    @Override
    protected DefaultUserService getService() {
        return userService;
    }

}
