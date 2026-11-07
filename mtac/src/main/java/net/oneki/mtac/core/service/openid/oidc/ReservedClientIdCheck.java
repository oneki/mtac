package net.oneki.mtac.core.service.openid.oidc;

import java.util.ArrayList;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.service.TokenAudiences;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;

/**
 * Startup check: no OpenID client declared in yml
 * ({@code mtac.openid.server.clients}) may use a reserved client_id, i.e. an
 * audience key or a configured audience URL. Otherwise the ID tokens of that
 * client (aud = client_id) would be accepted as access tokens. The application
 * does not start until the client is renamed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservedClientIdCheck {
  private final MtacProperties mtacProperties;
  private final TokenAudiences tokenAudiences;

  @PostConstruct
  public void check() {
    var reserved = new ArrayList<String>();
    for (var clientId : mtacProperties.getOpenId().getServer().getClients().keySet()) {
      if (tokenAudiences.isReserved(clientId)) {
        reserved.add(clientId);
      }
    }
    if (!reserved.isEmpty()) {
      throw new IllegalStateException("mtac.openid.server.clients: the client_id " + String.join(", ", reserved)
          + " is reserved (an access token audience, see mtac.jwt.audiences); rename the client");
    }
    var audiences = mtacProperties.getJwt().getAudiences();
    if (!audiences.isEmpty()) {
      // a missing key would turn every login or token request needing it into a 500
      var missing = TokenAudience.KEYS.stream().filter(key -> audiences.get(key) == null || audiences.get(key).isBlank())
          .toList();
      if (!missing.isEmpty()) {
        throw new IllegalStateException("mtac.jwt.audiences: missing " + String.join(", ", missing)
            + " (configure every audience " + TokenAudience.KEYS + ", or none)");
      }
    }
    for (var key : audiences.keySet()) {
      if (!TokenAudience.KEYS.contains(key)) {
        log.warn("mtac.jwt.audiences.{} is not a known audience key {}: no path requires it", key,
            TokenAudience.KEYS);
      }
    }
  }
}
