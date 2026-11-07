package net.oneki.mtac.core.service.openid.oidc;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient.ClientType;
import net.oneki.mtac.model.core.openid.oidc.OidcException;

/**
 * The OpenID clients, declared under {@code mtac.openid.server.clients}. The
 * provider is enabled as soon as one client is declared.
 */
@Service
@RequiredArgsConstructor
public class OidcClientRegistry {
  private final MtacProperties mtacProperties;

  public boolean isEnabled() {
    return !mtacProperties.getOpenId().getServer().getClients().isEmpty();
  }

  public MtacOidcClient find(String clientId) {
    if (clientId == null) {
      return null;
    }
    return mtacProperties.getOpenId().getServer().getClients().get(clientId);
  }

  /** Redirect URIs are compared as exact strings: no prefix, wildcard or normalisation. */
  public boolean isRedirectUriRegistered(MtacOidcClient client, String redirectUri) {
    return redirectUri != null && client.getRedirectUris().contains(redirectUri);
  }

  public boolean isPostLogoutRedirectUriRegistered(MtacOidcClient client, String redirectUri) {
    return redirectUri != null && client.getPostLogoutRedirectUris().contains(redirectUri);
  }

  /**
   * A public client runs in a browser: when it lists its origins, a call from
   * another origin is refused. Calls without an Origin header (native or server
   * side) are not concerned.
   */
  public boolean isOriginAllowed(MtacOidcClient client, String origin) {
    if (origin == null || client.getType() != ClientType.PUBLIC || client.getAllowedOrigins().isEmpty()) {
      return true;
    }
    return client.getAllowedOrigins().contains(origin);
  }

  /** True if the origin belongs to one of the clients: only those get the CORS headers. */
  public boolean isKnownOrigin(String origin) {
    if (origin == null) {
      return false;
    }
    return mtacProperties.getOpenId().getServer().getClients().values().stream()
        .anyMatch(client -> client.getAllowedOrigins().contains(origin));
  }

  /**
   * Authenticates the client on the token endpoint: a confidential client proves
   * its identity with its secret, a public client has none (PKCE protects the
   * exchange).
   */
  public MtacOidcClient authenticate(String clientId, String clientSecret, String origin) {
    var client = find(clientId);
    if (client == null) {
      throw OidcException.invalidClient("Unknown client");
    }
    if (client.getType() == ClientType.CONFIDENTIAL) {
      var secretHash = client.getSecretHash();
      if (secretHash == null || secretHash.isBlank() || clientSecret == null
          || !PasswordUtil.matches(clientSecret, secretHash)) {
        throw OidcException.invalidClient("Client authentication failed");
      }
    } else if (!isOriginAllowed(client, origin)) {
      throw OidcException.invalidClient("Origin not allowed for this client");
    }
    return client;
  }
}
