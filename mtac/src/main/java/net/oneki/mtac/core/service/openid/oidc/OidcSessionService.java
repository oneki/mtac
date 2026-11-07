package net.oneki.mtac.core.service.openid.oidc;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.repository.OidcRepository;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.oidc.OidcSession;
import net.oneki.mtac.model.resource.iam.identity.user.User;

/**
 * Single sign-on sessions of the OpenID provider. The browser holds a random
 * value in a cookie; only its hash is stored.
 */
@Service
@RequiredArgsConstructor
public class OidcSessionService {
  public static final String AMR_PASSWORD = "pwd";
  public static final String AMR_OTP = "otp";

  private final OidcRepository oidcRepository;
  private final MtacProperties mtacProperties;

  /**
   * Opens a session for a fully authenticated user.
   *
   * @return the value to put in the session cookie
   */
  public String create(User user, List<String> amr) {
    var now = Instant.now();
    var cookieValue = OidcCrypto.randomToken();
    oidcRepository.insertSession(OidcSession.builder()
        .id(OidcCrypto.sha256Hex(cookieValue))
        .identityId(user.getId())
        .sub(user.getUid())
        .authTime(now)
        .amr(amr)
        .lastSeenAt(now)
        .expiresAt(now.plusSeconds(config().getMaxAgeSec()))
        .build());
    return cookieValue;
  }

  /** Session of the browser, or null if it has none or the session ended. */
  public OidcSession resolve(String cookieValue) {
    if (cookieValue == null || cookieValue.isBlank()) {
      return null;
    }
    return getAlive(OidcCrypto.sha256Hex(cookieValue));
  }

  /** Session by id, or null if it ended (absolute lifetime or inactivity). */
  public OidcSession getAlive(String sessionId) {
    if (sessionId == null) {
      return null;
    }
    var session = oidcRepository.getSession(sessionId);
    if (session == null) {
      return null;
    }
    var now = Instant.now();
    if (!now.isBefore(session.getExpiresAt())
        || !now.isBefore(session.getLastSeenAt().plusSeconds(config().getIdleTimeoutSec()))) {
      oidcRepository.deleteSession(sessionId);
      return null;
    }
    return session;
  }

  /** Records an activity: the inactivity timeout starts again. */
  public void touch(OidcSession session) {
    var now = Instant.now();
    oidcRepository.touchSession(session.getId(), now);
    session.setLastSeenAt(now);
  }

  /** Ends the session of the browser, and with it its codes and refresh tokens. */
  public void end(String cookieValue) {
    if (cookieValue != null && !cookieValue.isBlank()) {
      oidcRepository.deleteSession(OidcCrypto.sha256Hex(cookieValue));
    }
  }

  public void endById(String sessionId) {
    if (sessionId != null) {
      oidcRepository.deleteSession(sessionId);
    }
  }

  /** Deletes the sessions, codes and refresh tokens that can no longer be used. */
  public void deleteExpired() {
    var now = Instant.now();
    oidcRepository.deleteExpired(now, now.minusSeconds(config().getIdleTimeoutSec()), now.minusSeconds(3600));
  }

  private MtacProperties.MtacOidcSession config() {
    return mtacProperties.getOpenId().getServer().getSession();
  }
}
