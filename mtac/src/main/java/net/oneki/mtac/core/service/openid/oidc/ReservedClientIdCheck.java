package net.oneki.mtac.core.service.openid.oidc;

import java.util.ArrayList;
import java.util.Map;

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
 *
 * <p>
 * It also checks the {@code first-party-audiences} of the yml clients: only
 * the keys of {@link OidcAuthorizationService#FIRST_PARTY_AUDIENCES} (mobile),
 * and only when {@code mtac.jwt.audiences} configures them.
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
      var missing = TokenAudience.REQUIRED_KEYS.stream()
          .filter(key -> audiences.get(key) == null || audiences.get(key).isBlank())
          .toList();
      if (!missing.isEmpty()) {
        throw new IllegalStateException("mtac.jwt.audiences: missing " + String.join(", ", missing)
            + " (configure every audience " + TokenAudience.REQUIRED_KEYS + ", or none)");
      }
    }
    checkFirstPartyAudiences(audiences);
    for (var key : audiences.keySet()) {
      if (!TokenAudience.KEYS.contains(key)) {
        log.warn("mtac.jwt.audiences.{} is not a known audience key {}: no path requires it", key,
            TokenAudience.KEYS);
      }
    }
  }

  private void checkFirstPartyAudiences(Map<String, String> audiences) {
    for (var entry : mtacProperties.getOpenId().getServer().getClients().entrySet()) {
      var firstParty = entry.getValue().getFirstPartyAudiences();
      if (firstParty == null) {
        continue;
      }
      for (var key : firstParty) {
        if (!OidcAuthorizationService.FIRST_PARTY_AUDIENCES.contains(key)) {
          throw new IllegalStateException("mtac.openid.server.clients." + entry.getKey()
              + ".first-party-audiences: " + key + " is not a first-party audience "
              + OidcAuthorizationService.FIRST_PARTY_AUDIENCES);
        }
        if (audiences.get(key) == null || audiences.get(key).isBlank()) {
          throw new IllegalStateException("mtac.openid.server.clients." + entry.getKey()
              + ".first-party-audiences: " + key + " needs mtac.jwt.audiences." + key);
        }
      }
    }
  }
}
