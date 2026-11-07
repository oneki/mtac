package net.oneki.mtac.core.service.openid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.service.security.PermissionService;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.openid.ImpersonateUserRequest;
import net.oneki.mtac.model.core.openid.ImpersonateUserResponse;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;
import net.oneki.mtac.model.core.util.exception.ForbiddenException;
import net.oneki.mtac.model.core.util.security.Claims;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.iam.identity.user.DefaultUserService;
import net.oneki.mtac.resource.iam.identity.user.UserService;

/**
 * Impersonation of a user by an administrator, in the Console only.
 *
 * <p>With a Console browser session ({@code mtac.iam.browser-session}), the
 * impersonation is kept by the Console session row
 * ({@link BrowserSessionService#startImpersonation}): its renewals mint
 * impersonation tokens until it ends. Without one (former password login), a
 * single impersonation token of {@value #IMPERSONATION_SEC} seconds is issued
 * ({@link #impersonateUser}) and the original token goes to a cookie.
 */
@Service
@RequiredArgsConstructor
public class ImpersonateService {
  /** Lifetime of an impersonation, and of the token of the former one. */
  public static final int IMPERSONATION_SEC = 3600;
  /** Claim of an impersonation token: true. */
  public static final String CLAIM_IMP = "imp";
  /** Claim of an impersonation token: the user name of the administrator. */
  public static final String CLAIM_ON_BEHALF = "onbehalf";

  protected PermissionService permissionService;
  protected SecurityContext securityContext;
  protected DefaultUserService userService;
  protected JwtTokenService tokenService;

  /**
   * Former impersonation: an impersonation token of {@value #IMPERSONATION_SEC}
   * seconds and the original access token.
   */
  public ImpersonateUserResponse impersonateUser(ImpersonateUserRequest request) {
    var user = checkImpersonation(request);
    var sub = Resource.toUid(user.getId());
    // impersonation happens in the Console only: console audience, 1 hour expiration
    var accessToken = getTokenService().generateAccessToken(sub, user.getLabel(), IMPERSONATION_SEC,
        impersonationClaims(onBehalf()), TokenAudience.CONSOLE);
    return ImpersonateUserResponse.builder()
        .accessToken(accessToken)
        .originalAccessToken(securityContext.getJwtToken())
        .build();
  }

  /**
   * The user to impersonate, once the caller is allowed to: the
   * {@code impersonate} permission on that user, and no impersonation in
   * progress (no nested impersonation).
   *
   * @throws ForbiddenException       without the permission, or while impersonating
   * @throws IllegalArgumentException when the user does not exist
   */
  public User checkImpersonation(ImpersonateUserRequest request) {
    if (!getPermissionService().hasPermission(Resource.fromUid(request.getUserUid()), "impersonate")) {
      throw new ForbiddenException("You do not have permission to impersonate this user.");
    }
    if (isImpersonating()) {
      throw new ForbiddenException("You are already impersonating a user.");
    }
    var user = getUserService().getByUid(request.getUserUid());
    if (user == null) {
      throw new IllegalArgumentException("User not found: " + request.getUserUid());
    }
    return user;
  }

  /** True when the token of the request is an impersonation token. */
  public boolean isImpersonating() {
    var attributes = securityContext.getAttributes();
    return attributes != null && attributes.containsKey(CLAIM_IMP);
  }

  /** The user name of the caller, written in the {@value #CLAIM_ON_BEHALF} claim. */
  public String onBehalf() {
    return securityContext.getUsername();
  }

  /** The Console session of the token of the request ({@code csid} claim), null when none. */
  public String consoleSessionId() {
    var attributes = securityContext.getAttributes();
    var value = attributes != null ? attributes.get(ConsoleSessionService.CLAIM) : null;
    return value != null ? value.toString() : null;
  }

  /** The claims that make an access token an impersonation token. */
  public static Claims impersonationClaims(String onBehalf) {
    var claims = new Claims();
    claims.put(CLAIM_IMP, true);
    claims.put(CLAIM_ON_BEHALF, onBehalf);
    return claims;
  }

  public PermissionService getPermissionService() {
    return permissionService;
  }

  public UserService<?, ?> getUserService() {
    return userService;
  }

  public JwtTokenService getTokenService() {
    return tokenService;
  }

  @Autowired
  public void setPermissionService(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @Autowired
  public void setSecurityContext(SecurityContext securityContext) {
    this.securityContext = securityContext;
  }

  @Autowired
  public void setUserService(DefaultUserService userService) {
    this.userService = userService;
  }

  @Autowired
  public void setTokenService(JwtTokenService tokenService) {
    this.tokenService = tokenService;
  }
}
