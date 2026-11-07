package net.oneki.mtac.core.util.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.http.HttpServletRequest;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;

/**
 * Picks the authentication manager of a request from the audience its path
 * requires (see {@link TokenAudience}):
 * <ul>
 * <li>{@code /public-api/**} -> api;</li>
 * <li>{@code /mcp} and {@code /mcp/**} -> mcp;</li>
 * <li>anything else ({@code /api/**}, {@code /ws/**}, ...) -> console.</li>
 * </ul>
 * The path is decoded and taken without the servlet context path. Public paths never reach
 * the resolver (they are ignored by the security filter chain), and neither
 * does a request without bearer token.
 */
public class AudienceAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {
  public static final String PUBLIC_API_PATH = "/public-api";
  public static final String MCP_PATH = "/mcp";

  private final Function<String, AuthenticationManager> managerFactory;
  private final Map<String, AuthenticationManager> managers = new ConcurrentHashMap<>();

  /**
   * @param managerFactory builds the manager of an audience key; called once per
   *                       key, the result is cached
   */
  public AudienceAuthenticationManagerResolver(Function<String, AuthenticationManager> managerFactory) {
    this.managerFactory = managerFactory;
  }

  @Override
  public AuthenticationManager resolve(HttpServletRequest request) {
    return managers.computeIfAbsent(audienceOf(request), managerFactory);
  }

  /** Audience key required on the path of a request. */
  public static String audienceOf(HttpServletRequest request) {
    // decoded, without the context path and the ;parameters, as Spring MVC matches it
    return audienceOf(UrlPathHelper.defaultInstance.getPathWithinApplication(request));
  }

  /** Audience key required on a path (without the servlet context path). */
  public static String audienceOf(String path) {
    if (path == null) {
      return TokenAudience.CONSOLE;
    }
    if (isUnder(path, PUBLIC_API_PATH)) {
      return TokenAudience.API;
    }
    if (isUnder(path, MCP_PATH)) {
      return TokenAudience.MCP;
    }
    return TokenAudience.CONSOLE;
  }

  private static boolean isUnder(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }
}
