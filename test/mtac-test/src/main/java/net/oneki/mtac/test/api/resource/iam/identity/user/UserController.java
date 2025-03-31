package net.oneki.mtac.test.api.resource.iam.identity.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.oneki.mtac.api.ResourceController;

@RestController
@RequestMapping("/api")
public class UserController extends ResourceController<UserUpsertRequest, User, UserService> {
    @Autowired protected UserService userService;
    
    @Override
    protected Class<UserUpsertRequest> getRequestClass() {
        return UserUpsertRequest.class;
    }

    @Override
    protected Class<User> getResourceClass() {
        return User.class;
    }

    @Override
    protected UserService getService() {
        return userService;
    }

}
