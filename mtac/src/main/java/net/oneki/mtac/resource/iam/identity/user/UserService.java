package net.oneki.mtac.resource.iam.identity.user;

import java.time.Instant;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.dto.UpsertResponse;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.security.Claims;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.identity.group.Group;
import net.oneki.mtac.model.resource.iam.identity.user.BaseUserUpsertRequest;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.iam.RoleRepository;
import net.oneki.mtac.resource.iam.identity.group.GroupMembershipRepository;
import net.oneki.mtac.resource.iam.identity.group.GroupRepository;
import net.oneki.mtac.resource.tenant.DefaultTenantService;

@Service("mtacUserService")
@Slf4j
@RequiredArgsConstructor
public abstract class UserService<U extends BaseUserUpsertRequest<? extends Group>, E extends User>
    extends ResourceService<U, E> {
  // private final UserRepository userRepository;
  protected GroupMembershipRepository groupMembershipRepository;
  protected GroupRepository groupRepository;
  // private final TenantRepository tenantRepository;
  protected JwtTokenService tokenService;
  protected ResourceRepository resourceRepository;
  protected RoleRepository roleRepository;
  protected SecurityContext securityContext;
  protected TokenRegistry tokenRegistry;
  protected TokenRepository tokenRepository;
  protected MtacProperties mtacProperties;
  protected DefaultTenantService tenantService;

  @Value("${mtac.iam.tenants-in-idtoken.enabled:false}")
  private boolean storeTenantsInIdToken;
  @Value("${mtac.iam.tenants-in-idtoken.schema-labels:}")
  private Set<String> storeTenantsSchemaLabels;

  @Autowired
  public final void setGroupMembershipRepository(GroupMembershipRepository groupMembershipRepository) {
    this.groupMembershipRepository = groupMembershipRepository;
  }

  @Autowired
  public final void setGroupRepository(GroupRepository groupRepository) {
    this.groupRepository = groupRepository;
  }

  @Autowired
  public final void setTokenService(JwtTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Autowired
  public final void setResourceRepository(ResourceRepository resourceRepository) {
    this.resourceRepository = resourceRepository;
  }

  @Autowired
  public final void setRoleRepository(RoleRepository roleRepository) {
    this.roleRepository = roleRepository;
  }

  @Autowired
  public final void setSecurityContext(SecurityContext securityContext) {
    this.securityContext = securityContext;
  }

  @Autowired
  public final void setTokenRegistry(TokenRegistry tokenRegistry) {
    this.tokenRegistry = tokenRegistry;
  }

  @Autowired
  public final void setTokenRepository(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  @Autowired
  public final void setMtacProperties(MtacProperties mtacProperties) {
    this.mtacProperties = mtacProperties;
  }

  @Autowired
  public final void setTenantService(DefaultTenantService defaultTenantService) {
    this.tenantService = defaultTenantService;
  }

  @Value("${jwt.expiration-sec:86400}")
  protected int accessTokenExpirationSec;

  @Value("${jwt.refresh-expiration-sec:2678400}")
  protected int refreshTokenExpirationSec;

  public E getByLabel(String tenantLabel, String userLabel) {
    return resourceRepository.getByLabel(userLabel, tenantLabel, getEntityClass());
  }

  public Claims userinfo() {
    log.debug("Inside userinfo() method");
    return userinfo(false);
  }

  public Claims userinfo(boolean forceRefresh) {
    return userinfo(securityContext.getSubject(), forceRefresh);
  }

  public Claims userinfo(String sub, boolean forceRefresh) {
    log.debug("Inside userinfo() method, get from tokenRegistry for sub: {}", sub);
    var claims = tokenRegistry.get(sub);
    log.debug("Claims from tokenRegistry: {}", claims);
    if (claims != null) {
      return claims;
    }
    var user = getByUidUnsecure(sub);
    return userinfo(user, forceRefresh);
  }

  public Claims userinfo(User user, boolean forceRefresh) {
    return userinfo(user, false, forceRefresh);
  }

  public Claims userinfo(User user, boolean includeRoleName, boolean forceRefresh) {
    var claims = tokenRegistry.get(user.getUid());
    if (claims != null) {
      // fillUserInfo(claims, user); // sometimes there is a bug and it seems
      // fillUserInfo was not called
      // so we force a call here
      return claims;
    } else {
      claims = new Claims();
    }
    claims.put("sub", user.getUid());
    claims.put("username", user.getLabel());
    claims.put("email", user.getEmail());
    claims.put("firstName", user.getFirstName());
    claims.put("lastName", user.getLastName());
    claims.putAll(tenantService.getTenantClaims(user, includeRoleName).asClaims());

    fillUserInfo(claims, user);

    // store the token in the cache
    tokenRegistry.put(user.getUid(), claims);
    tokenRepository.upsertToken(user.getId(), user.getUid(), claims,
        Instant.now().plusSeconds(accessTokenExpirationSec));
    log.debug("User info claims stored in tokenRepository for user: {}", user.getUid());
    return claims;
  }

  public Claims buildClaims(boolean includeRoleName) {
    return buildClaims(securityContext.getSubject(), includeRoleName);
  }

  public Claims buildClaims(String sub, boolean includeRoleName) {
    var user = getByUidUnsecure(sub);
    return buildClaims(user, includeRoleName);
  }

  public Claims buildClaims(User user, boolean includeRoleName) {
    var claims = new Claims();
    claims.put("sub", user.getUid());
    claims.put("username", user.getLabel());
    claims.put("email", user.getEmail());
    claims.put("firstName", user.getFirstName());
    claims.put("lastName", user.getLastName());
    claims.putAll(tenantService.getTenantClaims(user, includeRoleName).asClaims());

    fillUserInfo(claims, user);
    return claims;
  }

  // public void verifyResetToken(String resetToken) {
  // try {
  // tokenService.verify(resetToken);
  // } catch (Exception e) {
  // throw new BusinessException("INVALID_RESET_TOKEN", "Invalid or expired reset
  // token");
  // }
  // }

  // @Transactional
  // public void resetPassword(ResetPasswordRequest request) {
  // try {
  // var claims = tokenService.verify(request.getResetToken());
  // var email = claims.get("email", String.class);
  // var resetToken = claims.get("resetToken", String.class);
  // if (email == null || resetToken == null) {
  // throw new BusinessException("INVALID_RESET_TOKEN", "Invalid reset token");
  // }
  // var user = getByUniqueLabelUnsecureOrReturnNull(email);
  // if (user == null) {
  // throw new BusinessException("USER_NOT_FOUND", "User not found for the
  // provided email");
  // }
  // if (user.getResetPasswordToken() == null ||
  // !user.getResetPasswordToken().equals(resetToken)) {
  // throw new BusinessException("INVALID_RESET_TOKEN", "Invalid reset token");
  // }
  // user.setPassword(passwordUtil.hash(request.getNewPassword()));
  // user.setResetPasswordToken(null);
  // updateUnsecure(user);

  // } catch (Exception e) {
  // throw new BusinessException("RESET_PASSWORD_FAILED", "Failed to reset
  // password: " + e.getMessage());
  // }
  // }

  protected void fillUserInfo(Claims claims, User user) {

  }

  // private void buildTenantRoleIndexes(TenantRole tenantRole, Map<String,
  // RoleUserInfo> roleIndex,
  // Map<String, TenantUserInfo> tenantIndex, boolean includeRoleName) {
  // if (tenantRole != null) {
  // for (var role : tenantRole.getRoles()) {
  // var roleUserInfo = RoleUserInfo.builder()
  // .actions(role.getActions())
  // .schemas(role.getSchemas())
  // .build();
  // if (includeRoleName == true) {
  // roleUserInfo.setLabel(role.getName());
  // }
  // roleIndex.put(role.getUid(), roleUserInfo);
  // }
  // var tenant = tenantRole.getTenant();

  // var child = tenantIndex.get(tenant.getUid());
  // if (child != null) {
  // child.setRoles(tenantRole.getRoles().stream()
  // .map(role -> role.getUid())
  // .collect(Collectors.toSet()));
  // } else {
  // child = TenantUserInfo.builder()
  // .uid(tenant.getUid())
  // .label(tenant.getLabel())
  // .schemaLabel(tenant.getSchemaLabel())
  // .roles(tenantRole.getRoles().stream()
  // .map(role -> role.getUid())
  // .collect(Collectors.toSet()))
  // .build();
  // tenantIndex.put(tenant.getUid(), child);

  // var tenantId = tenant.getId();
  // var ancestorTenants = ResourceRegistry.getTenantAncestors(tenantId);

  // for (var ancestor : ancestorTenants) {
  // var parentTenantUserInfo = tenantIndex.get(ancestor.getUid());
  // if (parentTenantUserInfo != null) {
  // parentTenantUserInfo.getChildren().add(child);
  // break;
  // } else {
  // parentTenantUserInfo = TenantUserInfo.builder()
  // .uid(ancestor.getUid())
  // .label(ancestor.getLabel())
  // .schemaLabel(ancestor.getSchemaLabel())
  // .build();
  // parentTenantUserInfo.getChildren().add(child);
  // tenantIndex.put(ancestor.getUid(), parentTenantUserInfo);
  // child = parentTenantUserInfo;
  // }
  // }
  // }
  // }
  // }

  public UpsertResponse<E> create(U request) {
    var userEntity = toCreateEntity(request);
    if (userEntity.getPassword() == null) {
      throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
    } else {
      userEntity.setPassword(PasswordUtil.hash(request.getPassword()));
    }

    var result = resourceRepository.create(userEntity);
    if (userEntity.getMemberOf() != null) {
      for (var group : userEntity.getMemberOf()) {
        groupMembershipRepository.create(group.toRef(), result.toRef());
      }
    }

    return UpsertResponse.<E>builder()
        .status(UpsertResponse.Status.Success)
        .resource(result)
        .build();

  }

  public UpsertResponse<E> update(String urn, U request) {
    var userEntity = toUpdateEntity(urn, request);
    if (request.getPassword() != null) {
      userEntity.setPassword(PasswordUtil.hash(request.getPassword()));
    }
    resourceRepository.update(userEntity);
    return UpsertResponse.<E>builder()
        .status(UpsertResponse.Status.Success)
        .resource(userEntity)
        .build();
  }

  public Set<Integer> listGroupSids(@NonNull Resource user) {
    Set<Integer> groupSids = groupMembershipRepository.listByRight(user.toRef());
    if (groupSids.size() > 0) {
      var nestedGroupSids = groupRepository.listNestedGroupSidsUnsecure(groupSids);
      groupSids.addAll(nestedGroupSids);
    }
    return groupSids;
  }

}
