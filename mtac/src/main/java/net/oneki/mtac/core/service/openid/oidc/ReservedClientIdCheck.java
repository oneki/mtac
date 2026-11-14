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
 * and only when {@code mtac.jwt.audiences} configures them; that the audience
 * URLs are unique (two keys sharing one URL would let a token of one audience
 * pass the paths of the other); and that {@code ignore-sso-session} is only set
 * on a trusted client (the consent page needs the SSO session cookie).
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
    checkUniqueAudienceUrls(audiences);
    checkFirstPartyAudiences(audiences);
    checkIgnoreSsoSession();
    for (var key : audiences.keySet()) {
      if (!TokenAudience.KEYS.contains(key)) {
        log.warn("mtac.jwt.audiences.{} is not a known audience key {}: no path requires it", key,
            TokenAudience.KEYS);
      }
    }
  }

  private static void checkUniqueAudienceUrls(Map<String, String> audiences) {
    var keyByUrl = new java.util.HashMap<String, String>();
    for (var entry : audiences.entrySet()) {
      var url = entry.getValue();
      if (url == null || url.isBlank()) {
        continue;
      }
      var previous = keyByUrl.putIfAbsent(url.trim(), entry.getKey());
      if (previous != null) {
        throw new IllegalStateException("mtac.jwt.audiences: " + previous + " and " + entry.getKey()
            + " share the URL " + url + "; each audience needs its own URL");
      }
    }
  }

  private void checkIgnoreSsoSession() {
    for (var entry : mtacProperties.getOpenId().getServer().getClients().entrySet()) {
      var client = entry.getValue();
      if (client.isIgnoreSsoSession() && !OidcConsentService.isTrusted(client)) {
        throw new IllegalStateException("mtac.openid.server.clients." + entry.getKey()
            + ".ignore-sso-session needs consent-mode: trusted (the consent page relies on the SSO session)");
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
