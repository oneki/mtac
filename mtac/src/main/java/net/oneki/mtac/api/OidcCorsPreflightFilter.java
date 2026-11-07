package net.oneki.mtac.api;

import java.io.IOException;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.service.openid.oidc.OidcAuthorizationService;
import net.oneki.mtac.core.service.openid.oidc.OidcClientRegistry;

/**
 * Answers the CORS preflight requests of the OpenID endpoints a JavaScript
 * client calls (token, userinfo, revoke, discovery, JWKS). Only active when
 * {@code mtac.openid.server.cors-enabled} is true, i.e. when no reverse proxy
 * adds the CORS headers. The interactive endpoints are never exposed to CORS.
 */
@Component
@ConditionalOnProperty(prefix = "mtac.openid.server", name = "cors-enabled", havingValue = "true")
@RequiredArgsConstructor
public class OidcCorsPreflightFilter extends OncePerRequestFilter {
  private static final Set<String> PUBLIC_RESOURCES = Set.of(OidcAuthorizationService.PATH_DISCOVERY,
      OidcAuthorizationService.PATH_JWKS);
  private static final Set<String> CLIENT_ENDPOINTS = Set.of(OidcAuthorizationService.PATH_TOKEN,
      OidcAuthorizationService.PATH_USERINFO, OidcAuthorizationService.PATH_REVOKE);

  private final OidcClientRegistry clientRegistry;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var uri = request.getRequestURI();
    var publicResource = PUBLIC_RESOURCES.stream().anyMatch(uri::endsWith);
    var clientEndpoint = CLIENT_ENDPOINTS.stream().anyMatch(uri::endsWith);
    if (!clientRegistry.isEnabled() || (!publicResource && !clientEndpoint)) {
      filterChain.doFilter(request, response);
      return;
    }

    var origin = request.getHeader(HttpHeaders.ORIGIN);
    if (publicResource) {
      // public documents, served without credentials: readable from any origin
      response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    }
    if (!CorsUtils.isPreFlightRequest(request)) {
      // the endpoints add the header themselves on the actual request
      filterChain.doFilter(request, response);
      return;
    }
    if (clientEndpoint && clientRegistry.isKnownOrigin(origin)) {
      response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
      response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
    }
    response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS");
    response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization, Content-Type");
    response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "600");
    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
  }
}
