package net.oneki.mtac.api;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.MfaService;
import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.core.service.openid.oidc.LoginAttemptService;
import net.oneki.mtac.core.service.openid.oidc.OidcAuthorizationService;
import net.oneki.mtac.core.service.openid.oidc.OidcClientRegistry;
import net.oneki.mtac.core.service.openid.oidc.OidcCrypto;
import net.oneki.mtac.core.service.openid.oidc.OidcPageRenderer;
import net.oneki.mtac.core.service.openid.oidc.OidcSessionService;
import net.oneki.mtac.core.service.openid.oidc.TotpQrCode;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;
import net.oneki.mtac.model.core.openid.oidc.OidcAuthorizationRequest;
import net.oneki.mtac.model.core.openid.oidc.OidcException;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.iam.identity.user.User;

/**
 * HTTP endpoints of the OpenID Connect provider, registered by
 * {@link OpenIdController} when at least one client is declared.
 *
 * <p>
 * Two kinds of endpoints live here. The interactive ones (authorize, login,
 * mfa, logout) are visited by the browser of the user and rely on the SSO
 * session cookie. The others (token, userinfo, revoke, discovery) are called by
 * the clients, never read a cookie, and are the only ones that may be called
 * cross-origin (see {@link OidcCorsPreflightFilter} for the preflight requests).
 */
@Component
@Slf4j
public class OidcEndpoints {
  private static final String LOGIN_CONTEXT_AUDIENCE = "oidc-login";
  private static final int LOGIN_CONTEXT_TTL_SEC = 600;
  private static final String CSRF_COOKIE = "mtac_oidc_csrf";
  private static final String MFA_COOKIE = "mtac_oidc_mfa";
  private static final int MFA_COOKIE_TTL_SEC = 300;
  private static final String EXPIRED_REQUEST = "This sign-in request has expired. Go back to the application and try again.";

  private final OidcAuthorizationService authorizationService;
  private final OidcSessionService sessionService;
  private final OidcClientRegistry clientRegistry;
  private final OidcPageRenderer pageRenderer;
  private final LoginAttemptService loginAttemptService;
  private final JwtTokenService tokenService;
  private final MtacProperties mtacProperties;
  private OpenIdService openIdService;
  private MfaService mfaService;

  public OidcEndpoints(OidcAuthorizationService authorizationService, OidcSessionService sessionService,
      OidcClientRegistry clientRegistry, OidcPageRenderer pageRenderer, LoginAttemptService loginAttemptService,
      JwtTokenService tokenService, MtacProperties mtacProperties) {
    this.authorizationService = authorizationService;
    this.sessionService = sessionService;
    this.clientRegistry = clientRegistry;
    this.pageRenderer = pageRenderer;
    this.loginAttemptService = loginAttemptService;
    this.tokenService = tokenService;
    this.mtacProperties = mtacProperties;
  }

  @Autowired
  public void setOpenIdService(OpenIdService openIdService) {
    this.openIdService = openIdService;
  }

  @Autowired
  public void setMfaService(MfaService mfaService) {
    this.mfaService = mfaService;
  }

  public boolean isEnabled() {
    return clientRegistry.isEnabled();
  }

  /**
   * Makes the endpoints use the services of the application (the ones its
   * OpenIdController returns) instead of the MTAC defaults.
   */
  public void use(OpenIdService openIdService, MfaService mfaService) {
    this.openIdService = openIdService;
    this.mfaService = mfaService;
    authorizationService.setOpenIdService(openIdService);
  }

