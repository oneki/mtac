package net.oneki.mtac.service.iam.identity;

import java.util.Map;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.model.api.iam.identity.user.DefaultUserUpsertRequest;
import net.oneki.mtac.model.entity.iam.identity.DefaultUserEntity;
import net.oneki.mtac.repository.ResourceRepository;
import net.oneki.mtac.repository.TenantRepository;
import net.oneki.mtac.repository.iam.identity.GroupMembershipRepository;
import net.oneki.mtac.repository.iam.identity.GroupRepository;
import net.oneki.mtac.repository.iam.identity.UserRepository;
import net.oneki.mtac.service.ResourceService;
import net.oneki.mtac.service.security.JwtTokenService;
import net.oneki.mtac.util.SetUtils;
import net.oneki.mtac.util.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final GroupMembershipRepository groupMembershipRepository;
  private final GroupRepository groupRepository;
  private final TenantRepository tenantRepository;
  private final JwtTokenService tokenService;
  private final ResourceRepository resourceRepository;
  private final ResourceService resourceService;

  public DefaultUserEntity getByLabel(String tenantLabel, String userLabel) {
    return resourceRepository.getByLabel(userLabel, tenantLabel, DefaultUserEntity.class);
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

  public DefaultUserEntity create(DefaultUserUpsertRequest request) {
    var userEntity = resourceService.toCreateEntity(request, DefaultUserEntity.class);
    if (userEntity.getPassword() == null) {
      throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
    }
    userEntity.setPassword(passwordEncoder.encode(userEntity.getPassword()));
    return userRepository.create(userEntity);

  }

  public DefaultUserEntity update(DefaultUserUpsertRequest request) {
    var userEntity = resourceService.toUpdateEntity(request, DefaultUserEntity.class);
    if (request.getPassword() != null) {
      userEntity.setPassword(passwordEncoder.encode(request.getPassword()));
    }
    return userRepository.update(userEntity);
  }

  public Set<Integer> listGroupSids(@NonNull DefaultUserEntity user) {
    Set<Integer> groupSids = groupMembershipRepository.listByRight(user.toRef());
    if (groupSids.size() > 0) {
      var nestedGroupSids = groupRepository.listNestedGroupSidsUnsecure(groupSids);
      groupSids.addAll(nestedGroupSids);
    }
    return groupSids;
  }

}
