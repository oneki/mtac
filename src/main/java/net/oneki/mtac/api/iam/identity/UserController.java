package net.oneki.mtac.api.iam.identity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.model.api.iam.identity.user.BaseUserUpsertRequest;
import net.oneki.mtac.model.entity.iam.identity.User;
import net.oneki.mtac.model.entity.iam.identity.Group;
import net.oneki.mtac.model.framework.Page;
import net.oneki.mtac.repository.ResourceRepository;
import net.oneki.mtac.service.RelationService;
import net.oneki.mtac.service.ResourceService;
import net.oneki.mtac.service.iam.identity.UserService;
import net.oneki.mtac.util.query.Query;

@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final ResourceService resourceService;
    private final ResourceRepository resourceRepository;
    private final RelationService relationService;
    // private final UserRepository userRepository;

    // @GetMapping("/users")
    // public List<ResourceEntity<UserEntity>> listAllUsers() {
    // return resourceRepository.listByTenantAndType(Constants.TENANT_ROOT_ID,
    // UserEntity.class, null);
    // }

    // GET one entity
    @GetMapping("/users/{label_or_urn}")
    public User getByLabelOrUrn(@PathVariable("label_or_urn") String labelOrUrn) {
        return resourceRepository.getByLabelOrUrn(labelOrUrn, User.class);
    }

    @GetMapping("/users/{tenantLabel}/{label}")
    public User getByLabel(@PathVariable("tenantLabel") String tenantLabel,
            @PathVariable("label") String label) {
        return resourceRepository.getByLabel(label, tenantLabel, User.class);
    }

    // List / search entities
    @GetMapping(value = "/users")
    public Page<User> search(@RequestParam Map<String, String> params) throws Exception {
        var query = Query.fromRest(params, User.class);
        return resourceService.list(query, User.class);
    }

    @PostMapping("/users")
    public User createUser(@RequestBody BaseUserUpsertRequest<Group> user) {
        var result = userService.create(user);
        return result;
    }

    @PostMapping("/users/relations/{relations}")
    public List<User> populateRelations(@RequestBody List<User> entities, @PathVariable("relations") Set<String> relations) {
        return relationService.populateRelations(entities, relations);
    }

    @PostMapping("/user/relations/{relations}")
    public User populateRelations(@RequestBody User entity, @PathVariable("relations") Set<String> relations) {
        return relationService.populateRelations(entity, relations);

    }

    // @PutMapping("/users")
    // public User updateUser(@RequestBody BaseUserUpsertRequest<Group> user) {
    //     var result = userService.update(user);
    //     return result;
    // }

    @DeleteMapping("/users/{label_or_urn}")
    public void deleteUser(@PathVariable("label_or_urn") String labelOrUrn) {
        resourceService.deleteByLabelOrUrn(labelOrUrn, User.class);
    }

    @DeleteMapping("/tenants/{tenantLabel}/users/{userLabel}")
    public void deleteUser(@PathVariable("tenantLabel") String tenantLabel,
            @PathVariable("userLabel") String userLabel) {
        resourceService.deleteByLabel(tenantLabel, userLabel, User.class);
    }

}
