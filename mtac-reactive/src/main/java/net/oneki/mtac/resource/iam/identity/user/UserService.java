package net.oneki.mtac.resource.iam.identity.user;

import java.util.Map;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.ResourceReactiveRepository;
import net.oneki.mtac.core.repository.TenantReactiveRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.util.SetUtils;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.model.resource.iam.identity.user.UserUpsertRequest;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.iam.identity.group.GroupMembershipRepository;
import net.oneki.mtac.resource.iam.identity.group.GroupRepository;
import reactor.core.publisher.Mono;

@Service("mtacUserService")
@RequiredArgsConstructor
public class UserService extends ResourceService<UserUpsertRequest, User> {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final GroupMembershipRepository groupMembershipRepository;
  private final GroupRepository groupRepository;
  private final TenantReactiveRepository tenantRepository;
  private final JwtTokenService tokenService;
  private final ResourceReactiveRepository resourceRepository;
  private final SecurityContext securityContext;

  @Override
  public Class<User> getEntityClass() {
    return User.class;
  }

  @Override
  public Class<UserUpsertRequest> getRequestClass() {
    return UserUpsertRequest.class;
  }

  public Mono<User> getByLabel(String tenantLabel, String userLabel) {
    return resourceRepository.getByLabel(userLabel, tenantLabel, User.class);
  }

  public Mono<Map<String, Object>> login(String username, String password) {
    if (password == null || password.equals("")) {
      throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
    }

    return userRepository.getUserUnsecure(username)
        .switchIfEmpty(Mono.error(new BusinessException("INVALID_CREDENTIALS", "User/Password incorrect")))
        .map(user -> {
          if (user == null) {
            throw new BusinessException("INVALID_CREDENTIALS", "User/Password incorrect");
          }
          Set<Integer> sids = SetUtils.of(user.getId());
          sids.addAll(listGroupSids(user));
          var tenantSids = tenantRepository.listUserTenantSidsUnsecure(sids);

          return tokenService.generateExpiringToken(Map.of(
              "sub", user.getLabel(),
              "tenantSids", tenantSids,
              "sids", sids));
        });

  }

  public Mono<Map<String, Object>> userinfo() {
    return getByLabelOrUrn(securityContext.getUsername())
      .map(user -> {
        return Map.of(
            "email", user.getEmail(),
            "firstName", user.getFirstName(),
            "lastName", user.getLastName());
      });
  }

  public Mono<User> create(UserUpsertRequest request) {
    return toCreateEntity(request)
        .doOnNext(user -> {
          if (user.getPassword() == null) {
            throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
          }
        })
        .flatMap(user -> {
          user.setPassword(passwordEncoder.encode(user.getPassword()));
          return userRepository.create(user);
        });
  }

  public Mono<User> update(String urn, UserUpsertRequest request) {
    return toUpdateEntity(urn, request)
        .doOnNext(user -> {
          if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
          }
        })
        .flatMap(user -> {
          return userRepository.update(user);
        });
  }

  public Set<Integer> listGroupSids(@NonNull User user) {
    Set<Integer> groupSids = groupMembershipRepository.listByRight(user.toRef()).block();
    if (groupSids.size() > 0) {
      var nestedGroupSids = groupRepository.listNestedGroupSidsUnsecure(groupSids).block();
      groupSids.addAll(nestedGroupSids);
    }
    return groupSids;
  }

}
