package net.oneki.mtac.resource.iam.identity.user;

import java.text.ParseException;
import java.time.Instant;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.jboss.aerogear.security.otp.api.Base32;

import com.nimbusds.jwt.SignedJWT;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.oidc.OidcGrantRevocation;
import net.oneki.mtac.core.service.openid.oidc.TokenRevocationService;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.dto.UpsertResponse;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.exception.ForbiddenException;
import net.oneki.mtac.model.core.util.exception.NotFoundException;
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
  protected OidcGrantRevocation grantRevocation;
  protected TokenRevocationService tokenRevocations; // null: the access tokens of a disabled user live until they expire

  @Value("${mtac.iam.tenants-in-idtoken.enabled:false}")
  private boolean storeTenantsInIdToken;
  @Value("${mtac.iam.tenants-in-idtoken.schema-labels:}")
  private Set<String> storeTenantsSchemaLabels;

  @Autowired
  public void setGroupMembershipRepository(GroupMembershipRepository groupMembershipRepository) {
    this.groupMembershipRepository = groupMembershipRepository;
  }

  @Autowired
  public void setGroupRepository(GroupRepository groupRepository) {
    this.groupRepository = groupRepository;
  }

  @Autowired
  public void setTokenService(JwtTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Autowired
  public void setResourceRepository(ResourceRepository resourceRepository) {
    this.resourceRepository = resourceRepository;
  }

  @Autowired
  public void setRoleRepository(RoleRepository roleRepository) {
    this.roleRepository = roleRepository;
  }

  @Autowired
  public void setSecurityContext(SecurityContext securityContext) {
    this.securityContext = securityContext;
  }

  @Autowired
  public void setTokenRegistry(TokenRegistry tokenRegistry) {
    this.tokenRegistry = tokenRegistry;
  }

  @Autowired
  public void setTokenRepository(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  @Autowired
  public void setMtacProperties(MtacProperties mtacProperties) {
    this.mtacProperties = mtacProperties;
  }

  @Autowired
  public void setTenantService(DefaultTenantService defaultTenantService) {
    this.tenantService = defaultTenantService;
  }

  @Autowired
  public void setGrantRevocation(OidcGrantRevocation grantRevocation) {
    this.grantRevocation = grantRevocation;
  }

  @Autowired(required = false)
  public void setTokenRevocationService(TokenRevocationService tokenRevocations) {
    this.tokenRevocations = tokenRevocations;
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
    if (claims != null && !forceRefresh) {
      return claims;
    }
    var user = getByUidUnsecure(sub);
    return userinfo(user, forceRefresh);
  }

  public Claims userinfo(User user, boolean forceRefresh) {
    return userinfo(user, false, forceRefresh, false);
  }

  public Claims userinfo(User user, boolean includeRoleName, boolean forceRefresh, boolean light) {
    var claims = tokenRegistry.get(user.getUid());
    if (claims != null && !forceRefresh) {
      if (!light) {
        // sometimes there is a bug and it seems fillUserInfo was not called
        // so we force a call here        
        fillUserInfo(claims, user); 
      }
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

  /**
   * Updates a user. A new password or a change of the MFA requirement revokes
   * every delegated access of the user ({@link OidcGrantRevocation}) in the same
   * transaction: if the revocation fails, the update is rolled back. The user who
   * changes his own password keeps his Console session; any other change
   * (password set by somebody else, MFA turned on or off) invalidates it too.
   */
  @Transactional
  public UpsertResponse<E> update(String urn, U request) {
    var userEntity = toUpdateEntity(urn, request);
    var passwordChanged = request.getPassword() != null;
    if (passwordChanged) {
      userEntity.setPassword(PasswordUtil.hash(request.getPassword()));
    }
    // toUpdateEntity already merged the request: read the stored value
    var mfaChanged = request.getMfa() != null
        && Boolean.TRUE.equals(getByUidUnsecure(urn).getMfa()) != Boolean.TRUE.equals(request.getMfa());
    resourceRepository.update(userEntity);
    if (mfaChanged) {
      requireGrantRevocation().revokeAll(userEntity, OidcGrantRevocation.REASON_MFA_CHANGED, false);
    } else if (passwordChanged) {
      var self = isCurrentUser(userEntity);
      requireGrantRevocation().revokeAll(userEntity,
          self ? OidcGrantRevocation.REASON_PASSWORD_CHANGED : OidcGrantRevocation.REASON_PASSWORD_SET_BY_ADMIN,
          self);
    }
    return UpsertResponse.<E>builder()
        .status(UpsertResponse.Status.Success)
        .resource(userEntity)
        .build();
  }

  /**
   * MFA reset by an administrator (the user lost his authenticator). A new TOTP
   * secret replaces the old one, so the codes of the old authenticator stop
   * working, and the user enrols the new secret (QR code on the hosted MFA page)
   * at his next sign-in when MFA applies to him. Every delegated access and every
   * Console session of the user end. Needs the update permission on the user.
   */
  @Transactional
  public void resetMfa(String uid) {
    var user = getByUid(uid);
    if (user == null) {
      throw new NotFoundException("Cannot find the user " + uid);
    }
    if (!permissionService.hasPermission(user, "update")) {
      throw new ForbiddenException("The MFA reset of the user " + user.getLabel() + " is forbidden for user "
          + securityContext.getUsername());
    }
    var before = getByUidUnsecure(uid);
    user.setMfaActive(false);
    user.setTotpSecret(Base32.random());
    resourceRepository.update(user);
    requireGrantRevocation().revokeAll(user, OidcGrantRevocation.REASON_MFA_RESET, false);
    audit(user, before, Action.Update, true, false);
  }

  // ------------------------------------------------------------ lifecycle

  /**
   * True when the identity is a disabled user ({@code disabled = true}): it gets
   * no token by any path (hosted login, password login, code exchange, refresh,
   * Console session renewal). Null, an application or any other resource: false.
   */
  public static boolean isDisabled(Object identity) {
    return identity instanceof User user && Boolean.TRUE.equals(user.getDisabled());
  }

  /**
   * Disables a user (offboarding), with the update permission on the user. See
   * {@link #disableUnsecure(User)}.
   *
   * @return the disabled user
   * @throws NotFoundException  when the user does not exist
   * @throws ForbiddenException without the update permission on the user
   */
  @Transactional
  public E disable(String uid) {
    var user = getByUid(uid);
    if (user == null) {
      throw new NotFoundException("Cannot find the user " + uid);
    }
    if (!permissionService.hasPermission(user, "update")) {
      throw new ForbiddenException("Disabling the user " + user.getLabel() + " is forbidden for user "
          + securityContext.getUsername());
    }
    var before = getByUidUnsecure(uid);
    disableUnsecure(user);
    audit(user, before, Action.Update, true, false);
    return user;
  }

  /**
   * Disables a user without permission check (the caller checked its own
   * rights): stores {@code disabled = true} and, in the same transaction, ends
   * every access of the user ({@link OidcGrantRevocation#revokeAll}: OIDC refresh
   * token families, SSO sessions, consents, Console sessions) and writes a
   * {@code user} revocation in the revocation list, so that every access token
   * already issued (Console, mobile application, connectors) is refused within
   * about 30 seconds. If a revocation fails, the flag is rolled back with it.
   * Disabling a user already disabled revokes again (idempotent).
   *
   * <p>
   * This is the only way to set the flag: neither the MTAC nor the application
   * upsert request has a {@code disabled} field, and the mapper of an update
   * copies the fields of the request only, so an update never changes it.
   */
  @Transactional
  public void disableUnsecure(E user) {
    if (user == null || user.getId() == null) {
      throw new IllegalArgumentException("A persisted user is required");
    }
    user.setDisabled(true);
    resourceRepository.update(user);
    requireGrantRevocation().revokeAll(user, OidcGrantRevocation.REASON_USER_DISABLED, false);
    if (tokenRevocations != null) {
      tokenRevocations.revokeUser(user.getUid(), OidcGrantRevocation.REASON_USER_DISABLED);
    } else {
      log.warn("No revocation list: the access tokens of the disabled user {} stay valid until they expire",
          user.getLabel());
    }
    log.info("User {} (id {}) disabled", user.getLabel(), user.getId());
  }

  /**
   * Enables a disabled user again, with the update permission on the user. See
   * {@link #enableUnsecure(User)}.
   *
   * @return the enabled user
   * @throws NotFoundException  when the user does not exist
   * @throws ForbiddenException without the update permission on the user
   */
  @Transactional
  public E enable(String uid) {
    var user = getByUid(uid);
    if (user == null) {
      throw new NotFoundException("Cannot find the user " + uid);
    }
    if (!permissionService.hasPermission(user, "update")) {
      throw new ForbiddenException("Enabling the user " + user.getLabel() + " is forbidden for user "
          + securityContext.getUsername());
    }
    var before = getByUidUnsecure(uid);
    enableUnsecure(user);
    audit(user, before, Action.Update, true, false);
    return user;
  }

  /**
   * Enables a user without permission check: clears the flag. Nothing is
   * restored: the sessions ended by the disabling stay ended, the user signs in
   * again (the revocation list only refuses the tokens issued before the
   * disabling).
   */
  @Transactional
  public void enableUnsecure(E user) {
    if (user == null || user.getId() == null) {
      throw new IllegalArgumentException("A persisted user is required");
    }
    user.setDisabled(false);
    resourceRepository.update(user);
    log.info("User {} (id {}) enabled", user.getLabel(), user.getId());
  }

  /**
   * True when the caller is the user himself, not an administrator impersonating
   * him. The {@code imp} claim of an impersonation token is not copied into the
   * principal attributes (userinfo claims only), so it is read from the bearer
   * token, already verified by the resource server. When in doubt: false.
   */
  protected boolean isCurrentUser(User user) {
    if (securityContext == null || user.getUid() == null || !user.getUid().equals(securityContext.getSubject())) {
      return false;
    }
    if (securityContext.getAttributes().containsKey("imp")) {
      return false;
    }
    var token = securityContext.getJwtToken();
    if (token == null) {
      return false;
    }
    try {
      return SignedJWT.parse(token).getJWTClaimsSet().getClaim("imp") == null;
    } catch (ParseException e) {
      return false;
    }
  }

  protected OidcGrantRevocation requireGrantRevocation() {
    if (grantRevocation == null) {
      throw new IllegalStateException("OidcGrantRevocation is not configured: credentials cannot change");
    }
    return grantRevocation;
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
