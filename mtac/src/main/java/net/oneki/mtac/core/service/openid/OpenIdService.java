package net.oneki.mtac.core.service.openid;

import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.framework.service.TokenAudiences;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.AudienceEnforcement;
import net.oneki.mtac.model.core.openid.TokenResponse;
import net.oneki.mtac.model.core.openid.oidc.OidcException;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.security.Claims;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.identity.Identity;
import net.oneki.mtac.model.resource.iam.identity.application.Application;
import net.oneki.mtac.model.resource.iam.identity.application.ApplicationAuthMethod;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.DefaultResourceService;
import net.oneki.mtac.resource.iam.identity.application.DefaultApplicationService;
import net.oneki.mtac.resource.iam.identity.user.DefaultUserService;
import net.oneki.mtac.resource.iam.identity.user.UserService;

@Service
@Slf4j
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
  protected PrivateKeyJwtVerifier privateKeyJwtVerifier;
  protected ConsoleSessionService consoleSessions;

  /**
   * Console login ({@code consoleAccess = true}: users and applications, token
   * for the console audience) or token endpoint ({@code consoleAccess = false}:
   * see {@link #login(String, String, String, boolean, boolean)} with the api
   * audience and {@code allowUser = false}).
   */
  public TokenResponse login(String username, String password, boolean consoleAccess, boolean refreshToken) {
    return consoleAccess
        ? login(username, password, TokenAudience.CONSOLE, true, refreshToken)
        : login(username, password, TokenAudience.API, false, refreshToken);
  }

  /**
   * @param audience     key of the audience of the access token (see
   *                     {@link TokenAudience})
   * @param allowUser    false on the token endpoint: the client_credentials
   *                     grant is reserved to applications. A user's credentials
   *                     are then logged ({@code audience-enforcement: log}) or
   *                     refused with invalid_client ({@code strict}). Without
   *                     configured audiences, users are accepted as before.
   * @param refreshToken true to issue a refresh token to a user
   */
  public TokenResponse login(String username, String password, String audience, boolean allowUser,
      boolean refreshToken) {
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
        if (!allowUser) {
          checkUserOnTokenEndpoint(user, password);
        }
        return loginUser(user, password, audience, refreshToken);
      }
      case Application application -> {
        return loginApplication(application, password, audience);
      }
      default -> throw new BusinessException("INVALID_CREDENTIALS", "Username or password incorrect");
    }
  }

  /**
   * A user's email and password sent to the token endpoint. Without configured
   * audiences, nothing changes. In log mode the attempt is logged and goes on;
   * in strict mode it is refused with invalid_client once the password is
   * checked, so that the answer to a wrong password stays the same as before.
   */
  protected void checkUserOnTokenEndpoint(User user, String password) {
    if (!tokenAudiences().isConfigured()) {
      return;
    }
    if (mtacProperties.getJwt().getAudienceEnforcement() != AudienceEnforcement.strict) {
      log.warn("User credentials on the token endpoint (sub={}, username={}, path={}): accepted while "
          + "mtac.jwt.audience-enforcement is log, refused in strict mode", user.getUid(), user.getLabel(),
          currentPath());
      return;
    }
    var passwordMatches = PasswordUtil.matches(password, user.getPassword());
    audit(user, OperationType.LoginUser, false);
    if (!passwordMatches) {
      throw new BusinessException("INVALID_CREDENTIALS", "Username or password incorrect");
    }
    log.warn("User credentials on the token endpoint refused (sub={}, username={}, path={}): the "
        + "client_credentials grant is reserved to applications", user.getUid(), user.getLabel(), currentPath());
    throw OidcException.invalidClient("The client_credentials grant is reserved to applications");
  }

  /**
   * The Console refresh token of a user sent to the token endpoint, for a
   * public API token: same rule as a user's credentials on client_credentials
   * (the public API is for applications). Logged in log mode, refused in strict.
   */
  protected void checkConsoleRefreshOnTokenEndpoint(User user) {
    if (!tokenAudiences().isConfigured()) {
      return;
    }
    if (mtacProperties.getJwt().getAudienceEnforcement() != AudienceEnforcement.strict) {
      log.warn("Console refresh token on the token endpoint (sub={}, username={}, path={}): accepted while "
          + "mtac.jwt.audience-enforcement is log, refused in strict mode", user.getUid(), user.getLabel(),
          currentPath());
      return;
    }
    log.warn("Console refresh token on the token endpoint refused (sub={}, username={}, path={}): the public API "
        + "is reserved to applications", user.getUid(), user.getLabel(), currentPath());
    throw OidcException.invalidGrant("This refresh token cannot be used on the token endpoint");
  }

  public TokenResponse loginApplication(Application application, String password, String audience) {
    boolean status = false;
    try {
      // an application authenticating with a key has no secret: refuse the secret path by intent
      if (ApplicationAuthMethod.of(application) != ApplicationAuthMethod.CLIENT_SECRET
          || !PasswordUtil.matches(password, application.getPassword())) {
        throw new BusinessException("INVALID_CREDENTIALS", "client ID or client Secret incorrect");
      }
      status = true;
      return applicationToken(application, audience);
    } finally {
      audit(application, OperationType.LoginApplication, status);
    }
  }

  /**
   * client_credentials with a signed JWT instead of a secret (private_key_jwt,
   * RFC 7523 section 2.2).
   *
   * @throws OidcException invalid_client for any failure
   */
  public TokenResponse loginApplicationWithAssertion(String clientAssertionType, String clientAssertion,
      String clientId, Collection<String> expectedAudiences, String audience) {
    Application application = null;
    boolean status = false;
    try {
      application = privateKeyJwtVerifier.verify(clientAssertionType, clientAssertion, clientId, expectedAudiences,
          id -> getUserService().getByUniqueLabelUnsecureOrReturnNull(id, Identity.class) instanceof Application app
              ? app
              : null);
      status = true;
      return applicationToken(application, audience);
    } finally {
      audit(application != null ? application : Identity.builder().label(clientId).build(),
          OperationType.LoginApplication, status);
    }
  }

  protected TokenResponse applicationToken(Application application, String audience) {
    var expirationSec = mtacProperties.getJwt().getExpirationSec();
    if (application.getTokenExpiresIn() != null) {
      expirationSec = application.getTokenExpiresIn();
    }
    // store userinfo in cache
    tokenService.generateIdToken(applicationService.userinfo(application.getUid(), true));
    return TokenResponse.builder()
        .accessToken(
            tokenService.generateAccessToken(application.getUid(), application.getLabel(), expirationSec, null,
                audience))
        .expiresIn(expirationSec)
        .build();
  }

  public TokenResponse loginUser(User user, String password, String audience, boolean refreshToken) {
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
      ConsoleSessionService.Issued session = null;
      Map<String, Object> sessionClaims = null;
      if (refreshToken && consoleSessions != null) {
        // one Console session per login: other browsers stay signed in
        session = consoleSessions.open(user.getId());
        sessionClaims = Map.of(ConsoleSessionService.CLAIM, session.sessionId());
      }
      var tokenResponse = TokenResponse.builder()
          .accessToken(tokenService.generateAccessToken(user.getUid(), user.getLabel(), expirationSec, sessionClaims,
              audience))
          .idToken(tokenService.generateIdToken(getUserService().userinfo(user.getUid(), true)))
          .expiresIn(expirationSec)
          .build();
      if (session != null) {
        tokenResponse.setRefreshToken(session.refreshToken());
      } else if (refreshToken) {
        // former single refresh token per user, only without the Console session service (tests)
        var randomString = UUID.randomUUID().toString();
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

  /** Refresh of a Console session: the new access token is for the console audience. */
  public TokenResponse refreshToken(String refreshToken) {
    return refreshToken(refreshToken, TokenAudience.CONSOLE);
  }

  /**
   * @param audience key of the audience of the new access token (see
   *                 {@link TokenAudience})
   */
  public TokenResponse refreshToken(String refreshToken, String audience) {
    if (ConsoleSessionService.isSessionToken(refreshToken)) {
      if (consoleSessions == null) {
        throw new BadCredentialsException("Invalid refresh token");
      }
      return refreshConsoleSession(refreshToken, audience);
    }
    // former format (encrypted sub:exp:random), still accepted until it expires (30 days after the login)
    try {
      refreshToken = PasswordUtil.decrypt(refreshToken);
    } catch (RuntimeException e) {
      throw new BadCredentialsException("Invalid refresh token");
    }
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

    if (!randomUUID.equals(user.getRefreshToken())) {
      throw new BadCredentialsException("Invalid refresh token");
    }
    if (TokenAudience.API.equals(audience)) {
      checkConsoleRefreshOnTokenEndpoint(user);
    }

    var expirationSec = mtacProperties.getJwt().getExpirationSec();
    if (user.getTokenExpiresIn() != null) {
      expirationSec = user.getTokenExpiresIn();
    }
    return TokenResponse.builder()
        .accessToken(tokenService.generateAccessToken(sub, user.getLabel(), expirationSec, null, audience))
        .expiresIn(expirationSec)
        .build();
  }

  /** Refresh with a Console session token: the session slides, the token does not change. */
  protected TokenResponse refreshConsoleSession(String refreshToken, String audience) {
    var session = consoleSessions.find(refreshToken);
    if (session == null) {
      throw new BadCredentialsException("Invalid refresh token");
    }
    var user = getUserService().getByUidUnsecure(Resource.toUid(session.getIdentityId()));
    if (!(user instanceof User)) {
      throw new BadCredentialsException("User not found for the provided refresh token");
    }
    var sessionUser = (User) user;
    if (TokenAudience.API.equals(audience)) {
      checkConsoleRefreshOnTokenEndpoint(sessionUser);
    }
    // every check passed: only now does the session slide
    consoleSessions.slide(session);
    var expirationSec = mtacProperties.getJwt().getExpirationSec();
    if (sessionUser.getTokenExpiresIn() != null) {
      expirationSec = sessionUser.getTokenExpiresIn();
    }
    Map<String, Object> claims = TokenAudience.CONSOLE.equals(audience)
        ? Map.of(ConsoleSessionService.CLAIM, session.getId())
        : null;
    return TokenResponse.builder()
        .accessToken(tokenService.generateAccessToken(sessionUser.getUid(), sessionUser.getLabel(), expirationSec,
            claims, audience))
        .expiresIn(expirationSec)
        .build();
  }

  public void logout() {
    tokenRegistry.remove(securityContext.getSubject());
    tokenRepository.deleteToken(securityContext.getSubject());
    if (consoleSessions != null) {
      // the Console session of this token ends; the other browsers stay signed in
      consoleSessions.close(securityContext.getAttribute(ConsoleSessionService.CLAIM));
    }
  }

  @Autowired
  public void setConsoleSessions(ConsoleSessionService consoleSessions) {
    this.consoleSessions = consoleSessions;
  }

  @Autowired
  public void setPrivateKeyJwtVerifier(PrivateKeyJwtVerifier privateKeyJwtVerifier) {
    this.privateKeyJwtVerifier = privateKeyJwtVerifier;
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

  protected TokenAudiences tokenAudiences() {
    return new TokenAudiences(mtacProperties);
  }

  /** Path of the current HTTP request, for the logs; null outside a request. */
  protected static String currentPath() {
    var attributes = RequestContextHolder.getRequestAttributes();
    return attributes instanceof ServletRequestAttributes servletAttributes
        ? servletAttributes.getRequest().getRequestURI()
        : null;
  }

}