  public void register(RequestMappingHandlerMapping handlerMapping, String basePath) throws NoSuchMethodException {
    var form = MediaType.APPLICATION_FORM_URLENCODED_VALUE;

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_DISCOVERY).methods(RequestMethod.GET).build(),
        this, OidcEndpoints.class.getDeclaredMethod("discovery"));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_AUTHORIZE).methods(RequestMethod.GET).build(),
        this, OidcEndpoints.class.getDeclaredMethod("authorize", MultiValueMap.class, HttpServletRequest.class));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_LOGIN).methods(RequestMethod.GET).build(),
        this, OidcEndpoints.class.getDeclaredMethod("loginPage", String.class, HttpServletRequest.class));
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_LOGIN).methods(RequestMethod.POST)
            .consumes(form).build(),
        this, OidcEndpoints.class.getDeclaredMethod("login", MultiValueMap.class, HttpServletRequest.class));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_MFA).methods(RequestMethod.GET).build(),
        this, OidcEndpoints.class.getDeclaredMethod("mfaPage", String.class, HttpServletRequest.class));
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_MFA).methods(RequestMethod.POST)
            .consumes(form).build(),
        this, OidcEndpoints.class.getDeclaredMethod("mfa", MultiValueMap.class, HttpServletRequest.class));

    // more specific than the mapping of OpenIdController on the same path, which keeps the other grants
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_TOKEN).methods(RequestMethod.POST)
            .consumes(form).params("grant_type=authorization_code").build(),
        this, OidcEndpoints.class.getDeclaredMethod("token", MultiValueMap.class, String.class,
            HttpServletRequest.class));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_USERINFO)
            .methods(RequestMethod.GET, RequestMethod.POST).build(),
        this, OidcEndpoints.class.getDeclaredMethod("userinfo", String.class, HttpServletRequest.class));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_LOGOUT).methods(RequestMethod.GET).build(),
        this, OidcEndpoints.class.getDeclaredMethod("logoutRequest", MultiValueMap.class, HttpServletRequest.class));
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_LOGOUT).methods(RequestMethod.POST)
            .consumes(form).build(),
        this, OidcEndpoints.class.getDeclaredMethod("logout", MultiValueMap.class, HttpServletRequest.class));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_REVOKE).methods(RequestMethod.POST)
            .consumes(form).build(),
        this, OidcEndpoints.class.getDeclaredMethod("revoke", MultiValueMap.class, String.class,
            HttpServletRequest.class));
  }

  // ------------------------------------------------------------ discovery

  public ResponseEntity<Map<String, Object>> discovery() {
    return ResponseEntity.ok(authorizationService.discovery());
  }

  // ------------------------------------------------------------ authorize

  public ResponseEntity<String> authorize(@RequestParam MultiValueMap<String, String> query,
      HttpServletRequest request) {
    var params = query.toSingleValueMap();
    var redirectUri = params.get("redirect_uri");
    var state = params.get("state");

    // 1. client and redirect URI: an error here is shown, never redirected
    MtacOidcClient client;
    try {
      if (hasSeveralValues(query, "client_id") || hasSeveralValues(query, "redirect_uri")) {
        throw OidcException.invalidRequest("Duplicated parameter");
      }
      client = authorizationService.validateClient(params.get("client_id"), redirectUri);
    } catch (OidcException e) {
      return errorPage(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // 2. the rest of the request: an error is returned to the client
    OidcAuthorizationRequest authorizationRequest;
    try {
      for (var name : query.keySet()) {
        if (hasSeveralValues(query, name)) {
          throw OidcException.invalidRequest("Duplicated parameter " + name);
        }
      }
      authorizationRequest = authorizationService.validateAuthorizationRequest(client, params);
    } catch (OidcException e) {
      return redirectError(redirectUri, e.getError(), e.getMessage(), state);
    }

    // 3. a sufficient SSO session answers the request without interaction
    var session = sessionService.resolve(cookieValue(request, sessionCookieName()));
    if (authorizationService.isSessionSufficient(authorizationRequest, client, session)) {
      var response = new LinkedHashMap<String, String>();
      response.put("code", authorizationService.issueCode(authorizationRequest, session));
      response.put("state", state);
      response.put("iss", authorizationService.getIssuer());
      return redirect(OidcAuthorizationService.appendQuery(redirectUri, response), new HttpHeaders());
    }
    if (authorizationRequest.getPrompt().contains(OidcAuthorizationService.PROMPT_NONE)) {
      return redirectError(redirectUri, "login_required", "The user is not signed in", state);
    }

    // 4. otherwise the user signs in, then comes back to this endpoint. prompt and max_age
    // are satisfied by that fresh login and are dropped, or the request would loop.
    var resumeParams = new LinkedHashMap<>(params);
    resumeParams.remove("prompt");
    resumeParams.remove("max_age");
    var context = new HashMap<String, Object>();
    context.put("aud", LOGIN_CONTEXT_AUDIENCE);
    context.put("cid", authorizationRequest.getClientId());
    context.put("q", OidcAuthorizationService.appendQuery("", resumeParams).substring(1));
    var loginContext = tokenService.newToken(context, LOGIN_CONTEXT_TTL_SEC);
    return redirect(pageUrl(OidcAuthorizationService.PATH_LOGIN, loginContext), new HttpHeaders());
  }

  // ---------------------------------------------------------------- login

  public ResponseEntity<String> loginPage(@RequestParam(name = "ctx", required = false) String ctx,
      HttpServletRequest request) {
    var context = readLoginContext(ctx);
    if (context == null) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    return renderLogin(context, request, HttpStatus.OK, context.params().get("login_hint"), null);
  }

  public ResponseEntity<String> login(@RequestParam MultiValueMap<String, String> form, HttpServletRequest request) {
    var context = readLoginContext(form.getFirst("ctx"));
    if (context == null) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    if (!isCsrfTokenValid(form, request)) {
      return errorPage(HttpStatus.FORBIDDEN, EXPIRED_REQUEST);
    }
    var username = form.getFirst("username") != null ? form.getFirst("username").trim() : "";
    var attemptKey = "login|" + username.toLowerCase() + "|" + clientAddress(request);
    if (loginAttemptService.isLocked(attemptKey)) {
      return renderLogin(context, request, HttpStatus.TOO_MANY_REQUESTS, username,
          "Too many attempts. Try again in a few minutes.");
    }

    User user;
    try {
      user = openIdService.authenticateUser(username, form.getFirst("password"));
    } catch (BusinessException e) {
      loginAttemptService.recordFailure(attemptKey);
      return renderLogin(context, request, HttpStatus.UNAUTHORIZED, username, "Incorrect email or password.");
    }
    loginAttemptService.recordSuccess(attemptKey);

    if (Boolean.TRUE.equals(user.getMfa())) {
      // second factor: the MFA token proves the password step, the session is opened only after the code
      var mfaToken = mfaService.generateMfaTokenResponse(user).getMfaToken();
      var headers = new HttpHeaders();
      headers.add(HttpHeaders.SET_COOKIE, cookie(MFA_COOKIE, mfaToken, MFA_COOKIE_TTL_SEC, "Strict"));
      return redirect(pageUrl(OidcAuthorizationService.PATH_MFA, context.token()), headers);
    }
    if (context.client().isRequireMfa()) {
      return redirectError(context.params().get("redirect_uri"), "access_denied",
          "Multi-factor authentication is required for this application", context.params().get("state"));
    }
    return completeLogin(context, request, user, List.of(OidcSessionService.AMR_PASSWORD));
  }

  // ------------------------------------------------------------------ mfa

  public ResponseEntity<String> mfaPage(@RequestParam(name = "ctx", required = false) String ctx,
      HttpServletRequest request) {
    var context = readLoginContext(ctx);
    if (context == null) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    var user = mfaUser(request);
    if (user == null) {
      return redirect(pageUrl(OidcAuthorizationService.PATH_LOGIN, context.token()), new HttpHeaders());
    }
    return renderMfa(context, request, HttpStatus.OK, user, null);
  }

  public ResponseEntity<String> mfa(@RequestParam MultiValueMap<String, String> form, HttpServletRequest request) {
    var context = readLoginContext(form.getFirst("ctx"));
    if (context == null) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    if (!isCsrfTokenValid(form, request)) {
      return errorPage(HttpStatus.FORBIDDEN, EXPIRED_REQUEST);
    }
    var user = mfaUser(request);
    if (user == null) {
      return redirect(pageUrl(OidcAuthorizationService.PATH_LOGIN, context.token()), new HttpHeaders());
    }
    var attemptKey = "mfa|" + user.getUid();
    if (loginAttemptService.isLocked(attemptKey)) {
      return renderMfa(context, request, HttpStatus.TOO_MANY_REQUESTS, user,
          "Too many attempts. Try again in a few minutes.");
    }
    var code = form.getFirst("totp") != null ? form.getFirst("totp").replaceAll("\\s", "") : "";
    try {
      mfaService.verifyCode(user, code);
    } catch (BadCredentialsException e) {
      loginAttemptService.recordFailure(attemptKey);
      return renderMfa(context, request, HttpStatus.UNAUTHORIZED, user, "Invalid verification code.");
    }
    loginAttemptService.recordSuccess(attemptKey);
    return completeLogin(context, request, user, List.of(OidcSessionService.AMR_PASSWORD, OidcSessionService.AMR_OTP));
  }

  /** User who passed the password step, read from the MFA cookie; null if there is none. */
  private User mfaUser(HttpServletRequest request) {
    var mfaToken = cookieValue(request, MFA_COOKIE);
    if (mfaToken == null) {
      return null;
    }
    try {
      return mfaService.getMfaUser(mfaToken);
    } catch (Exception e) {
      return null;
    }
  }

  /** Opens the SSO session and sends the browser back to the authorization endpoint. */
  private ResponseEntity<String> completeLogin(LoginContext context, HttpServletRequest request, User user,
      List<String> amr) {
    // a new session id at each login: the previous session of this browser, if any, ends
    sessionService.end(cookieValue(request, sessionCookieName()));
    var sessionCookie = sessionService.create(user, amr);

    var headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(sessionCookieName(), sessionCookie,
        mtacProperties.getOpenId().getServer().getSession().getMaxAgeSec(), "Lax"));
    headers.add(HttpHeaders.SET_COOKIE, cookie(MFA_COOKIE, "", 0, "Strict"));
    headers.add(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, "", 0, "Strict"));
    return redirect(authorizationService.url(OidcAuthorizationService.PATH_AUTHORIZE) + "?" + context.query(), headers);
  }

  // ---------------------------------------------------------------- token

  public ResponseEntity<?> token(@RequestParam MultiValueMap<String, String> form,
      @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
    var origin = request.getHeader("Origin");
    try {
      for (var name : List.of("client_id", "client_secret", "code", "redirect_uri", "code_verifier")) {
        if (hasSeveralValues(form, name)) {
          throw OidcException.invalidRequest("Duplicated parameter " + name);
        }
      }
      var client = clientCredentials(form.getFirst("client_id"), form.getFirst("client_secret"), authorization);
      return tokenResponse(authorizationService.exchangeCode(client[0], client[1], origin, form.getFirst("code"),
          form.getFirst("redirect_uri"), form.getFirst("code_verifier")), origin);
    } catch (OidcException e) {
      return jsonError(e, origin);
    }
  }

  /**
   * Refresh of the tokens of an OpenID client. Called by the token endpoint of
   * {@link OpenIdController} when the refresh token is one issued here.
   */
  public ResponseEntity<?> refresh(String clientId, String clientSecret, String refreshToken) {
    var attributes = RequestContextHolder.getRequestAttributes();
    var origin = attributes instanceof ServletRequestAttributes servletAttributes
        ? servletAttributes.getRequest().getHeader("Origin")
        : null;
    try {
      return tokenResponse(authorizationService.refresh(clientId, clientSecret, origin, refreshToken), origin);
    } catch (OidcException e) {
      return jsonError(e, origin);
    }
  }

  public ResponseEntity<?> revoke(@RequestParam MultiValueMap<String, String> form,
      @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
    var origin = request.getHeader("Origin");
    try {
      var client = clientCredentials(form.getFirst("client_id"), form.getFirst("client_secret"), authorization);
      authorizationService.revoke(client[0], client[1], origin, form.getFirst("token"));
      return ResponseEntity.ok().headers(apiHeaders(origin)).build();
    } catch (OidcException e) {
      return jsonError(e, origin);
    }
  }

  public ResponseEntity<?> userinfo(@RequestHeader(value = "Authorization", required = false) String authorization,
      HttpServletRequest request) {
    var origin = request.getHeader("Origin");
    try {
      if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
        throw OidcException.invalidToken("Missing access token");
      }
      return ResponseEntity.ok().headers(apiHeaders(origin))
          .body(authorizationService.userinfo(authorization.substring(7).trim()));
    } catch (OidcException e) {
      var headers = apiHeaders(origin);
      headers.add(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + e.getError() + "\"");
      return ResponseEntity.status(e.getStatus()).headers(headers).body(errorBody(e));
    }
  }

  // --------------------------------------------------------------- logout

  /**
   * RP-initiated logout. With a valid id_token_hint the request comes from a
   * client of the user and the session ends at once. Without it anyone could
   * have sent the user here, so a confirmation is asked.
   */
  public ResponseEntity<String> logoutRequest(@RequestParam MultiValueMap<String, String> query,
      HttpServletRequest request) {
    var params = query.toSingleValueMap();
    var hint = authorizationService.parseIdTokenHint(params.get("id_token_hint"));
    var sessionCookie = cookieValue(request, sessionCookieName());

    if (hint != null) {
      var sessionId = hint.get("sid", String.class);
      sessionService.endById(sessionId);
      var headers = new HttpHeaders();
      if (sessionCookie != null && OidcCrypto.sha256Hex(sessionCookie).equals(sessionId)) {
        headers.add(HttpHeaders.SET_COOKIE, cookie(sessionCookieName(), "", 0, "Lax"));
      }
      return afterLogout(hint.getAudience(), params.get("post_logout_redirect_uri"), params.get("state"), headers);
    }
    if (sessionService.resolve(sessionCookie) == null) {
      return afterLogout(params.get("client_id"), params.get("post_logout_redirect_uri"), params.get("state"),
          new HttpHeaders());
    }

    var model = new HashMap<String, String>();
    model.put("confirm", "true");
    model.put("action", authorizationService.url(OidcAuthorizationService.PATH_LOGOUT));
    model.put("clientId", params.get("client_id"));
    model.put("postLogoutRedirectUri", params.get("post_logout_redirect_uri"));
    model.put("state", params.get("state"));
    return page("logout", model, HttpStatus.OK, request, clientRegistry.find(params.get("client_id")),
        new HttpHeaders());
  }

  public ResponseEntity<String> logout(@RequestParam MultiValueMap<String, String> form, HttpServletRequest request) {
    if (!isCsrfTokenValid(form, request)) {
      return errorPage(HttpStatus.FORBIDDEN, "This request has expired. Try again.");
    }
    sessionService.end(cookieValue(request, sessionCookieName()));
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(sessionCookieName(), "", 0, "Lax"));
    headers.add(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, "", 0, "Strict"));
    return afterLogout(form.getFirst("client_id"), form.getFirst("post_logout_redirect_uri"), form.getFirst("state"),
        headers);
  }

  private ResponseEntity<String> afterLogout(String clientId, String postLogoutRedirectUri, String state,
      HttpHeaders headers) {
    var target = authorizationService.postLogoutRedirect(clientId, postLogoutRedirectUri, state);
    if (target != null) {
      return redirect(target, headers);
    }
    return page("logout", new HashMap<>(), HttpStatus.OK, null, null, headers);
  }

  // -------------------------------------------------------- login context

  /**
   * What the login pages need to resume the authorization request once the user
   * is signed in. It travels in a signed token: the pages never redirect to an
   * address taken from a plain parameter.
   */
  private record LoginContext(String token, String query, Map<String, String> params, MtacOidcClient client) {
  }

  private LoginContext readLoginContext(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      var claims = tokenService.verify(token);
      if (!LOGIN_CONTEXT_AUDIENCE.equals(claims.getAudience())) {
        return null;
      }
      var client = clientRegistry.find(claims.get("cid", String.class));
      var query = claims.get("q", String.class);
      if (client == null || query == null) {
        return null;
      }
      var params = new LinkedHashMap<String, String>();
      for (var pair : query.split("&")) {
        var separator = pair.indexOf('=');
        if (separator > 0) {
          params.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
              URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
      }
      return new LoginContext(token, query, params, client);
    } catch (Exception e) {
      return null;
    }
  }

  private String pageUrl(String path, String loginContext) {
    return authorizationService.url(path) + "?ctx=" + URLEncoder.encode(loginContext, StandardCharsets.UTF_8);
  }

  // ---------------------------------------------------------------- pages

  private ResponseEntity<String> renderLogin(LoginContext context, HttpServletRequest request, HttpStatus status,
      String username, String error) {
    var model = new HashMap<String, String>();
    model.put("action", authorizationService.url(OidcAuthorizationService.PATH_LOGIN));
    model.put("ctx", context.token());
    model.put("clientName", context.client().getName());
    model.put("username", username);
    model.put("error", error);
    return page("login", model, status, request, context.client(), new HttpHeaders());
  }

  private ResponseEntity<String> renderMfa(LoginContext context, HttpServletRequest request, HttpStatus status,
      User user, String error) {
    var model = new HashMap<String, String>();
    model.put("action", authorizationService.url(OidcAuthorizationService.PATH_MFA));
    model.put("ctx", context.token());
    model.put("clientName", context.client().getName());
    model.put("username", user.getLabel());
    model.put("error", error);
    // a user who never validated a code has to enrol the secret first
    if (!Boolean.TRUE.equals(user.getMfaActive()) && user.getTotpSecret() != null) {
      var issuerName = mtacProperties.getOpenId().getServer().getTotpIssuerName();
      model.put("totpSecret", user.getTotpSecret());
      model.put("qrCode", TotpQrCode.dataUri(TotpQrCode.otpAuthUri(issuerName, user.getLabel(), user.getTotpSecret())));
    }
    return page("mfa", model, status, request, context.client(), new HttpHeaders());
  }

  private ResponseEntity<String> errorPage(HttpStatus status, String message) {
    var model = new HashMap<String, String>();
    model.put("message", message);
    return page("error", model, status, null, null, new HttpHeaders());
  }

  /**
   * Renders a hosted page with its security headers.
   *
   * @param request when not null the page holds a form: a CSRF token is added to
   *                the model and to a cookie
   * @param client  client the form may end up redirecting to
   */
  private ResponseEntity<String> page(String template, Map<String, String> model, HttpStatus status,
      HttpServletRequest request, MtacOidcClient client, HttpHeaders headers) {
    var nonce = OidcCrypto.randomToken();
    model.put("nonce", nonce);
    if (request != null) {
      var csrf = cookieValue(request, CSRF_COOKIE);
      if (csrf == null || csrf.isBlank()) {
        csrf = OidcCrypto.randomToken();
      }
      model.put("csrf", csrf);
      headers.add(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, csrf, LOGIN_CONTEXT_TTL_SEC, "Strict"));
    }

    // Browsers apply form-action to the redirects that follow a form submission:
    // the origins of the client must be allowed or the return to the client is blocked.
    var formAction = new StringBuilder("'self'");
    if (client != null) {
      for (var origin : origins(client)) {
        formAction.append(' ').append(origin);
      }
    }
    headers.setContentType(new MediaType("text", "html", StandardCharsets.UTF_8));
    headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.add("Content-Security-Policy", "default-src 'none'; style-src 'nonce-" + nonce
        + "'; img-src 'self' data:; form-action " + formAction + "; frame-ancestors 'none'; base-uri 'none'");
    headers.add("X-Frame-Options", "DENY");
    headers.add("X-Content-Type-Options", "nosniff");
    headers.add("Referrer-Policy", "no-referrer");
    return ResponseEntity.status(status).headers(headers).body(pageRenderer.render(template, model));
  }

  private static Set<String> origins(MtacOidcClient client) {
    var result = new LinkedHashSet<String>();
    var uris = new ArrayList<>(client.getRedirectUris());
    uris.addAll(client.getPostLogoutRedirectUris());
    for (var value : uris) {
      try {
        var uri = URI.create(value);
        if (uri.getScheme() != null && uri.getHost() != null) {
          result.add(uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
        }
      } catch (IllegalArgumentException e) {
        log.warn("Invalid redirect URI in the OpenID client configuration: {}", value);
      }
    }
    return result;
  }

  // ------------------------------------------------------------ responses

  private ResponseEntity<String> redirect(String location, HttpHeaders headers) {
    headers.add(HttpHeaders.LOCATION, location);
    headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.add("Referrer-Policy", "no-referrer");
    // 303: the browser always follows with a GET, also after a form submission
    return ResponseEntity.status(HttpStatus.SEE_OTHER).headers(headers).build();
  }

  /** Error returned to the client through its redirect URI, which is already validated. */
  private ResponseEntity<String> redirectError(String redirectUri, String error, String description, String state) {
    var params = new LinkedHashMap<String, String>();
    params.put("error", error);
    params.put("error_description", description);
    params.put("state", state);
    params.put("iss", authorizationService.getIssuer());
    return redirect(OidcAuthorizationService.appendQuery(redirectUri, params), new HttpHeaders());
  }

  private ResponseEntity<?> tokenResponse(Object body, String origin) {
    return ResponseEntity.ok().headers(apiHeaders(origin)).body(body);
  }

  private ResponseEntity<?> jsonError(OidcException e, String origin) {
    var headers = apiHeaders(origin);
    if ("invalid_client".equals(e.getError())) {
      headers.add(HttpHeaders.WWW_AUTHENTICATE, "Basic");
    }
    return ResponseEntity.status(e.getStatus()).headers(headers).body(errorBody(e));
  }

  private static Map<String, String> errorBody(OidcException e) {
    var body = new LinkedHashMap<String, String>();
    body.put("error", e.getError());
    body.put("error_description", e.getMessage());
    return body;
  }

  /** Headers of the endpoints called by the clients: never cached, CORS for the origins of the clients. */
  private HttpHeaders apiHeaders(String origin) {
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.add(HttpHeaders.PRAGMA, "no-cache");
    if (mtacProperties.getOpenId().getServer().isCorsEnabled() && clientRegistry.isKnownOrigin(origin)) {
      headers.add("Access-Control-Allow-Origin", origin);
      headers.add(HttpHeaders.VARY, "Origin");
    }
    return headers;
  }

  // -------------------------------------------------------------- helpers

  /**
   * Client id and secret, from the Basic authorization header (where both are
   * form-urlencoded, RFC 6749 section 2.3.1) or from the body.
   */
  private static String[] clientCredentials(String clientId, String clientSecret, String authorization) {
    if (authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
      try {
        var decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
        var separator = decoded.indexOf(':');
        if (separator > 0) {
          return new String[] { URLDecoder.decode(decoded.substring(0, separator), StandardCharsets.UTF_8),
              URLDecoder.decode(decoded.substring(separator + 1), StandardCharsets.UTF_8) };
        }
      } catch (IllegalArgumentException e) {
        // falls through to the error below
      }
      throw OidcException.invalidClient("Invalid Basic authorization header");
    }
    if (clientId == null || clientId.isBlank()) {
      throw OidcException.invalidClient("Missing client_id");
    }
    return new String[] { clientId, clientSecret };
  }

  private boolean isCsrfTokenValid(MultiValueMap<String, String> form, HttpServletRequest request) {
    return OidcCrypto.constantTimeEquals(cookieValue(request, CSRF_COOKIE), form.getFirst("csrf"));
  }

  private static boolean hasSeveralValues(MultiValueMap<String, String> params, String name) {
    var values = params.get(name);
    return values != null && values.size() > 1;
  }

  private static String cookieValue(HttpServletRequest request, String name) {
    var cookie = WebUtils.getCookie(request, name);
    return cookie != null ? cookie.getValue() : null;
  }

  private String sessionCookieName() {
    return mtacProperties.getOpenId().getServer().getSession().getCookieName();
  }

  /** A cookie of the provider: never readable by scripts, sent over HTTPS only unless configured otherwise. */
  private String cookie(String name, String value, int maxAgeSec, String sameSite) {
    var secure = mtacProperties.getOpenId().getServer().getSession().isSecure() ? "; Secure" : "";
    return name + "=" + value + "; Path=/; Max-Age=" + maxAgeSec + "; HttpOnly; SameSite=" + sameSite + secure;
  }

  /** Address of the browser: the reverse proxy sets X-Real-IP and overwrites whatever the client sent. */
  private static String clientAddress(HttpServletRequest request) {
    var forwarded = request.getHeader("X-Real-IP");
    return forwarded != null && !forwarded.isBlank() ? forwarded : request.getRemoteAddr();
  }
}
