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
  private Clock clock = Clock.systemUTC();

  @Autowired
  public ConsoleSessionService(ConsoleRefreshTokenRepository repository, MtacProperties mtacProperties) {
    this.repository = repository;
    this.mtacProperties = mtacProperties;
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  /** A new Console session and its refresh token. */
  public record Issued(String sessionId, String refreshToken) {
  }

  /** Opens a Console session for a user who just signed in (password, then TOTP when required). */
  public Issued open(Integer identityId) {
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
        .build();
    repository.insert(session);
    return new Issued(session.getId(), token);
  }

  public static boolean isSessionToken(String refreshToken) {
    return refreshToken != null && refreshToken.startsWith(TOKEN_PREFIX);
  }

  /**
   * The live session of a refresh token, without touching it; null when the
   * token is unknown, revoked or expired. Call {@link #slide} once every other
   * check passed, so that a refused refresh never extends the session.
   */
  public ConsoleRefreshToken find(String refreshToken) {
    var session = repository.getByHash(OidcCrypto.sha256Hex(refreshToken));
    if (session == null || session.getRevokedAt() != null || !clock.instant().isBefore(session.getExpiresAt())) {
      return null;
    }
    return session;
  }

  /** A refresh used the session: the inactivity window starts again, within the maximum age. */
  public void slide(ConsoleRefreshToken session) {
    var now = clock.instant();
    var expiresAt = expiry(session.getCreatedAt(), now);
    repository.slide(session.getId(), now, expiresAt);
    session.setLastUsedAt(now);
    session.setExpiresAt(expiresAt);
  }

  /** {@link #find} then {@link #slide}: the session of a refresh token, null when it is not usable. */
  public ConsoleRefreshToken refresh(String refreshToken) {
    var session = find(refreshToken);
    if (session != null) {
      slide(session);
    }
    return session;
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

  /** Purge of the expired sessions and of those revoked more than a week ago. */
  public int purge() {
    var now = clock.instant();
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
