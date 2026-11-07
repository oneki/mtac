package net.oneki.mtac.core.service.openid;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.oidc.OidcCrypto;
import net.oneki.mtac.core.service.openid.oidc.OidcSessionService;
import net.oneki.mtac.framework.repository.ConsoleRefreshTokenRepository;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.ConsoleRefreshToken;

/**
 * Console sessions: one refresh token per login (browser), so that several
 * browsers stay signed in. The token is opaque and never changes (the Console
 * keeps the token it got at login); its expiry slides with every refresh
 * ({@code mtac.jwt.refresh-expiration-sec} of inactivity) up to
 * {@code mtac.jwt.refresh-max-age-sec} after the login. The session id is the
 * {@value #CLAIM} claim of the Console access tokens.
 *
 * <p>A session opened through the OpenID provider (browser session, see
 * {@code mtac.iam.browser-session}) is linked to its SSO session
 * ({@code sso_session_id}): it is usable only while that SSO session is alive,
 * and each renewal touches it. Ending the SSO session closes the linked Console
 * sessions: {@link OidcSessionService} revokes them through
 * {@link ConsoleRefreshTokenRepository#revokeBySsoSession} (it depends on the
 * repository, not on this service, so there is no bean cycle), and
 * {@link #purge()} closes those whose SSO session no longer exists.
 */
@Slf4j
@Service
public class ConsoleSessionService {
  /** Prefix of the Console refresh tokens; the former tokens (encrypted sub:exp:random) never start with it. */
  public static final String TOKEN_PREFIX = "crt_";
  /** Claim of the Console access tokens naming their session (sid is the OpenID SSO session). */
  public static final String CLAIM = "csid";
  /** Revoked tokens are kept that long, for the investigations, before the purge. */
  public static final long KEEP_REVOKED_SEC = 7 * 86400;
  private static final int MAX_USER_AGENT_LENGTH = 512;

  private final ConsoleRefreshTokenRepository repository;
  private final MtacProperties mtacProperties;
  private OidcSessionService oidcSessions; // null: a session linked to an SSO session is never usable
  private Clock clock = Clock.systemUTC();

