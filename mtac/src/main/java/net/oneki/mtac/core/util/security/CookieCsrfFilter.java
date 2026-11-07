package net.oneki.mtac.core.util.security;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.model.core.config.MtacProperties;

/**
 * CSRF protection of the requests authenticated by the Console cookies
 * (backend for frontend, {@code mtac.iam.browser-session}).
 *
 * <p>An unsafe request (POST, PUT, PATCH, DELETE) that carries the access token
 * cookie or the Console session cookie is accepted only when the browser says it comes from the API's own origin
 * ({@code Sec-Fetch-Site: same-origin}) or, from a browser that does not send
 * {@code Sec-Fetch-Site}, when its {@code Origin} header (else the origin of its
 * {@code Referer}) is one of the Console origins
 * ({@code mtac.iam.browser-session.origins}). Otherwise: 403
 * {@code {"error":"csrf"}}. A present {@code Sec-Fetch-Site} other than
 * {@code same-origin} is refused whatever the Origin header says.
 *
 * <p>Not concerned: the safe methods and the requests without Console cookie
 * (service accounts, connectors, MCP clients: no ambient credential, nothing to
 * forge). An {@code Authorization} header does not exempt a request: the token
 * resolver reads the cookie first ({@code token-location: cookie,header,...}),
 * so such a request may still be authenticated by the cookie. The public paths never reach the security chain (the hosted pages have their own
 * CSRF token).
 *
 * <p>Not a Spring bean on purpose (see {@link ConsoleSessionCookieFilter}):
 * {@link BaseResourceServerConfig} creates it.
 */
@Slf4j
public class CookieCsrfFilter extends OncePerRequestFilter {
  public static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
  public static final String SAME_ORIGIN = "same-origin";
  private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
  private static final byte[] BODY = "{\"error\":\"csrf\"}".getBytes(StandardCharsets.UTF_8);

  private final MtacProperties mtacProperties;

  public CookieCsrfFilter(MtacProperties mtacProperties) {
    this.mtacProperties = mtacProperties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !mtacProperties.getIam().getBrowserSession().isEnabled()
        || !UNSAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))
        || !hasConsoleCookie(request);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (isAllowed(request)) {
      chain.doFilter(request, response);
      return;
    }
    var origin = request.getHeader(HttpHeaders.ORIGIN);
    log.warn("CSRF: {} {} refused (cookie authentication, Sec-Fetch-Site {}, Origin {}, Referer origin {})",
        request.getMethod(), request.getRequestURI(), request.getHeader(SEC_FETCH_SITE), origin,
        origin == null ? normalizeOrigin(request.getHeader(HttpHeaders.REFERER)) : "-");
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setContentLength(BODY.length);
    response.getOutputStream().write(BODY);
  }

  /** The rule itself, for a cookie-authenticated unsafe request. */
  boolean isAllowed(HttpServletRequest request) {
    var fetchSite = request.getHeader(SEC_FETCH_SITE);
    if (fetchSite != null && !fetchSite.isBlank()) {
      // set by the browser, a page cannot change it
      return SAME_ORIGIN.equalsIgnoreCase(fetchSite.trim());
    }
    var origin = request.getHeader(HttpHeaders.ORIGIN);
    var requestOrigin = origin != null ? normalizeOrigin(origin) : normalizeOrigin(request.getHeader(HttpHeaders.REFERER));
    return requestOrigin != null && consoleOrigins().contains(requestOrigin);
  }

  private boolean hasConsoleCookie(HttpServletRequest request) {
    return hasCookie(request, mtacProperties.getIam().getAccessTokenCookieName())
        || hasCookie(request, mtacProperties.getIam().getBrowserSession().getSessionCookieName());
  }

  private static boolean hasCookie(HttpServletRequest request, String name) {
    if (name == null) {
      return false;
    }
    var cookie = WebUtils.getCookie(request, name);
    return cookie != null && cookie.getValue() != null && !cookie.getValue().isEmpty();
  }

  /** The configured Console origins, normalized. */
  List<String> consoleOrigins() {
    return consoleOrigins(mtacProperties);
  }

  /** {@code mtac.iam.browser-session.origins}, normalized ({@link #normalizeOrigin}), invalid ones left out. */
  public static List<String> consoleOrigins(MtacProperties mtacProperties) {
    var origins = mtacProperties.getIam().getBrowserSession().getOrigins();
    if (origins == null) {
      return List.of();
    }
    return origins.stream().map(CookieCsrfFilter::normalizeOrigin).filter(Objects::nonNull).distinct().toList();
  }

  /**
   * {@code scheme://host[:port]} of an http(s) origin or URL, lower case, the
   * default port left out (same rule as the Console sign-in endpoints); null
   * when it is not one ({@code null} origin of a sandboxed page, other scheme,
   * no host, user info).
   */
  public static String normalizeOrigin(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      var uri = new URI(value.trim());
      var scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
      if (!"http".equals(scheme) && !"https".equals(scheme) || uri.getHost() == null
          || uri.getRawUserInfo() != null) {
        return null;
      }
      var port = uri.getPort();
      var defaultPort = "http".equals(scheme) ? 80 : 443;
      return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT)
          + (port > 0 && port != defaultPort ? ":" + port : "");
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
