package net.oneki.mtac.api;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.WebUtils;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.service.openid.BrowserSessionService;
import net.oneki.mtac.core.service.openid.ImpersonateService;
import net.oneki.mtac.core.service.openid.MfaService;
import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.core.service.openid.PrivateKeyJwtVerifier;
import net.oneki.mtac.core.service.openid.ResetPasswordService;
import net.oneki.mtac.core.service.openid.oidc.OidcAuthorizationService;
import net.oneki.mtac.core.service.security.PermissionService;
import net.oneki.mtac.core.util.security.BrowserSessionCookies;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.MtacIam.TokenLocation;
import net.oneki.mtac.model.core.openid.ImpersonateUserRequest;
import net.oneki.mtac.model.core.openid.JwksResponse;
import net.oneki.mtac.model.core.openid.LoginRequest;
import net.oneki.mtac.model.core.openid.ResetPasswordRequest;
import net.oneki.mtac.model.core.openid.TokenResponse;
import net.oneki.mtac.model.core.openid.TriggerResetPasswordRequest;
import net.oneki.mtac.model.core.openid.oidc.OidcException;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;
import net.oneki.mtac.model.core.util.StringUtils;
import net.oneki.mtac.resource.iam.identity.user.DefaultUserService;
import net.oneki.mtac.resource.iam.identity.user.UserService;

@RequiredArgsConstructor
public abstract class OpenIdController {

  protected JwtTokenService tokenService;
  protected PermissionService permissionService;
  protected OpenIdService openIdService;
  protected ResetPasswordService resetPasswordService;
  protected MfaService mfaService;
  protected DefaultUserService userService;
  protected RequestMappingHandlerMapping handlerMapping;
  protected MtacProperties mtacProperties;
  protected ImpersonateService impersonateService;
  protected OidcEndpoints oidcEndpoints;
  protected BrowserSessionEndpoints browserSessionEndpoints;
  protected BrowserSessionService browserSessionService;