  @Autowired
  public ConsoleSessionService(ConsoleRefreshTokenRepository repository, MtacProperties mtacProperties) {
    this.repository = repository;
    this.mtacProperties = mtacProperties;
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  /** SSO sessions of the OpenID provider (setter injection: it never depends on this service). */
  @Autowired(required = false)
  public void setOidcSessionService(OidcSessionService oidcSessions) {
    this.oidcSessions = oidcSessions;
  }

  /** A new Console session, its refresh token and its first expiry. */
  public record Issued(String sessionId, String refreshToken, Instant expiresAt) {
    public Issued(String sessionId, String refreshToken) {
      this(sessionId, refreshToken, null);
    }
  }

  /** Opens a Console session for a user who just signed in (password, then TOTP when required). */
  public Issued open(Integer identityId) {
    return open(identityId, null);
  }

  /**
   * Opens a Console session for a user signed in through the OpenID provider:
   * the session follows the SSO session {@code ssoSessionId} (null: none).
   */
  public Issued open(Integer identityId, String ssoSessionId) {
    var now = clock.instant();
    var token = TOKEN_PREFIX + OidcCrypto.randomToken();
    var session = ConsoleRefreshToken.builder()
        .id(UUID.randomUUID().toString().replace("-", ""))
        .tokenHash(OidcCrypto.sha256Hex(token))
        .identityId(identityId)
        .createdAt(now)
        .lastUsedAt(now)
        .expiresAt(expiry(now, now))
        .userAgent(userAgent())
        .ssoSessionId(ssoSessionId)
        .build();
    repository.insert(session);
    return new Issued(session.getId(), token, session.getExpiresAt());
  }

  public static boolean isSessionToken(String refreshToken) {
    return refreshToken != null && refreshToken.startsWith(TOKEN_PREFIX);
  }

  /**
   * The live session of a refresh token, without touching it; null when the
   * token is unknown, revoked or expired, or when the SSO session it is linked
   * to has ended (the Console session is then closed). Call {@link #slide} once
   * every other check passed, so that a refused refresh never extends the
   * session.
   */
  public ConsoleRefreshToken find(String refreshToken) {
    var session = lookup(refreshToken);
    if (session == null || session.getRevokedAt() != null || !clock.instant().isBefore(session.getExpiresAt())) {
      return null;
    }
    if (session.getSsoSessionId() != null
        && (oidcSessions == null || oidcSessions.getAlive(session.getSsoSessionId()) == null)) {
      // the user signed out of the OpenID provider, or its SSO session expired
      repository.revoke(session.getId(), clock.instant());
      return null;
    }
    return session;
  }

  /**
   * The stored session of a refresh token whatever its state (revoked, expired),
   * null when the token is unknown. For the logout, which also ends the SSO
   * session of an expired Console session.
   */
  public ConsoleRefreshToken lookup(String refreshToken) {
    if (refreshToken == null) {
      return null;
    }
    return repository.getByHash(OidcCrypto.sha256Hex(refreshToken));
  }

  /**
   * A refresh used the session: the inactivity window starts again, within the
   * maximum age, and the linked SSO session records the activity.
   */
  public void slide(ConsoleRefreshToken session) {
    var now = clock.instant();
    var expiresAt = expiry(session.getCreatedAt(), now);
    repository.slide(session.getId(), now, expiresAt);
    session.setLastUsedAt(now);
    session.setExpiresAt(expiresAt);
    if (session.getSsoSessionId() != null && oidcSessions != null) {
      var ssoSession = oidcSessions.getAlive(session.getSsoSessionId());
      if (ssoSession != null) {
        oidcSessions.touch(ssoSession);
      }
    }
  }

  /** {@link #find} then {@link #slide}: the session of a refresh token, null when it is not usable. */
  public ConsoleRefreshToken refresh(String refreshToken) {
    var session = find(refreshToken);
    if (session != null) {
      slide(session);
    }
    return session;
  }

  /**
   * The user of the session impersonates {@code identityId} until
   * {@code expiresAt} (see {@link BrowserSessionService#startImpersonation}).
   *
   * @return false when the session is unknown or revoked
   */
  public boolean startImpersonation(ConsoleRefreshToken session, Integer identityId, Instant expiresAt) {
    if (repository.startImpersonation(session.getId(), identityId, expiresAt) == 0) {
      return false;
    }
    session.setImpersonatedIdentityId(identityId);
    session.setImpersonationExpiresAt(expiresAt);
    return true;
  }

  /** Ends the impersonation of the session, if any (exit, expiry). */
  public void stopImpersonation(ConsoleRefreshToken session) {
    repository.stopImpersonation(session.getId());
    session.setImpersonatedIdentityId(null);
    session.setImpersonationExpiresAt(null);
  }

  /** Logout: ends this Console session only. */
  public void close(String sessionId) {
    if (sessionId != null) {
      repository.revoke(sessionId, clock.instant());
    }
  }

  /**
   * Ends every Console session of an identity except {@code keepSessionId}
   * (null: all of them).
   *
   * @return the number of sessions ended
   */
  public int closeAll(Integer identityId, String keepSessionId) {
    return repository.revokeOfIdentity(identityId, keepSessionId, clock.instant());
  }

  /** Ends the Console sessions linked to an SSO session that just ended. */
  public int closeBySsoSession(String ssoSessionId) {
    return ssoSessionId != null ? repository.revokeBySsoSession(ssoSessionId, clock.instant()) : 0;
  }

  /**
   * Closes the Console sessions whose SSO session no longer exists (whatever
   * deleted it: expiry purge, revocation), then deletes the expired sessions and
   * those revoked more than a week ago.
   */
  public int purge() {
    var now = clock.instant();
    repository.revokeOrphanedSsoSessions(now);
    return repository.purge(now, now.minusSeconds(KEEP_REVOKED_SEC));
  }

  /** Inactivity window from now, never beyond the maximum age counted from the login. */
  Instant expiry(Instant createdAt, Instant now) {
    var jwt = mtacProperties.getJwt();
    var idle = now.plusSeconds(jwt.getRefreshExpirationSec());
    if (jwt.getRefreshMaxAgeSec() <= 0) {
      return idle;
    }
    var max = createdAt.plusSeconds(jwt.getRefreshMaxAgeSec());
    return idle.isBefore(max) ? idle : max;
  }

  private static String userAgent() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
      var value = attributes.getRequest().getHeader("User-Agent");
      if (value != null) {
        return value.length() > MAX_USER_AGENT_LENGTH ? value.substring(0, MAX_USER_AGENT_LENGTH) : value;
      }
    }
    return null;
  }
}
