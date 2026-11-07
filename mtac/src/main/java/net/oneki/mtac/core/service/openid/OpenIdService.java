package net.oneki.mtac.core.service.openid;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.TokenResponse;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.security.Claims;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.resource.iam.identity.Identity;
import net.oneki.mtac.model.resource.iam.identity.application.Application;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.DefaultResourceService;
import net.oneki.mtac.resource.iam.identity.application.DefaultApplicationService;
import net.oneki.mtac.resource.iam.identity.user.DefaultUserService;
import net.oneki.mtac.resource.iam.identity.user.UserService;

@Service
@RequiredArgsConstructor
public class OpenIdService {
  public enum OperationType {
    Login,
    LoginUser,
    LoginApplication,
    RefreshToken
  }

  // hash of a random password: lets authenticateUser spend the same time on an unknown user
  private static volatile String dummyPasswordHash;
  // claims of the application userinfo that the profile and email scopes already cover
  private static final Set<String> PROFILE_CLAIMS = Set.of("sub", "username", "email", "firstName", "lastName");

  protected DefaultUserService userService;
  protected DefaultApplicationService applicationService;
  protected JwtTokenService tokenService;
  protected MtacProperties mtacProperties;
  protected MfaService mfaService;
  protected TokenRegistry tokenRegistry;
  protected TokenRepository tokenRepository;
  protected SecurityContext securityContext;
  protected DefaultResourceService resourceService;

  public TokenResponse login(String username, String password, boolean consoleAccess, boolean refreshToken) {
    boolean status = false;
    Identity identity = null;
    try {
      if (password == null || password.equals("")) {
        throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
      }
      identity = getUserService().getByUniqueLabelUnsecureOrReturnNull(username, Identity.class);
      if (identity == null) {
        throw new BusinessException("INVALID_CREDENTIALS", "incorrect credentials");
      }
    } finally {
      if (identity == null) {
        audit(Identity.builder().label(username).build(), OperationType.Login, status);
      }
    }

    switch (identity) {
      case User user -> {
        return loginUser(user, password, refreshToken);
      }
      case Application application -> {
        return loginApplication(application, password);
      }
      default -> throw new BusinessException("INVALID_CREDENTIALS", "Username or password incorrect");
    }
  }

  public TokenResponse loginApplication(Application application, String password) {
    boolean status = false;
    try {
      if (!PasswordUtil.matches(password, application.getPassword())) {
        throw new BusinessException("INVALID_CREDENTIALS", "client ID or client Secret incorrect");
      }
      status = true;
      var expirationSec = mtacProperties.getJwt().getExpirationSec();
      if (application.getTokenExpiresIn() != null) {
        expirationSec = application.getTokenExpiresIn();
      }

      // store userinfo in cache
      tokenService.generateIdToken(applicationService.userinfo(application.getUid(), true));
      var tokenResponse = TokenResponse.builder()
          .accessToken(
              tokenService.generateAccessToken(application.getUid(), application.getLabel(), expirationSec, null))
          .expiresIn(expirationSec)
          .build();
      return tokenResponse;
    } finally {
      audit(application, OperationType.LoginApplication, status);
    }

  }

  public TokenResponse loginUser(User user, String password, boolean refreshToken) {
    boolean status = false;
    try {
      if (!PasswordUtil.matches(password, user.getPassword())) {
        throw new BusinessException("INVALID_CREDENTIALS", "Username or password incorrect");
      }
      var expirationSec = mtacProperties.getJwt().getExpirationSec();
      if (user.getTokenExpiresIn() != null) {
        expirationSec = user.getTokenExpiresIn();
      }
      status = true;
      // Check if 2FA is required.
      // If so, return a response with a token of type MFA
      var isMfaRequired = user.getMfa();
      if (isMfaRequired != null && isMfaRequired == true) {
        return mfaService.generateMfaTokenResponse(user);
      }

      // If not, generate a token response with access, id and refresh tokens
      var randomString = UUID.randomUUID().toString();
      var tokenResponse = TokenResponse.builder()
          .accessToken(tokenService.generateAccessToken(user.getUid(), user.getLabel(), expirationSec, null))
          .idToken(tokenService.generateIdToken(getUserService().userinfo(user.getUid(), true)))
          .expiresIn(expirationSec)
          .build();
      if (refreshToken == true) {
        tokenResponse.setRefreshToken(tokenService.generateRefreshToken(user.getUid(), randomString));
        user.setRefreshToken(randomString);
        resourceService.updateUnsecure(user);
      }

      return tokenResponse;
    } finally {
      audit(user, OperationType.LoginUser, status);
    }
  }

