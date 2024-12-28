package net.oneki.mtac.resource.iam.identity.user;

import java.util.Map;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.ResourceRepository;
import net.oneki.mtac.core.repository.TenantRepository;
import net.oneki.mtac.core.service.security.JwtTokenService;
import net.oneki.mtac.core.util.SetUtils;
import net.oneki.mtac.core.util.exception.BusinessException;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.iam.identity.GroupMembershipRepository;
import net.oneki.mtac.resource.iam.identity.GroupRepository;
import net.oneki.mtac.resource.iam.identity.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService extends ResourceService<UserUpsertRequest, User> {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final GroupMembershipRepository groupMembershipRepository;
  private final GroupRepository groupRepository;
  private final TenantRepository tenantRepository;
  private final JwtTokenService tokenService;
  private final ResourceRepository resourceRepository;

  public User getByLabel(String tenantLabel, String userLabel) {
    return resourceRepository.getByLabel(userLabel, tenantLabel, User.class);
  }

  public Map<String, Object> login(String username, String password) {
    if (password == null || password.equals("")) {
      throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
    }

    var user = userRepository.getUserUnsecure(username);
    if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
      throw new BusinessException("INVALID_CREDENTIALS", "User/Password incorrect");
    }
    Set<Integer> sids = SetUtils.of(user.getId());
    sids.addAll(listGroupSids(user));
    var tenantSids = tenantRepository.listUserTenantSidsUnsecure(sids);

    return tokenService.generateExpiringToken(Map.of(
        "sub", user.getLabel(),
        "tenantSids", tenantSids,
        "sids", sids));
  }

  public User create(UserUpsertRequest request) {
    var userEntity = toCreateEntity(request, User.class);
    if (userEntity.getPassword() == null) {
      throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
    }
    userEntity.setPassword(passwordEncoder.encode(userEntity.getPassword()));
    return userRepository.create(userEntity);

  }

  public User update(String urn, UserUpsertRequest request) {
    var userEntity = toUpdateEntity(urn, request, User.class);
    if (request.getPassword() != null) {
      userEntity.setPassword(passwordEncoder.encode(request.getPassword()));
    }
    return userRepository.update(userEntity);
  }

  public Set<Integer> listGroupSids(@NonNull User user) {
    Set<Integer> groupSids = groupMembershipRepository.listByRight(user.toRef());
    if (groupSids.size() > 0) {
      var nestedGroupSids = groupRepository.listNestedGroupSidsUnsecure(groupSids);
      groupSids.addAll(nestedGroupSids);
    }
    return groupSids;
  }

}
