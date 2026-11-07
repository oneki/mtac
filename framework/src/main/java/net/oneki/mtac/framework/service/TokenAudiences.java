package net.oneki.mtac.framework.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;

/**
 * The audiences of {@code mtac.jwt.audiences}. When none is configured, MTAC
 * keeps its former behaviour: access tokens carry no audience and the resource
 * server accepts them.
 */
@Service
@RequiredArgsConstructor
public class TokenAudiences {
  private final MtacProperties mtacProperties;

  public boolean isConfigured() {
    return !mtacProperties.getJwt().getAudiences().isEmpty();
  }

  /** URL of the audience key (see {@link TokenAudience}), or null when not configured. */
  public String url(String key) {
    return mtacProperties.getJwt().getAudiences().get(key);
  }

  /** Key of a configured audience URL. */
  public Optional<String> keyOf(String audience) {
    if (audience == null) {
      return Optional.empty();
    }
    return mtacProperties.getJwt().getAudiences().entrySet().stream()
        .filter(entry -> audience.equals(entry.getValue()))
        .map(entry -> entry.getKey())
        .findFirst();
  }

  /**
   * A client_id may never be an audience: the id tokens of that client would
   * otherwise be accepted as access tokens.
   */
  public boolean isReserved(String clientId) {
    if (clientId == null) {
      return false;
    }
    return TokenAudience.KEYS.contains(clientId) || keyOf(clientId).isPresent();
  }
}
