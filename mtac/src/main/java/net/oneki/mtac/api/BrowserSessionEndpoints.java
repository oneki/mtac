package net.oneki.mtac.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.BrowserSessionService;
import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.core.service.openid.oidc.OidcAuthorizationService;
import net.oneki.mtac.core.service.openid.oidc.OidcCrypto;
import net.oneki.mtac.core.util.security.BrowserSessionCookies;
import net.oneki.mtac.core.util.security.CookieCsrfFilter;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.oidc.OidcException;

/**
 * Sign-in of the Console through the OpenID provider, the API being the client
 * (backend for frontend, {@code mtac.iam.browser-session}). No token reaches
 * the browser storage: the API exchanges the code itself and keeps the Console
 * session in HttpOnly cookies.
 * <ul>
 * <li>{@code GET <api>/auth/login[?prompt=&login_hint=]}: state, PKCE verifier
 * and nonce in a signed 10-minute transaction cookie, then 302 to the
 * authorization endpoint of the provider;</li>
 * <li>{@code GET <api>/auth/callback?code&state}: checks the state, exchanges
 * the code in-process, opens a Console session linked to the SSO session
 * ({@code sid}), sets the access token and session cookies, then 302 to the
 * Console ({@code post-login-path}). Any failure: 302 to
 * {@code post-login-error-path?error=<code>}, with a code of a fixed list;</li>
 * <li>{@code GET <api>/auth/logout[?post_logout_redirect_uri=]}: closes the
 * Console session, ends its SSO session, clears the cookies, then 302 to the
 * given URI when it is on a Console origin, else to the Console root;</li>
 * <li>{@code GET <api>/auth/config}: {@code {"passwordLogin": bool}}.</li>
 * </ul>
 * All four paths are public (the security chain ignores them).
 */
@Slf4j
@Component
public class BrowserSessionEndpoints {
  public static final String PATH_LOGIN = "/auth/login";
  public static final String PATH_CALLBACK = "/auth/callback";
  public static final String PATH_LOGOUT = "/auth/logout";
  public static final String PATH_CONFIG = "/auth/config";
  /** aud of the transaction token: no other token signed with the key has it. */
  public static final String TRANSACTION_AUDIENCE = "mtac-browser-login";
  public static final int TRANSACTION_TTL_SEC = 600;
  public static final String SCOPE = "openid profile email";

  /** Error codes the Console may receive; anything else becomes server_error. */
  public static final String ERROR_INVALID_STATE = "invalid_state";
  public static final String ERROR_SERVER = "server_error";
  static final Set<String> FORWARDED_ERRORS = Set.of(
      // sent by the provider (RFC 6749 4.1.2.1, OpenID Connect Core 3.1.2.6)
      "access_denied", "invalid_request", "unauthorized_client", "unsupported_response_type", "invalid_scope",
      "server_error", "temporarily_unavailable", "login_required", "consent_required", "interaction_required",
      "account_selection_required", "invalid_target",
      // raised by the code exchange
      "invalid_grant", "invalid_client",
      // raised here
      ERROR_INVALID_STATE);
  private static final int MAX_LOGIN_HINT_LENGTH = 256;

  private final MtacProperties mtacProperties;
  private final BrowserSessionService browserSessions;
  private final OidcAuthorizationService authorizationService;
  private final JwtTokenService tokenService;
  private final BrowserSessionCookies cookies;

  public BrowserSessionEndpoints(MtacProperties mtacProperties, BrowserSessionService browserSessions,
      OidcAuthorizationService authorizationService, JwtTokenService tokenService) {
    this.mtacProperties = mtacProperties;
    this.browserSessions = browserSessions;
    this.authorizationService = authorizationService;
    this.tokenService = tokenService;
    this.cookies = new BrowserSessionCookies(mtacProperties);
  }

  public boolean isEnabled() {
    return config().isEnabled();
  }

  /** Makes the endpoints find the users through the application's user service. */
  public void use(OpenIdService openIdService) {
    browserSessions.use(openIdService);
  }

