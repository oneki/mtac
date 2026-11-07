package net.oneki.mtac.model.core.openid.oidc;

import java.util.List;

/**
 * Keys of {@code mtac.jwt.audiences}. Every access token carries the URL of
 * exactly one of them in its aud claim, and each resource server path accepts
 * one of them only.
 */
public final class TokenAudience {
  public static final String CONSOLE = "console"; // /api/**, /ws/**
  public static final String API = "api"; // /public-api/**
  public static final String MCP = "mcp"; // /mcp

  public static final List<String> KEYS = List.of(CONSOLE, API, MCP);

  /** JOSE header typ of the access tokens (RFC 9068). */
  public static final String ACCESS_TOKEN_TYP = "at+jwt";

  private TokenAudience() {
  }
}
