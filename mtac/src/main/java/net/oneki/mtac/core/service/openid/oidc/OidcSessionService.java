package net.oneki.mtac.core.service.openid.oidc;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.repository.ConsoleRefreshTokenRepository;
import net.oneki.mtac.framework.repository.OidcRepository;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.oidc.OidcSession;
import net.oneki.mtac.model.resource.iam.identity.user.User;

/**
 * Single sign-on sessions of the OpenID provider. The browser holds a random
 * value in a cookie; only its hash is stored.
 *
 * <p>Ending a session (logout, inactivity or lifetime noticed by
 * {@link #getAlive}) also ends the Console sessions opened from it (browser
 * session of the Console, {@code console_refresh_token.sso_session_id}). The
 * repository is used directly, not {@code ConsoleSessionService}, which
 * depends on this service: no bean cycle. The sessions deleted by the expiry
 * purge are caught by {@code ConsoleSessionService.purge()}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OidcSessionService {
  public static final String AMR_PASSWORD = "pwd";
  public static final String AMR_OTP = "otp";

  private final OidcRepository oidcRepository;
  private final MtacProperties mtacProperties;
  private ConsoleRefreshTokenRepository consoleSessions; // null: no Console session follows the SSO sessions
  private TokenRevocationService tokenRevocations; // null: the access tokens of an ended session live until they expire

  @Autowired(required = false)
  public void setConsoleRefreshTokenRepository(ConsoleRefreshTokenRepository consoleSessions) {
    this.consoleSessions = consoleSessions;
  }

  @Autowired(required = false)
  public void setTokenRevocationService(TokenRevocationService tokenRevocations) {
    this.tokenRevocations = tokenRevocations;
  }

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
      closeConsoleSessions(sessionId);
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

  /**
   * The user of the session proved himself again (step-up before a new
   * consent): the authentication time and methods of the same session are
   * updated. The session keeps its id, hence its cookie, codes and refresh
   * tokens; its absolute lifetime does not start again.
   */
  public void reauthenticate(OidcSession session, List<String> amr) {
    var now = Instant.now();
    oidcRepository.updateSessionAuthentication(session.getId(), now, amr);
    session.setAuthTime(now);
    session.setAmr(amr);
    session.setLastSeenAt(now);
  }

  /**
   * Ends the session of the browser, and with it its authorization codes and
   * the Console sessions opened from it. The OpenID refresh tokens of the
   * connectors survive it (their session_id becomes null): they are revoked
   * in Connected applications or by a password change.
   *
   * <p>
   * The access tokens issued in the session (claim {@code sid}) are put in the
   * revocation list ({@link TokenRevocationService}): they are refused within
   * about 30 seconds. A connector then refreshes its tokens, which carry no
   * {@code sid} once the session has ended.
   */
  public void end(String cookieValue) {
    if (cookieValue != null && !cookieValue.isBlank()) {
      endById(OidcCrypto.sha256Hex(cookieValue));
    }
  }

  public void endById(String sessionId) {
    if (sessionId != null) {
      // a session already gone (expired, ended elsewhere) has nothing left to revoke
      var existed = tokenRevocations != null && oidcRepository.getSession(sessionId) != null;
      oidcRepository.deleteSession(sessionId);
      closeConsoleSessions(sessionId);
      if (existed) {
        revokeAccessTokens(sessionId);
      }
    }
  }

  private void revokeAccessTokens(String sessionId) {
    try {
      tokenRevocations.revokeSession(sessionId, "logout");
    } catch (RuntimeException e) {
      log.error("Unable to add an ended SSO session to the revocation list: its access tokens stay valid until "
          + "they expire", e);
    }
  }

  private void closeConsoleSessions(String sessionId) {
    if (consoleSessions != null) {
      consoleSessions.revokeBySsoSession(sessionId, Instant.now());
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
