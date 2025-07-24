package net.oneki.mtac.resource.iam.identity.user;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.security.RoleUserInfo;
import net.oneki.mtac.model.core.security.TenantRole;
import net.oneki.mtac.model.core.security.TenantUserInfo;
import net.oneki.mtac.model.core.security.TenantWithHierarchy;
import net.oneki.mtac.model.core.security.TenantWithHierarchy.TenantHierarchy;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.exception.UnauthenticatedException;
import net.oneki.mtac.model.core.util.security.Claims;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.Tenant;
import net.oneki.mtac.model.resource.iam.Role;
import net.oneki.mtac.model.resource.iam.identity.group.Group;
import net.oneki.mtac.model.resource.iam.identity.user.BaseUserUpsertRequest;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.iam.RoleRepository;
import net.oneki.mtac.resource.iam.identity.group.GroupMembershipRepository;
import net.oneki.mtac.resource.iam.identity.group.GroupRepository;

@Service("mtacUserService")
@RequiredArgsConstructor
public abstract class UserService<U extends BaseUserUpsertRequest<? extends Group>, E extends User>
    extends ResourceService<U, E> {
  // private final UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private GroupMembershipRepository groupMembershipRepository;
  private GroupRepository groupRepository;
  // private final TenantRepository tenantRepository;
  protected JwtTokenService tokenService;
  private ResourceRepository resourceRepository;
  private RoleRepository roleRepository;
  private SecurityContext securityContext;
  private TokenRegistry tokenRegistry;
  private TokenRepository tokenRepository;

  @Value("${mtac.iam.tenants-in-idtoken.enabled:false}")
  private boolean storeTenantsInIdToken;
  @Value("${mtac.iam.tenants-in-idtoken.schema-labels:}")
  private Set<String> storeTenantsSchemaLabels;

  @Autowired
  public final void setPasswordEncoder(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

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

  @Value("${jwt.expiration-sec:86400}")
  protected int accessTokenExpirationSec;

  @Value("${jwt.refresh-expiration-sec:2678400}")
  protected int refreshTokenExpirationSec;

  public E getByLabel(String tenantLabel, String userLabel) {
    return resourceRepository.getByLabel(userLabel, tenantLabel, getEntityClass());
  }

  public Map<String, Object> login(String username, String password) {
    if (password == null || password.equals("")) {
      throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
    }

    var user = resourceRepository.getByLabelOrUrnUnsecure(username, getEntityClass());
    if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
      throw new BusinessException("INVALID_CREDENTIALS", "User/Password incorrect");
    }

    var accessTokenClaims = new Claims();
    accessTokenClaims.put("jti", UUID.randomUUID().toString());
    var sub = Resource.toUid(user.getId());
    accessTokenClaims.put("sub", sub);
    accessTokenClaims.put("username", user.getLabel());

    var idTokenClaims = userinfo(sub, true);

    // Refresh token expiration
    user.setRefreshExp(System.currentTimeMillis() / 1000 + refreshTokenExpirationSec);
    updateUnsecure(user);

    return tokenService.generateExpiringToken(accessTokenClaims, idTokenClaims);

  }

  private String generatecAccessToken(User user) {
     var accessTokenClaims = new Claims();
    accessTokenClaims.put("jti", UUID.randomUUID().toString());
    var sub = Resource.toUid(user.getId());
    accessTokenClaims.put("sub", sub);
    accessTokenClaims.put("username", user.getLabel());


    return tokenService.newToken(accessTokenClaims);
  }

  public void logout() {
    tokenRegistry.remove(securityContext.getSubject());
    tokenRepository.deleteToken(securityContext.getSubject());
  }

  public void triggerResetPassword(String email, String resetLink, BiConsumer<String, User> resetLinkSender) {
    E user = null;
    try {
      user = getByUniqueLabelUnsecureOrReturnNull(email);
    } catch (Exception e) {
      // user not found, do nothing
      return;
    }

    if (user == null)
      return;
    var resetToken = UUID.randomUUID().toString();
    // generete JWT token that contains the reset token
    var jwtToken = tokenService.newToken(Map.of("resetToken", resetToken, "email", email), 86400);
    // update user attribute to store the reset token
    user.setResetPasswordToken(resetToken);
    updateUnsecure(user);
    if (!resetLink.contains("?"))
      resetLink += "?";
    ;
    resetLink += "resetToken=" + jwtToken;
    resetLinkSender.accept(resetLink, user);
  }

  public Claims userinfo() {
    return userinfo(false);
  }
  public Claims userinfo(boolean forceRefresh) {
    return userinfo(securityContext.getSubject(), forceRefresh);
  }

  public Claims userinfo(String sub, boolean forceRefresh) {
    var claims = tokenRegistry.get(sub);
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
      return claims;
    } else {
      claims = new Claims();
    }
    claims.put("sub", user.getUid());
    claims.put("username", user.getLabel());
    claims.put("email", user.getEmail());
    claims.put("firstName", user.getFirstName());
    claims.put("lastName", user.getLastName());

    var sids = new ArrayList<Integer>();
    sids.add(user.getId());
    sids.addAll(listGroupSids(user));
    var tenantRoles = roleRepository.listAssignedTenantRolesByIdentity(sids);
    var tenantSids = tenantRoles.stream()
        .filter(tenantRole -> tenantRole.getRoles().size() > 0)
        .map(tenantRole -> tenantRole.getTenant().getId())
        .collect(Collectors.toList());
    claims.put("tenantSids", tenantSids);
    claims.put("sids", sids);
    var roleIndex = new HashMap<String, RoleUserInfo>();
    var tenantIndex = new HashMap<String, TenantUserInfo>();
    for (var tenantRole : tenantRoles) {
      buildTenantRoleIndexes(tenantRole, roleIndex, tenantIndex, includeRoleName);
      // var tenantId = tenantRole.getTenant().getId();
      // var ancestorTenants = ResourceRegistry.getTenantAncestors(tenantId);
      // var hierarchy = ancestorTenants.stream()
      // .map(ancestor -> (TenantHierarchy) TenantHierarchy.builder()
      // .uid(ancestor.getUid())
      // .label(ancestor.getLabel())
      // .schemaLabel(ancestor.getSchemaLabel())
      // .build())
      // .toList();
      // tenantRole.getTenant().setHierarchy(hierarchy);
    }
    claims.put("roles", roleIndex);
    claims.put("tenants", tenantIndex.get(Resource.toUid(Constants.TENANT_ROOT_ID)));
    if (storeTenantsInIdToken) {
      // build a Set of tenantIds from the tenantIndex
      var tenantUids = tenantIndex.values().stream()
          .filter(tenantUserInfo -> {
            return tenantUserInfo.getSchemaLabel() != null
                && storeTenantsSchemaLabels.contains(tenantUserInfo.getSchemaLabel());
          })
          .map(TenantUserInfo::getUid)
          .collect(Collectors.toSet());
      claims.put("tenantUids", tenantUids);
    }
    fillUserInfo(claims, user);
    // store the token in the cache
    tokenRegistry.put(user.getUid(), claims);
    tokenRepository.upsertToken(user.getId(), user.getUid(), claims, Instant.now().plusSeconds(accessTokenExpirationSec));

    return claims;
  }

  public String refreshToken(String sub, String token) {
    var user = getByUidUnsecure(sub);
    var refreshExp = user.getRefreshExp();
    if (refreshExp == null || refreshExp < System.currentTimeMillis() / 1000) {
      throw new UnauthenticatedException();
    }
    // generate a new access token
    var accessToken = generatecAccessToken(user);

    //  generate a new id token
    userinfo(sub, true);

    return accessToken;
  }

  protected void fillUserInfo(Claims claims, User user) {
  }

  private void buildTenantRoleIndexes(TenantRole tenantRole, Map<String, RoleUserInfo> roleIndex,
      Map<String, TenantUserInfo> tenantIndex, boolean includeRoleName) {
    if (tenantRole != null) {
      for (var role : tenantRole.getRoles()) {
        var roleUserInfo = RoleUserInfo.builder()
            .actions(role.getActions())
            .schemas(role.getSchemas())
            .build();
        if (includeRoleName == true) {
          roleUserInfo.setLabel(role.getName());
        }
        roleIndex.put(role.getUid(), roleUserInfo);
      }
      var tenant = tenantRole.getTenant();

      var child = tenantIndex.get(tenant.getUid());
      if (child != null) {
        child.setRoles(tenantRole.getRoles().stream()
            .map(role -> role.getUid())
            .collect(Collectors.toSet()));
      } else {
        child = TenantUserInfo.builder()
            .uid(tenant.getUid())
            .label(tenant.getLabel())
            .schemaLabel(tenant.getSchemaLabel())
            .roles(tenantRole.getRoles().stream()
                .map(role -> role.getUid())
                .collect(Collectors.toSet()))
            .build();
        tenantIndex.put(tenant.getUid(), child);

        var tenantId = tenant.getId();
        var ancestorTenants = ResourceRegistry.getTenantAncestors(tenantId);

        for (var ancestor : ancestorTenants) {
          var parentTenantUserInfo = tenantIndex.get(ancestor.getUid());
          if (parentTenantUserInfo != null) {
            parentTenantUserInfo.getChildren().add(child);
            break;
          } else {
            parentTenantUserInfo = TenantUserInfo.builder()
                .uid(ancestor.getUid())
                .label(ancestor.getLabel())
                .schemaLabel(ancestor.getSchemaLabel())
                .build();
            parentTenantUserInfo.getChildren().add(child);
            tenantIndex.put(ancestor.getUid(), parentTenantUserInfo);
            child = parentTenantUserInfo;
          }
        }
      }
    }
  }

  public E create(U request) {
    var userEntity = toCreateEntity(request);
    if (userEntity.getPassword() == null) {
      throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
    }

    var result = resourceRepository.create(userEntity);
    if (userEntity.getMemberOf() != null) {
      for (var group : userEntity.getMemberOf()) {
        groupMembershipRepository.create(group.toRef(), result.toRef());
      }
    }

    return result;

  }

  public E update(String urn, U request) {
    var userEntity = toUpdateEntity(urn, request);
    if (request.getPassword() != null) {
      userEntity.setPassword(passwordEncoder.encode(request.getPassword()));
    }
    resourceRepository.update(userEntity);
    return userEntity;
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
