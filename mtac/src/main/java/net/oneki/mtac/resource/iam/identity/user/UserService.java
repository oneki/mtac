package net.oneki.mtac.resource.iam.identity.user;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.jboss.aerogear.security.otp.Totp;
import org.jboss.aerogear.security.otp.api.Base32;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.security.RoleUserInfo;
import net.oneki.mtac.model.core.security.TenantRole;
import net.oneki.mtac.model.core.security.TenantUserInfo;
import net.oneki.mtac.model.core.util.StringUtils;
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

@Service("mtacUserService")
@RequiredArgsConstructor
public abstract class UserService<U extends BaseUserUpsertRequest<? extends Group>, E extends User>
    extends ResourceService<U, E> {
  // private final UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  protected GroupMembershipRepository groupMembershipRepository;
  protected GroupRepository groupRepository;
  // private final TenantRepository tenantRepository;
  protected JwtTokenService tokenService;
  protected ResourceRepository resourceRepository;
  protected RoleRepository roleRepository;
  protected SecurityContext securityContext;
  protected TokenRegistry tokenRegistry;
  protected TokenRepository tokenRepository;
  protected PasswordUtil passwordUtil;

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

  @Autowired
  public final void setPasswordUtil(PasswordUtil passwordUtil) {
    this.passwordUtil = passwordUtil;
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

    var isMfaRequired = user.getMfa();
    var totpSecret = user.getTotpSecret();

    if (isMfaRequired != null && isMfaRequired == true) {

      if (totpSecret == null) {
        user.setTotpSecret(Base32.random());
        updateUnsecure(user);
      }

      return tokenService.generateMfaToken(user.getUid(), user.getLabel(), user.getMfaActive() ? null : totpSecret,
          null);
    }

    var accessTokenClaims = new Claims();
    accessTokenClaims.put("jti", UUID.randomUUID().toString());
    var sub = Resource.toUid(user.getId());
    accessTokenClaims.put("sub", sub);
    accessTokenClaims.put("username", user.getLabel());

    var idTokenClaims = userinfo(sub, true);

    var result = tokenService.generateExpiringToken(accessTokenClaims, idTokenClaims);

    // generate refresh token
    // the refresh token is simply the string "<sub>:<expirationTimestampInSeconds>:<randomUUID>"
    // the <randomUUID> is stored in the user profile in DB to be able to revoke it
    var randomUUID = UUID.randomUUID().toString();
    var refreshToken =  sub + ":" + Instant.now().plusSeconds(refreshTokenExpirationSec).getEpochSecond() + ":" + randomUUID;

    // encrypt the refresh token
    refreshToken = passwordUtil.encrypt(refreshToken);

    result.put("refresh_token", refreshToken);
    result.put("refresh_token_expires_in", refreshTokenExpirationSec);

    user.setRefreshToken(randomUUID);
    updateUnsecure(user);

    return result;
  }

  public Map<String, Object> refreshToken(String refreshToken) {
    // decrypt the refresh token
    refreshToken = passwordUtil.decrypt(refreshToken);
    var parts = refreshToken.split(":");
    if (parts.length != 3) {
      throw new BadCredentialsException("Invalid refresh token format");
    }
    var sub = parts[0];
    var expirationTimestamp = Long.parseLong(parts[1]);
    var randomUUID = parts[2];

    if (expirationTimestamp < System.currentTimeMillis() / 1000) {
      throw new BadCredentialsException("Refresh token has expired");
    }

    var user = getByUidUnsecure(sub);
    if (user == null) {
      throw new BadCredentialsException("User not found for the provided refresh token");
    }

    if (!user.getRefreshToken().equals(randomUUID)) {
      throw new BadCredentialsException("Invalid refresh token");
    }

    var accessTokenClaims = new Claims();
    accessTokenClaims.put("jti", UUID.randomUUID().toString());
    accessTokenClaims.put("sub", sub);
    accessTokenClaims.put("username", user.getLabel());
    var result = new HashMap<String, Object>();
    result.put("access_token", tokenService.newToken(accessTokenClaims, accessTokenExpirationSec));
    result.put("token_type", "Bearer");
    result.put("expires_in", accessTokenExpirationSec);
    return result;
  }

  public Map<String, Object> verifyTotp(String verificationCode, String mfaToken, Boolean trusted, String noMfaToken) {
    var claims = tokenService.verify(mfaToken);
    var audience = claims.getAudience();
    if (audience == null || !audience.contains("mfa")) {
      throw new BadCredentialsException(
          "Invalid audience. The provided authorization token is not a valid MFA authorization token");
    }
    var sub = claims.getSubject();
    var user = getByUidUnsecure(sub);

    if (verificationCode == null) {
      // The user is using a trusted device, so we check the noMfaToken and not the
      // verification code
      if (noMfaToken == null) {
        throw new BadCredentialsException("Invalid verification code");
      }
      // check if the noMfaToken is valid (this token is used to not re-ask the
      // verification code up to x days)
      var noMfaClaims = tokenService.verify(noMfaToken);
      if (!noMfaClaims.getSubject().equals(claims.getSubject())) {
        throw new BadCredentialsException("The subject of the no_mfa_token is invalid");
      }

    } else if (verificationCode.length() == 8) {
      // it's a code sent by mail / sms

      String dbVerificationCode = user.getVerificationCode();
      if (dbVerificationCode == null) {
        throw new BadCredentialsException("The mail verification code has been revoked");
      }

      var dbVerificationCodeValue = dbVerificationCode.split(":")[0];
      var dbVerificationCodeExpiresAt = Long.valueOf(dbVerificationCode.split(":")[1]);

      if (dbVerificationCodeValue.equals(verificationCode)) {
        if (dbVerificationCodeExpiresAt < System.currentTimeMillis()) {
          throw new BadCredentialsException("This verification code has been expired");
        }
      } else {
        throw new BadCredentialsException("Invalid verification code");
      }
      user.setVerificationCode(null);
      update(user);
    } else {
      // we received a TOTP code
      var totpSecret = user.getTotpSecret();
      if (totpSecret == null) {
        throw new RuntimeException("There is no TOTP secret defined for the user " + sub);
      }

      Totp totp = new Totp(totpSecret);

      if (!StringUtils.isStringLong(verificationCode) || !totp.verify(verificationCode)) {
        throw new BadCredentialsException("Invalid verification code");
      }

      if (tokenService.isTotpRevoked(user.getId(), verificationCode)) {
        throw new BadCredentialsException("The verification code has already been used and is revoked");
      }

      // revoke totp
      tokenService.revokeTotp(user.getId(), verificationCode);

      if (!user.getMfaActive()) {
        user.setMfaActive(true);
        update(user);
      }
    }

    var accessTokenClaims = new Claims();
    accessTokenClaims.put("jti", UUID.randomUUID().toString());
    accessTokenClaims.put("sub", sub);
    accessTokenClaims.put("username", user.getLabel());
    var idTokenClaims = userinfo(sub, true);
    var result = tokenService.generateExpiringToken(accessTokenClaims, idTokenClaims);

    if (trusted != null && trusted) {
      var noMfaExpirationSec = 86400 * 30; // 30 days
      var noMfaClaims = new HashMap<String, Object>();
      noMfaClaims.put("sub", sub);
      noMfaClaims.put("aud", "mfa");
      noMfaToken = tokenService.newToken(noMfaClaims, noMfaExpirationSec);
      result.put("no_mfa_token", noMfaToken);
      result.put("no_mfa_token_expires_in", noMfaExpirationSec);
    }

    return result;
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
    tokenRepository.upsertToken(user.getId(), user.getUid(), claims,
        Instant.now().plusSeconds(accessTokenExpirationSec));

    return claims;
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
    } else {
      userEntity.setPassword(passwordUtil.hash(request.getPassword()));
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
      userEntity.setPassword(passwordUtil.hash(request.getPassword()));
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
