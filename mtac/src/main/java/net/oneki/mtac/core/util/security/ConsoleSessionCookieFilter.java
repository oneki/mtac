package net.oneki.mtac.core.util.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.BrowserSessionService;
import net.oneki.mtac.model.core.config.MtacProperties;

/**
 * Renews the Console access token cookie from the Console session cookie
 * (backend for frontend, {@code mtac.iam.browser-session}), so that the
 * browser never refreshes anything itself.
 *
 * <p>Runs in the resource server chain, before the bearer token filter. When
 * the request carries the session cookie and its access token cookie is
 * missing, unreadable, expired or expires within {@code renew-before-sec}, the
 * Console session is renewed ({@link BrowserSessionService#renew}: live
 * session, live SSO session, then slide), a new access token is set as cookie
 * and the request goes on with it: the request is wrapped so that
 * {@link HttpServletRequest#getCookies()} returns the new access token cookie,
 * which is what {@link BearerTokenResolver} (and the issuer resolution) reads.
 * The expiry of the current cookie is read WITHOUT verifying the token, only to
 * decide whether to renew: the resource server still verifies whatever token
 * the request ends up with.
 *
 * <p>When the session can no longer be used, both cookies are cleared and the
 * request goes on without access token (401). The
 * {@code <apiBasePath>/auth/} endpoints are left alone. An Authorization header
 * does not stop the renewal: the token resolver reads the cookie first.
 *
 * <p>Registered after {@link CookieCsrfFilter}: a forged request is refused
 * before it can renew (keep alive) the session.
 *
 * <p>Not a Spring bean on purpose: a Filter bean would also be registered as a
 * servlet filter. {@link BaseResourceServerConfig} creates it.
 */
@Slf4j
public class ConsoleSessionCookieFilter extends OncePerRequestFilter {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final MtacProperties mtacProperties;
  private final BrowserSessionService browserSessions;
  private final BrowserSessionCookies cookies;
  private Clock clock = Clock.systemUTC();

  public ConsoleSessionCookieFilter(MtacProperties mtacProperties, BrowserSessionService browserSessions) {
    this.mtacProperties = mtacProperties;
    this.browserSessions = browserSessions;
    this.cookies = new BrowserSessionCookies(mtacProperties);
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!config().isEnabled()) {
      return true;
    }
    var path = request.getRequestURI().substring(request.getContextPath().length());
    return path.startsWith(mtacProperties.getApiBasePath() + BrowserSessionCookies.TRANSACTION_PATH + "/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var sessionToken = cookieValue(request, config().getSessionCookieName());
    if (sessionToken == null || sessionToken.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    var accessCookieName = mtacProperties.getIam().getAccessTokenCookieName();
    var accessToken = cookieValue(request, accessCookieName);
    if (accessToken != null && !needsRenewal(accessToken)) {
      chain.doFilter(request, response);
      return;
    }

    BrowserSessionService.Renewed renewed;
    try {
      renewed = browserSessions.renew(sessionToken);
    } catch (RuntimeException e) {
      // database unreachable or similar: the browser keeps its cookies, this request goes on as it came
      log.warn("Console session renewal failed: {}", e.getMessage());
      chain.doFilter(request, response);
      return;
    }
    if (renewed == null) {
      // logout elsewhere, SSO session ended, password change, expiry: the browser must sign in again
      response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearAccessToken());
      response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearSession());
      // an impersonation kept by the session ended with it
      cookies.clearImpersonation().forEach(cookie -> response.addHeader(HttpHeaders.SET_COOKIE, cookie));
      chain.doFilter(new AccessTokenCookieRequest(request, accessCookieName, null), response);
      return;
    }
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.accessToken(renewed.accessToken(), renewed.accessTokenTtlSec()));
    // the session slid: its cookie lives as long as the session
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.session(sessionToken, renewed.sessionMaxAgeSec()));
    if (renewed.impersonationMaxAgeSec() != null) {
      // impersonation kept by the session: indicator for its remaining lifetime, cleared once it ended
      response.addHeader(HttpHeaders.SET_COOKIE, cookies.impersonationActive(renewed.impersonationMaxAgeSec()));
    }
    chain.doFilter(new AccessTokenCookieRequest(request, accessCookieName, renewed.accessToken()), response);
  }

  /**
   * True when the token is unreadable, has no expiry, or expires within
   * renew-before-sec. A short-lived token (the last minute of an
   * impersonation, a small tokenExpiresIn) is renewed only in the second half
   * of its lifetime, otherwise every request would renew a token just minted.
   */
  boolean needsRenewal(String token) {
    var claims = unverifiedClaims(token);
    var expiresAt = claims != null ? epochSecond(claims.get("exp")) : null;
    if (expiresAt == null) {
      return true;
    }
    long renewBefore = config().getRenewBeforeSec();
    var issuedAt = epochSecond(claims.get("iat"));
    if (issuedAt != null && issuedAt.isBefore(expiresAt)) {
      renewBefore = Math.min(renewBefore, (expiresAt.getEpochSecond() - issuedAt.getEpochSecond()) / 2);
    }
    return !clock.instant().plusSeconds(renewBefore).isBefore(expiresAt);
  }

  /** exp claim of a JWT, read without checking the signature; null when it cannot be read. */
  static Instant unverifiedExpiry(String token) {
    var claims = unverifiedClaims(token);
    return claims != null ? epochSecond(claims.get("exp")) : null;
  }

  /** Payload of a JWT, read without checking the signature; null when it cannot be read. */
  private static JsonNode unverifiedClaims(String token) {
    try {
      var parts = token.split("\\.");
      if (parts.length < 2) {
        return null;
      }
      var payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      return JSON.readTree(payload);
    } catch (Exception e) {
      return null;
    }
  }

  private static Instant epochSecond(JsonNode value) {
    return value != null && value.canConvertToLong() ? Instant.ofEpochSecond(value.asLong()) : null;
  }

  private static String cookieValue(HttpServletRequest request, String name) {
    var cookie = WebUtils.getCookie(request, name);
    return cookie != null ? cookie.getValue() : null;
  }

  private MtacProperties.MtacBrowserSession config() {
    return mtacProperties.getIam().getBrowserSession();
  }

  /**
   * The request with its access token cookie replaced by the renewed token, or
   * removed ({@code token} null).
   */
  static final class AccessTokenCookieRequest extends HttpServletRequestWrapper {
    private final Cookie[] cookies;

    AccessTokenCookieRequest(HttpServletRequest request, String name, String token) {
      super(request);
      var result = new ArrayList<Cookie>();
      if (request.getCookies() != null) {
        for (var cookie : request.getCookies()) {
          if (!name.equals(cookie.getName())) {
            result.add(cookie);
          }
        }
      }
      if (token != null) {
        result.add(new Cookie(name, token));
      }
      cookies = result.isEmpty() ? null : result.toArray(new Cookie[0]);
    }

    @Override
    public Cookie[] getCookies() {
      return cookies;
    }
  }
}
