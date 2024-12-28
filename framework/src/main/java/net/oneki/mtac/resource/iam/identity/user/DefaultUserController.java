package net.oneki.mtac.resource.iam.identity.user;

import org.springframework.beans.factory.annotation.Autowired;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.api.ResourceController;
import net.oneki.mtac.core.repository.ResourceRepository;
import net.oneki.mtac.core.service.RelationService;

@RequiredArgsConstructor
public class DefaultUserController extends ResourceController<UserUpsertRequest, User, UserService> {
    @Autowired private UserService userService;
    private final ResourceRepository resourceRepository;
    private final RelationService relationService;
    // private final UserRepository userRepository;

    // @GetMapping("/users")
    // public List<ResourceEntity<UserEntity>> listAllUsers() {
    // return resourceRepository.listByTenantAndType(Constants.TENANT_ROOT_ID,
    // UserEntity.class, null);
    // }

    // // GET one entity
    // @GetMapping("/users/{label_or_urn}")
    // public User getByLabelOrUrn(@PathVariable("label_or_urn") String labelOrUrn) {
    //     return resourceRepository.getByLabelOrUrn(labelOrUrn, User.class);
    // }

    // @GetMapping("/users/{tenantLabel}/{label}")
    // public User getByLabel(@PathVariable("tenantLabel") String tenantLabel,
    //         @PathVariable("label") String label) {
    //     return resourceRepository.getByLabel(label, tenantLabel, User.class);
    // }

    // // List / search entities
    // @GetMapping(value = "/users")
    // public Page<User> search(@RequestParam Map<String, String> params) throws Exception {
    //     var query = Query.fromRest(params, User.class);
    //     return user.list(query, User.class);
    // }

    // @PostMapping("/users")
    // public User createUser(@RequestBody BaseUserUpsertRequest<Group> user) {
    //     var result = userService.create(user);
    //     return result;
    // }

    // @PostMapping("/users/relations/{relations}")
    // public List<User> populateRelations(@RequestBody List<User> entities, @PathVariable("relations") Set<String> relations) {
    //     return relationService.populateRelations(entities, relations);
    // }

    // @PostMapping("/user/relations/{relations}")
    // public User populateRelations(@RequestBody User entity, @PathVariable("relations") Set<String> relations) {
    //     return relationService.populateRelations(entity, relations);

    // }

    // @PutMapping("/users")
    // public User updateUser(@RequestBody BaseUserUpsertRequest<Group> user) {
    //     var result = userService.update(user);
    //     return result;
    // }

    // @DeleteMapping("/users/{label_or_urn}")
    // public void deleteUser(@PathVariable("label_or_urn") String labelOrUrn) {
    //     resourceService.deleteByLabelOrUrn(labelOrUrn, User.class);
    // }

    // @DeleteMapping("/tenants/{tenantLabel}/users/{userLabel}")
    // public void deleteUser(@PathVariable("tenantLabel") String tenantLabel,
    //         @PathVariable("userLabel") String userLabel) {
    //     resourceService.deleteByLabel(tenantLabel, userLabel, User.class);
    // }

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
