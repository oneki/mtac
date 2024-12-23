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
import net.oneki.mtac.model.api.iam.identity.user.DefaultUserUpsertRequest;
import net.oneki.mtac.model.entity.iam.identity.DefaultUserEntity;
import net.oneki.mtac.model.framework.Page;
import net.oneki.mtac.repository.ResourceRepository;
import net.oneki.mtac.service.RelationService;
import net.oneki.mtac.service.ResourceService;
import net.oneki.mtac.service.iam.identity.UserService;
import net.oneki.mtac.util.query.Query;

@RequiredArgsConstructor
public class UserResource {
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
    @GetMapping("/users/{publicId}")
    public DefaultUserEntity getByPublicid(@PathVariable("publicId") UUID publicId) {
        return resourceRepository.getByPublicId(publicId, DefaultUserEntity.class);
    }

    @GetMapping("/users/{tenantLabel}/{label}")
    public DefaultUserEntity getByLabel(@PathVariable("tenantLabel") String tenantLabel,
            @PathVariable("label") String label) {
        return resourceRepository.getByLabel(label, tenantLabel, DefaultUserEntity.class);
    }

    // List / search entities
    @GetMapping(value = "/users")
    public Page<DefaultUserEntity> search(@RequestParam Map<String, String> params) throws Exception {
        var query = Query.fromRest(params, DefaultUserEntity.class);
        return resourceService.list(query, DefaultUserEntity.class);
    }

    @PostMapping("/users")
    public DefaultUserEntity createUser(@RequestBody DefaultUserUpsertRequest user) {
        var result = userService.create(user);
        return result;
    }

    @PostMapping("/users/relations/{relations}")
    public List<DefaultUserEntity> populateRelations(@RequestBody List<DefaultUserEntity> entities, @PathVariable("relations") Set<String> relations) {
        return relationService.populateRelations(entities, relations);
    }

    @PostMapping("/user/relations/{relations}")
    public DefaultUserEntity populateRelations(@RequestBody DefaultUserEntity entity, @PathVariable("relations") Set<String> relations) {
        return relationService.populateRelations(entity, relations);

    }

    @PutMapping("/users")
    public DefaultUserEntity updateUser(@RequestBody DefaultUserUpsertRequest user) {
        var result = userService.update(user);
        return result;
    }

    @DeleteMapping("/users/{publicId}")
    public void deleteUser(@PathVariable("publicId") UUID publicId) {
        resourceService.deleteByPublicId(publicId, DefaultUserEntity.class);
    }

    @DeleteMapping("/tenants/{tenantLabel}/users/{userLabel}")
    public void deleteUser(@PathVariable("tenantLabel") String tenantLabel,
            @PathVariable("userLabel") String userLabel) {
        resourceService.deleteByLabel(tenantLabel, userLabel, DefaultUserEntity.class);
    }

}
