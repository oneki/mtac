package net.oneki.mtac.model.core.openid.oidc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keys of {@code mtac.jwt.audiences}. Every access token carries the URL of
 * exactly one of them in its aud claim, and each resource server path accepts
 * one of them, or a list of them (see {@link #anyOf(String...)}).
 */
public final class TokenAudience {
  public static final String CONSOLE = "console"; // /api/**, /ws/**
  public static final String API = "api"; // /public-api/**
  public static final String MCP = "mcp"; // /mcp
  /**
   * /api/** (never /ws/**): the first-party mobile applications. Only a yml
   * client that lists it in {@code first-party-audiences} can obtain it.
   */
  public static final String MOBILE = "mobile";

  /** Every known key: none of them may be used as a client_id. */
  public static final List<String> KEYS = List.of(CONSOLE, API, MCP, MOBILE);
  /**
   * Keys a configured {@code mtac.jwt.audiences} map must contain. The mobile
   * audience is optional: without it, no client can obtain it.
   */
  public static final List<String> REQUIRED_KEYS = List.of(CONSOLE, API, MCP);

  /** JOSE header typ of the access tokens (RFC 9068). */
  public static final String ACCESS_TOKEN_TYP = "at+jwt";

  /** Separator of the keys of a requirement that accepts several audiences. */
  private static final String ANY_OF_SEPARATOR = " ";

  private TokenAudience() {
  }

  /**
   * Requirement of a path that accepts any of these audience keys, e.g.
   * {@code anyOf(CONSOLE, MOBILE)} = {@code "console mobile"}. A single key is
   * returned as is.
   */
  public static String anyOf(String... keys) {
    return String.join(ANY_OF_SEPARATOR, keys);
  }

  /** The audience keys a requirement (a key or {@link #anyOf(String...)}) accepts. */
  public static Set<String> accepted(String requirement) {
    var result = new LinkedHashSet<String>();
    if (requirement != null) {
      for (var key : requirement.trim().split(ANY_OF_SEPARATOR + "+")) {
        if (!key.isEmpty()) {
          result.add(key);
        }
      }
    }
    return result;
  }

  /** True when a requirement (a key or {@link #anyOf(String...)}) accepts this audience key. */
  public static boolean accepts(String requirement, String key) {
    return key != null && accepted(requirement).contains(key);
  }
}