  @PostConstruct
  public void init() throws NoSuchMethodException {

    // Login (JSON)
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/oauth2/token")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_JSON_VALUE)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("auth", LoginRequest.class));

    // login (url-encoded - OIDC standard; also supports Basic Auth header for client_id/client_secret)
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/oauth2/token")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("auth", String.class, String.class,
            String.class, String.class, String.class, String.class, String.class));

    // logout
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/logout")
            .methods(RequestMethod.POST)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("logout"));

    // TOTP verification
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/totp/verify")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_JSON_VALUE)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("verifyTotp", Map.class, String.class));

    // JWKS endpoint
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/.well-known/jwks.json")
            .methods(RequestMethod.GET)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("getJwks"));

    // Userinfo endpoint
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/oauth2/userinfo")
            .methods(RequestMethod.GET)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("userinfo", Boolean.class));

    // Trigger reset password
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/login/trigger-reset-password")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("triggerResetpassword", TriggerResetPasswordRequest.class));

    // Verify reset password token
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/login/verify-reset-token")
            .methods(RequestMethod.GET)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("verifyResetPassword", String.class));

    // Reset password
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/login/reset-password")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_JSON_VALUE)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("resetPassword", ResetPasswordRequest.class));

    // Impersonate user
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/login/impersonate")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_JSON_VALUE)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("impersonate", ImpersonateUserRequest.class,
            HttpServletRequest.class));

    // Exit impersonation
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/login/exit-impersonation")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_JSON_VALUE)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("exitImpersonation", HttpServletRequest.class));

    // OpenID Connect provider (authorization code flow), only when a client is declared
    if (oidcEndpoints.isEnabled()) {
      oidcEndpoints.use(getOpenIdService(), getMfaService());
      oidcEndpoints.useResetPasswordService(getResetPasswordService()); // the application's (sends the email)
      oidcEndpoints.register(handlerMapping, getApiBasePath());
    }

    // Console sign-in through the OpenID provider, the API being the client (backend for frontend)
    if (browserSessionEndpoints != null && browserSessionEndpoints.isEnabled()) {
      if (oidcEndpoints.isEnabled()) {
        browserSessionEndpoints.use(getOpenIdService());
        browserSessionEndpoints.register(handlerMapping, getApiBasePath());
      } else {
        org.slf4j.LoggerFactory.getLogger(OpenIdController.class).error("mtac.iam.browser-session.enabled is true "
            + "but the OpenID provider has no client: the Console sign-in endpoints are not registered");
      }
    }
  }

  public ResponseEntity<?> auth(@RequestBody LoginRequest request) throws Exception {
    TokenResponse result;
    try {
      // client_credentials: reserved to applications, token for the public API
      result = getOpenIdService().login(request.getClient_id(), request.getClient_secret(), TokenAudience.API, false,
          false);
    } catch (OidcException e) {
      return tokenEndpointError(e);
    }
    // var tokenLocation = mtacProperties.getIam().getTokenLocation();
    // var httpHeaders = new HttpHeaders();
    // if ("cookie".equals(tokenLocation)) {
    //   var cookieName = mtacProperties.getIam().getAccessTokenCookieName();
    //   httpHeaders.add("Set-Cookie",
    //       cookieName + "=" + result.getAccessToken() + "; Path=/; SameSite=Strict; httpOnly");
    // }
    return ResponseEntity.ok().body(result);
  }

  public ResponseEntity<?> auth(
      @RequestParam(value = "client_id", required = false) String client_id,
      @RequestParam(value = "client_secret", required = false) String client_secret,
      @RequestParam(value = "grant_type", required = false) String grant_type,
      @RequestParam(value = "refresh_token", required = false) String refresh_token,
      @RequestParam(value = "client_assertion_type", required = false) String client_assertion_type,
      @RequestParam(value = "client_assertion", required = false) String client_assertion,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader)
      throws Exception {
    // private_key_jwt: the client proves its identity with a signed JWT, never together with a secret
    if (PrivateKeyJwtVerifier.isAssertionRequest(client_assertion_type, client_assertion)) {
      if (client_secret != null || authorizationHeader != null || !"client_credentials".equals(grant_type)) {
        return tokenEndpointError(OidcException.invalidClient(
            "client_assertion is only accepted alone, with the client_credentials grant"));
      }
      try {
        return ResponseEntity.ok().body(getOpenIdService().loginApplicationWithAssertion(client_assertion_type,
            client_assertion, client_id, List.of(mtacProperties.getJwt().getIssuer() + OidcAuthorizationService.PATH_TOKEN),
            TokenAudience.API));
      } catch (OidcException e) {
        return tokenEndpointError(e);
      }
    }

    // If Basic Auth header is present, extract client_id/client_secret from it
    if (authorizationHeader != null && authorizationHeader.startsWith("Basic ")) {
      String decoded = new String(Base64.getDecoder().decode(authorizationHeader.substring(6)));
      int separator = decoded.indexOf(':');
      if (separator != -1) {
        client_id = decoded.substring(0, separator);
        client_secret = decoded.substring(separator + 1);
      }
    }

    // refresh tokens issued to an OpenID client carry a prefix the other ones cannot have
    if ("refresh_token".equals(grant_type) && refresh_token != null
        && refresh_token.startsWith(OidcAuthorizationService.REFRESH_TOKEN_PREFIX) && oidcEndpoints.isEnabled()) {
      return oidcEndpoints.refresh(client_id, client_secret, refresh_token);
    }

    TokenResponse result = null;
    try {
      if ("refresh_token".equals(grant_type)) {
        // refresh token of a Console session (the Console itself refreshes on /login/refresh)
        result = getOpenIdService().refreshToken(refresh_token, TokenAudience.API);
      } else if ("client_credentials".equals(grant_type)) {
        // reserved to applications, token for the public API
        result = getOpenIdService().login(client_id, client_secret, TokenAudience.API, false, false);
      } else {
        throw new IllegalArgumentException("Unsupported grant_type: " + grant_type);
      }
    } catch (OidcException e) {
      return tokenEndpointError(e);
    }
    // var tokenLocation = mtacProperties.getIam().getTokenLocation();
    // var httpHeaders = new HttpHeaders();
    // if ("cookie".equals(tokenLocation)) {
    //   var cookieName = mtacProperties.getIam().getAccessTokenCookieName();
    //   httpHeaders.add("Set-Cookie",
    //       cookieName + "=" + result.getAccessToken() + "; Path=/; SameSite=Strict; httpOnly");
    // }

    return ResponseEntity.ok().body(result);
  }

  /** OAuth error response of the token endpoint (RFC 6749 section 5.2). */
  protected ResponseEntity<?> tokenEndpointError(OidcException e) {
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.add(HttpHeaders.PRAGMA, "no-cache");
    if ("invalid_client".equals(e.getError())) {
      headers.add(HttpHeaders.WWW_AUTHENTICATE, "Basic");
    }
    var body = new LinkedHashMap<String, String>();
    body.put("error", e.getError());
    body.put("error_description", e.getMessage());
    return ResponseEntity.status(e.getStatus()).headers(headers).body(body);
  }

  public Map<String, Object> userinfo(@RequestParam(name="forceRefresh", required = false) Boolean forceRefresh) throws Exception {
    return getUserService().userinfo(forceRefresh != null ? forceRefresh : false);
  }

  public ResponseEntity<Void> logout() throws Exception {
    getOpenIdService().logout();
    var tokenLocation = mtacProperties.getIam().getTokenLocation();
    var httpHeaders = new HttpHeaders();
    if (tokenLocation.contains(TokenLocation.cookie)) {
      cleanCookies(httpHeaders);
    }
    return ResponseEntity.ok().headers(httpHeaders).build();
  }

  public JwksResponse getJwks() {
    return JwksResponse.builder().key(tokenService.getJwkAsJsonObject()).build();
  }

  public ResponseEntity<?> verifyTotp(@RequestBody Map<String, String> body,
      @RequestHeader(value = "authorization", required = true) String authorizationHeader)
      throws Exception {
    if (!isPasswordLoginEnabled()) {
      return ResponseEntity.notFound().build();
    }
    String token = authorizationHeader.trim().substring(7);
    // second step of the Console login
    var result = getMfaService().verifyTotp(body.getOrDefault("totp", null), token,
        StringUtils.toBoolean(body.getOrDefault("trusted_device", null)),
        body.getOrDefault("no_mfa_token", null), TokenAudience.CONSOLE);
    var tokenLocation = mtacProperties.getIam().getTokenLocation();
    var httpHeaders = new HttpHeaders();
    if (tokenLocation.contains(TokenLocation.cookie) && result.getAccessToken() != null) {
      httpHeaders.add(HttpHeaders.SET_COOKIE, cookies().accessToken(result.getAccessToken(), result.getExpiresIn()));
    }

    return ResponseEntity.ok().headers(httpHeaders).body(result);
  }

  // Former Console forgot / reset password pages: the hosted pages (/oauth2/forgot-password,
  // /oauth2/reset-password) replace them, with the same answer for every address and the per-IP lock
  public ResponseEntity<?> triggerResetpassword(@RequestBody TriggerResetPasswordRequest request) {
    if (!isPasswordLoginEnabled()) {
      return ResponseEntity.notFound().build();
    }
    getResetPasswordService().triggerResetPassword(request.getEmail());
    return ResponseEntity.ok().build();
  }

  public ResponseEntity<?> verifyResetPassword(@RequestParam("resetToken") String resetToken) {
    if (!isPasswordLoginEnabled()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(getResetPasswordService().verifyResetToken(resetToken));
  }

  public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
    if (!isPasswordLoginEnabled()) {
      return ResponseEntity.notFound().build();
    }
    getResetPasswordService().resetPassword(request);
    return ResponseEntity.ok().build();
  }

  /**
   * Starts impersonating a user. With a Console browser session (the session
   * cookie of the session of the access token), the impersonation is kept by
   * that session: {@code fuzz.token} becomes the impersonation token, renewed as
   * such by the cookie filter until the impersonation ends, and the API sets
   * the indicator cookie read by the Console. Without one (former password
   * login), a one-hour impersonation token, the original token kept in the
   * impersonation token cookie.
   */
  public ResponseEntity<?> impersonate(@RequestBody ImpersonateUserRequest request, HttpServletRequest httpRequest) {
    var impersonation = getImpersonateService();
    var session = browserSessionService != null
        ? browserSessionService.sessionOf(httpRequest, impersonation.consoleSessionId())
        : null;
    var httpHeaders = new HttpHeaders();
    if (session != null) {
      // the permission and nested impersonation checks, then the session keeps it
      var target = impersonation.checkImpersonation(request);
      var started = browserSessionService.startImpersonation(session, target);
      if (started == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      httpHeaders.add(HttpHeaders.SET_COOKIE, cookies().accessToken(started.accessToken(), started.accessTokenTtlSec()));
      httpHeaders.add(HttpHeaders.SET_COOKIE, cookies().impersonationActive(started.impersonationMaxAgeSec()));
      // a former impersonation of this browser, if any
      httpHeaders.add(HttpHeaders.SET_COOKIE, cookies().clearImpersonationToken());
      // no token in the body: the browser holds none
      return ResponseEntity.ok().headers(httpHeaders).body(Map.of("expiresIn", started.impersonationMaxAgeSec()));
    }
    var result = impersonation.impersonateUser(request);
    httpHeaders.add(HttpHeaders.SET_COOKIE, cookies().accessToken(result.getAccessToken(), null));
    httpHeaders.add(HttpHeaders.SET_COOKIE, cookies().impersonationToken(result.getOriginalAccessToken()));
    // the indicator is set by the API in both cases, for the lifetime of the impersonation token
    httpHeaders.add(HttpHeaders.SET_COOKIE, cookies().impersonationActive(ImpersonateService.IMPERSONATION_SEC));
    return ResponseEntity.ok().headers(httpHeaders).body(result);
  }

  /**
   * Ends the impersonation. With a Console browser session: clears it on the
   * session and gives the token of the session user back. Without one: the
   * original token of the impersonation token cookie.
   */
  public ResponseEntity<?> exitImpersonation(HttpServletRequest request) {
    var session = browserSessionService != null
        ? browserSessionService.sessionOf(request, getImpersonateService().consoleSessionId())
        : null;
    var httpHeaders = new HttpHeaders();
    if (session != null) {
      var restored = browserSessionService.stopImpersonation(session);
      if (restored == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      httpHeaders.add(HttpHeaders.SET_COOKIE,
          cookies().accessToken(restored.accessToken(), restored.accessTokenTtlSec()));
      cookies().clearImpersonation().forEach(cookie -> httpHeaders.add(HttpHeaders.SET_COOKIE, cookie));
      return ResponseEntity.ok().headers(httpHeaders).build();
    }
    var impTokenCookie = WebUtils.getCookie(request, mtacProperties.getIam().getImpersonateTokenCookieName());
    if (impTokenCookie == null) {
      return ResponseEntity.badRequest().body("No impersonation token found");
    }
    var impToken = impTokenCookie.getValue();
    httpHeaders.add(HttpHeaders.SET_COOKIE, cookies().accessToken(impToken, null));
    cookies().clearImpersonation().forEach(cookie -> httpHeaders.add(HttpHeaders.SET_COOKIE, cookie));
    return ResponseEntity.ok().headers(httpHeaders).build();
  }

  // @PostMapping(value = "/v1/totp/mail")
  // public Map<String, Object> sendVerificationCodeByMail(@RequestHeader(value =
  // "authorization", required = true) String authorizationHeader) throws
  // Exception {
  // String token = authorizationHeader.trim().substring(7);
  // getUserService().sendVerificationCodeByMail(token);
  // return Map.of("status", "success");
  // }

  @Autowired
  public void setTokenService(JwtTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Autowired
  public void setPermissionService(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @Autowired
  public void setOpenIdService(OpenIdService openIdService) {
    this.openIdService = openIdService;
  }

  @Autowired
  public void setResetPasswordService(ResetPasswordService resetPasswordService) {
    this.resetPasswordService = resetPasswordService;
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
  public void setHandlerMapping(RequestMappingHandlerMapping handlerMapping) {
    this.handlerMapping = handlerMapping;
  }

  @Autowired
  public void setImpersonateService(ImpersonateService impersonateService) {
    this.impersonateService = impersonateService;
  }

  @Autowired
  public void setOidcEndpoints(OidcEndpoints oidcEndpoints) {
    this.oidcEndpoints = oidcEndpoints;
  }

  @Autowired(required = false)
  public void setBrowserSessionService(BrowserSessionService browserSessionService) {
    this.browserSessionService = browserSessionService;
  }

  @Autowired(required = false)
  public void setBrowserSessionEndpoints(BrowserSessionEndpoints browserSessionEndpoints) {
    this.browserSessionEndpoints = browserSessionEndpoints;
  }

  /**
   * False while the Console signs in through the OpenID provider only
   * ({@code mtac.iam.browser-session.enabled} without
   * {@code password-login-enabled}): the former password login endpoints
   * ({@code /login}, {@code /login/refresh}, {@code /totp/verify}) and the
   * former reset password ones ({@code /login/trigger-reset-password},
   * {@code /login/verify-reset-token}, {@code /login/reset-password}) then answer
   * 404. Always true without browser session.
   */
  protected boolean isPasswordLoginEnabled() {
    var browserSession = mtacProperties.getIam().getBrowserSession();
    return !browserSession.isEnabled() || browserSession.isPasswordLoginEnabled();
  }

  /** Set-Cookie values of the Console cookies (same attributes everywhere). */
  protected BrowserSessionCookies cookies() {
    return new BrowserSessionCookies(mtacProperties);
  }

  protected void cleanCookies(HttpHeaders httpHeaders) {
    var cookies = cookies();
    httpHeaders.add(HttpHeaders.SET_COOKIE, cookies.clearAccessToken());
    cookies.clearImpersonation().forEach(cookie -> httpHeaders.add(HttpHeaders.SET_COOKIE, cookie));
    if (mtacProperties.getIam().getBrowserSession().isEnabled()) {
      // the Console session was closed by the logout (csid of the access token)
      httpHeaders.add(HttpHeaders.SET_COOKIE, cookies.clearSession());
    }
  }

  protected abstract UserService<?, ?> getUserService();

  protected OpenIdService getOpenIdService() {
    return openIdService;
  }

  protected MfaService getMfaService() {
    return mfaService;
  }

  protected ResetPasswordService getResetPasswordService() {
    return resetPasswordService;
  }

  protected String getApiBasePath() {
    return mtacProperties.getApiBasePath();
  }

  protected ImpersonateService getImpersonateService() {
    return impersonateService;
  }
}