  public void register(RequestMappingHandlerMapping handlerMapping, String basePath) throws NoSuchMethodException {
    if (config().getRedirectUri() == null || config().getRedirectUri().isBlank()) {
      log.error("mtac.iam.browser-session.enabled is true but redirect-uri is missing: the Console sign-in "
          + "endpoints are not registered");
      return;
    }
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + PATH_LOGIN).methods(RequestMethod.GET).build(),
        this, BrowserSessionEndpoints.class.getDeclaredMethod("login", String.class, String.class, String.class));
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + PATH_CALLBACK).methods(RequestMethod.GET).build(),
        this, BrowserSessionEndpoints.class.getDeclaredMethod("callback", MultiValueMap.class,
            HttpServletRequest.class));
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + PATH_LOGOUT).methods(RequestMethod.GET).build(),
        this, BrowserSessionEndpoints.class.getDeclaredMethod("logout", String.class, HttpServletRequest.class));
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + PATH_CONFIG).methods(RequestMethod.GET)
            .produces(MediaType.APPLICATION_JSON_VALUE).build(),
        this, BrowserSessionEndpoints.class.getDeclaredMethod("clientConfig"));
  }

  // ---------------------------------------------------------------- login

  public ResponseEntity<Void> login(@RequestParam(name = "prompt", required = false) String prompt,
      @RequestParam(name = "login_hint", required = false) String loginHint,
      @RequestParam(name = "origin", required = false) String requestedOrigin) {
    // a Console served from another configured origin (e.g. run locally against the beta API) names itself
    var origin = allowedOrigin(requestedOrigin);
    var state = OidcCrypto.randomToken();
    var verifier = OidcCrypto.randomToken(); // 43 base64url characters: a valid PKCE verifier
    var nonce = OidcCrypto.randomToken();
    var transaction = new LinkedHashMap<String, Object>();
    transaction.put("aud", TRANSACTION_AUDIENCE);
    transaction.put("st", state);
    transaction.put("cv", verifier);
    transaction.put("n", nonce);
    transaction.put("o", origin);

    var params = new LinkedHashMap<String, String>();
    params.put("response_type", "code");
    params.put("client_id", config().getClientId());
    params.put("redirect_uri", redirectUri(origin));
    params.put("scope", SCOPE);
    params.put("state", state);
    params.put("nonce", nonce);
    params.put("code_challenge", OidcCrypto.codeChallenge(verifier));
    params.put("code_challenge_method", "S256");
    if (prompt != null && !prompt.isBlank()) {
      params.put("prompt", prompt); // validated by the provider
    }
    if (loginHint != null && !loginHint.isBlank() && loginHint.length() <= MAX_LOGIN_HINT_LENGTH) {
      params.put("login_hint", loginHint);
    }
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE,
        cookies.transaction(tokenService.newToken(transaction, TRANSACTION_TTL_SEC), TRANSACTION_TTL_SEC));
    return redirect(OidcAuthorizationService.appendQuery(
        authorizationService.url(OidcAuthorizationService.PATH_AUTHORIZE), params), headers);
  }

  // ------------------------------------------------------------- callback

  private record Transaction(String state, String verifier, String nonce, String origin) {
  }

  public ResponseEntity<Void> callback(@RequestParam MultiValueMap<String, String> query,
      HttpServletRequest request) {
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookies.clearTransaction()); // single use, whatever happens
    var error = query.getFirst("error");
    if (error != null) {
      return errorRedirect(error, headers);
    }
    var transaction = readTransaction(cookieValue(request, config().getTransactionCookieName()));
    if (transaction == null || !OidcCrypto.constantTimeEquals(transaction.state(), query.getFirst("state"))
        || hasSeveralValues(query, "state") || hasSeveralValues(query, "code")) {
      return errorRedirect(ERROR_INVALID_STATE, headers);
    }
    var origin = transaction.origin();
    // RFC 9207: when the provider names itself, it must be ours
    var iss = query.getFirst("iss");
    if (iss != null && !iss.equals(authorizationService.getIssuer())) {
      return errorRedirect("invalid_request", origin, headers);
    }
    var code = query.getFirst("code");
    if (code == null || code.isBlank()) {
      return errorRedirect("invalid_request", origin, headers);
    }

    io.jsonwebtoken.Claims idToken;
    try {
      var tokens = authorizationService.exchangeCode(config().getClientId(), null, null, code,
          redirectUri(origin), transaction.verifier());
      idToken = tokenService.verify(tokens.getIdToken());
    } catch (OidcException e) {
      log.warn("Console sign-in: the code exchange failed: {} {}", e.getError(), e.getMessage());
      return errorRedirect(e.getError(), origin, headers);
    } catch (RuntimeException e) {
      log.error("Console sign-in: the code exchange failed", e);
      return errorRedirect(ERROR_SERVER, origin, headers);
    }
    if (!config().getClientId().equals(idToken.getAudience())
        || !OidcCrypto.constantTimeEquals(transaction.nonce(), idToken.get("nonce", String.class))) {
      log.warn("Console sign-in: the ID token does not match the sign-in request (aud or nonce)");
      return errorRedirect("invalid_grant", origin, headers);
    }
    var user = browserSessions.user(idToken.getSubject());
    if (user == null) {
      return errorRedirect("access_denied", origin, headers);
    }
    // the Console session this browser had until now is replaced, not left alive next to the new one
    var previousSession = cookieValue(request, config().getSessionCookieName());
    if (previousSession != null && !previousSession.isBlank()) {
      browserSessions.closePrevious(previousSession);
    }
    var signedIn = browserSessions.open(user, idToken.get("sid", String.class));
    headers.add(HttpHeaders.SET_COOKIE, cookies.accessToken(signedIn.accessToken(), signedIn.accessTokenTtlSec()));
    headers.add(HttpHeaders.SET_COOKIE, cookies.session(signedIn.sessionToken(), signedIn.sessionMaxAgeSec()));
    // a new sign-in never inherits the impersonation indicator of a previous session of this browser
    cookies.clearImpersonation().forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, cookie));
    return redirect(origin + config().getPostLoginPath(), headers);
  }

  private Transaction readTransaction(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      var claims = tokenService.verify(token);
      if (!TRANSACTION_AUDIENCE.equals(claims.getAudience())) {
        return null;
      }
      var state = claims.get("st", String.class);
      var verifier = claims.get("cv", String.class);
      var nonce = claims.get("n", String.class);
      var origin = allowedOrigin(claims.get("o", String.class));
      return state != null && verifier != null && nonce != null ? new Transaction(state, verifier, nonce, origin)
          : null;
    } catch (RuntimeException e) {
      return null; // expired, forged, or not a transaction
    }
  }

  // --------------------------------------------------------------- logout

  public ResponseEntity<Void> logout(
      @RequestParam(name = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
      HttpServletRequest request) {
    var headers = new HttpHeaders();
    var sessionToken = cookieValue(request, config().getSessionCookieName());
    var accessToken = cookieValue(request, mtacProperties.getIam().getAccessTokenCookieName());
    // SameSite=Strict: a navigation started by another site carries no cookie, so it cannot sign the user out.
    // A page of a sibling host (same site) would send them: only a Console page (same-origin) or the user
    // (address bar, bookmark: none) may sign out; a browser without Sec-Fetch-Site is let through.
    var fetchSite = request.getHeader(CookieCsrfFilter.SEC_FETCH_SITE);
    var allowed = fetchSite == null || fetchSite.isBlank()
        || CookieCsrfFilter.SAME_ORIGIN.equalsIgnoreCase(fetchSite.trim()) || "none".equalsIgnoreCase(fetchSite.trim());
    if (!allowed) {
      log.warn("Console logout refused: Sec-Fetch-Site {}", fetchSite);
    } else if (sessionToken != null || accessToken != null) {
      if (sessionToken != null) {
        browserSessions.logout(sessionToken);
      }
      headers.add(HttpHeaders.SET_COOKIE, cookies.clearAccessToken());
      headers.add(HttpHeaders.SET_COOKIE, cookies.clearSession());
      cookies.clearImpersonation().forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, cookie));
    }
    var target = isConsoleUri(postLogoutRedirectUri) ? postLogoutRedirectUri : consoleOrigin() + "/";
    return redirect(target, headers);
  }

  // --------------------------------------------------------------- config

  public ResponseEntity<Map<String, Object>> clientConfig() {
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
        // issuer: base URL of the hosted pages (the Console redirects the former reset-password links there)
        .body(Map.of("passwordLogin", config().isPasswordLoginEnabled(), "issuer", authorizationService.getIssuer()));
  }

  // -------------------------------------------------------------- helpers

  /**
   * Origin of the Console: the configured origin of the redirect URI, else the
   * origin of the redirect URI itself.
   */
  public String consoleOrigin() {
    var redirectOrigin = origin(config().getRedirectUri());
    for (var configured : config().getOrigins()) {
      var normalized = normalizeOrigin(configured);
      if (normalized != null && normalized.equals(redirectOrigin)) {
        return normalized;
      }
    }
    return redirectOrigin;
  }

  /** True for an absolute http(s) URI whose origin is exactly a Console origin (no user info). */
  public boolean isConsoleUri(String uri) {
    if (uri == null || uri.isBlank()) {
      return false;
    }
    try {
      var parsed = new URI(uri);
      if (!parsed.isAbsolute() || parsed.getRawUserInfo() != null || parsed.getHost() == null) {
        return false;
      }
      var origin = origin(parsed);
      if (origin == null) {
        return false;
      }
      if (origin.equals(consoleOrigin())) {
        return true;
      }
      return config().getOrigins().stream().map(BrowserSessionEndpoints::normalizeOrigin).anyMatch(origin::equals);
    } catch (URISyntaxException e) {
      return false;
    }
  }

  static String normalizeOrigin(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return origin(value.trim());
  }

  private static String origin(String uri) {
    try {
      return origin(new URI(uri));
    } catch (URISyntaxException | NullPointerException e) {
      return null;
    }
  }

  /** scheme://host[:port], lower case, for http and https only. */
  private static String origin(URI uri) {
    var scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
    if (!"http".equals(scheme) && !"https".equals(scheme) || uri.getHost() == null) {
      return null;
    }
    var port = uri.getPort();
    var defaultPort = "http".equals(scheme) ? 80 : 443;
    return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (port > 0 && port != defaultPort ? ":" + port : "");
  }

  private ResponseEntity<Void> errorRedirect(String error, HttpHeaders headers) {
    return errorRedirect(error, consoleOrigin(), headers);
  }

  private ResponseEntity<Void> errorRedirect(String error, String origin, HttpHeaders headers) {
    var code = error != null && FORWARDED_ERRORS.contains(error) ? error : ERROR_SERVER;
    return redirect(OidcAuthorizationService.appendQuery(origin + config().getPostLoginErrorPath(),
        Map.of("error", code)), headers);
  }

  /** The requested Console origin when it is a configured one, else the default Console origin. */
  String allowedOrigin(String requested) {
    var normalized = normalizeOrigin(requested);
    if (normalized != null && config().getOrigins().stream().map(BrowserSessionEndpoints::normalizeOrigin)
        .anyMatch(normalized::equals)) {
      return normalized;
    }
    return consoleOrigin();
  }

  /**
   * Redirect URI of a Console origin: the path of the configured redirect URI
   * on that origin. The provider still checks it against the client's
   * registered redirect URIs.
   */
  String redirectUri(String origin) {
    if (origin.equals(origin(config().getRedirectUri()))) {
      return config().getRedirectUri();
    }
    return origin + URI.create(config().getRedirectUri()).getRawPath();
  }

  private static ResponseEntity<Void> redirect(String location, HttpHeaders headers) {
    headers.add(HttpHeaders.LOCATION, location);
    headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.add("Referrer-Policy", "no-referrer");
    return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
  }

  private static boolean hasSeveralValues(MultiValueMap<String, String> params, String name) {
    var values = params.get(name);
    return values != null && values.size() > 1;
  }

  private static String cookieValue(HttpServletRequest request, String name) {
    var cookie = WebUtils.getCookie(request, name);
    return cookie != null ? cookie.getValue() : null;
  }

  private MtacProperties.MtacBrowserSession config() {
    return mtacProperties.getIam().getBrowserSession();
  }
}