  /**
   * Checks the credentials of a user without issuing any token. Used by the hosted
   * login page of the OpenID provider, which opens an SSO session instead.
   *
   * @throws BusinessException INVALID_CREDENTIALS, whatever the reason
   */
  public User authenticateUser(String username, String password) {
    boolean status = false;
    Identity identity = null;
    try {
      if (username == null || password == null || password.equals("")) {
        throw new BusinessException("INVALID_CREDENTIALS", "Username or password incorrect");
      }
      identity = getUserService().getByUniqueLabelUnsecureOrReturnNull(username, Identity.class);
      if (!(identity instanceof User user)) {
        // same cost as a real check, so that the response time does not tell
        // whether the user exists
        if (dummyPasswordHash == null) {
          dummyPasswordHash = PasswordUtil.hash(PasswordUtil.randomPassword());
        }
        PasswordUtil.matches(password, dummyPasswordHash);
        throw new BusinessException("INVALID_CREDENTIALS", "Username or password incorrect");
      }
      if (!PasswordUtil.matches(password, user.getPassword())) {
        throw new BusinessException("INVALID_CREDENTIALS", "Username or password incorrect");
      }
      status = true;
      return user;
    } finally {
      audit(identity != null ? identity : Identity.builder().label(username).build(), OperationType.LoginUser, status);
    }
  }

  /**
   * Claims released to an OpenID client about a user, according to the granted
   * scopes: {@code profile}, {@code email}, and the scope configured under
   * {@code mtac.openid.server.tenants-scope} for the tenant / role claims.
   */
  public Claims oidcClaims(String sub, Set<String> scopes) {
    var user = getUserService().getByUidUnsecure(sub);
    if (user == null) {
      throw new BusinessException("INVALID_CREDENTIALS", "User not found");
    }
    var claims = new Claims();
    claims.put("sub", user.getUid());
    if (scopes.contains("profile")) {
      var name = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
          + (user.getLastName() != null ? user.getLastName() : "")).trim();
      putIfPresent(claims, "name", name.isEmpty() ? null : name);
      putIfPresent(claims, "given_name", user.getFirstName());
      putIfPresent(claims, "family_name", user.getLastName());
      putIfPresent(claims, "preferred_username", user.getLabel());
    }
    if (scopes.contains("email")) {
      putIfPresent(claims, "email", user.getEmail());
    }
    if (scopes.contains(mtacProperties.getOpenId().getServer().getTenantsScope())) {
      var applicationClaims = getUserService().buildClaims(sub, false);
      applicationClaims.forEach((key, value) -> {
        if (value != null && !PROFILE_CLAIMS.contains(key)) {
          claims.put(key, value);
        }
      });
    }
    return claims;
  }

  private static void putIfPresent(Claims claims, String key, Object value) {
    if (value != null) {
      claims.put(key, value);
    }
  }

  public Claims userinfo(String sub, boolean forceRefresh) {
    var userOrApplication = resourceService.getByUidUnsecure(sub);
    if (userOrApplication == null) {
      throw new BusinessException("INVALID_CREDENTIALS", "User or application not found");
    }
    switch (userOrApplication) {
      case User user -> {
        return getUserService().userinfo(user, false, forceRefresh, true);
      }
      case Application application -> {
        return applicationService.userinfo(application, forceRefresh);
      }
      default -> throw new BusinessException("INVALID_CREDENTIALS", "User or application not found");
    }
  }

  public TokenResponse refreshToken(String refreshToken) {
    // decrypt the refresh token
    refreshToken = PasswordUtil.decrypt(refreshToken);
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

    var user = getUserService().getByUidUnsecure(sub);
    if (user == null) {
      throw new BadCredentialsException("User not found for the provided refresh token");
    }

    if (!user.getRefreshToken().equals(randomUUID)) {
      throw new BadCredentialsException("Invalid refresh token");
    }

    var expirationSec = mtacProperties.getJwt().getExpirationSec();
    if (user.getTokenExpiresIn() != null) {
      expirationSec = user.getTokenExpiresIn();
    }
    return TokenResponse.builder()
        .accessToken(tokenService.generateAccessToken(sub, user.getLabel(), expirationSec, null))
        .expiresIn(expirationSec)
        .build();
  }

  public void logout() {
    tokenRegistry.remove(securityContext.getSubject());
    tokenRepository.deleteToken(securityContext.getSubject());
  }

  @Autowired
  public void setApplicationService(DefaultApplicationService applicationService) {
    this.applicationService = applicationService;
  }

  @Autowired
  public void setTokenService(JwtTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Autowired
  public void setMtacProperties(MtacProperties mtacProperties) {
    this.mtacProperties = mtacProperties;
  }

  @Autowired
  public void setMfaService(MfaService mfaService) {
    this.mfaService = mfaService;
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
  public void setSecurityContext(SecurityContext securityContext) {
    this.securityContext = securityContext;
  }

  @Autowired
  public void setResourceService(DefaultResourceService resourceService) {
    this.resourceService = resourceService;
  }

  @Autowired
  public void setUserService(DefaultUserService userService) {
    this.userService = userService;
  }

  public UserService<?, ?> getUserService() {
    return userService;
  }

  protected void audit(Identity resourceEntity, OperationType operation, boolean status) {
  }

}
