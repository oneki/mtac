package net.oneki.mtac.core.service.openid.oidc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.ImpersonateService;
import net.oneki.mtac.framework.repository.TokenRevocationRepository;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.oidc.TokenRevocation;
import net.oneki.mtac.model.core.openid.oidc.TokenRevocation.Kind;

/**
 * Revocation list of the access tokens: an access token is a self-contained
 * JWT, checked without any database read, so a long-lived one (1 hour for a
 * mobile application) would outlive a sign-out. The tokens whose {@code sub},
 * {@code sid} or {@code fam} claim is revoked are refused by
 * {@code JwtAuthenticationManager} within about 30 seconds on every instance.
 *
 * <ul>
 * <li>{@link #revokeUser}, {@link #revokeSession} and {@link #revokeFamily}
 * insert a row in {@code token_revocation} and add it to the set of this
 * instance at once (after the commit when a transaction is active);</li>
 * <li>every instance reads the rows written by the others every
 * {@value #REFRESH_INTERVAL_MS} ms with one query on the index of
 * {@code revoked_at} ({@link #refresh()}, in-process scheduler: the set
 * belongs to the instance). The query goes back {@link #OVERLAP} before the
 * newest row already read, so that a row committed late (inside a longer
 * transaction) or by an instance whose clock lags is not missed; the read
 * point never goes beyond the clock of this instance, so a row stamped in the
 * future by an instance whose clock runs ahead does not make the next reads
 * skip the rows of the other instances;</li>
 * <li>a row expires ({@code expires_at}) when every token it targets has
 * expired: the longest access token lifetime plus the accepted clock skew
 * after the revocation. The same refresh drops the expired entries from memory
 * and deletes them from the table.</li>
 * </ul>
 *
 * <p>
 * A user revocation only refuses the tokens issued until it (claim
 * {@code iat}, within {@link #USER_ISSUED_AT_MARGIN}): the user may sign in
 * again once allowed to. Sessions and families are never reused, so every
 * token that carries a revoked one is refused.
 *
 * <p>
 * The periodic read relies on Spring scheduling: the application must enable
 * it ({@code @EnableScheduling}), otherwise the revocations written by other
 * instances are never read (and the expired ones never purged). The table is
 * required: {@link TokenRevocationTableCheck} refuses to start without it.
 */
@Service
@Slf4j
public class TokenRevocationService {
  public static final long REFRESH_INTERVAL_MS = 30_000;
  /** How far before the newest row already read each refresh reads again. */
  public static final Duration OVERLAP = Duration.ofMinutes(5);
  /** A token issued that long after a user revocation is still refused (clocks of the instances). */
  public static final Duration USER_ISSUED_AT_MARGIN = Duration.ofSeconds(5);

  /** Revocation of this instance: when it was written and until when it applies. */
  private record Entry(Instant revokedAt, Instant expiresAt) {
  }

  private final TokenRevocationRepository repository;
  private final MtacProperties mtacProperties;
  private Clock clock = Clock.systemUTC();
  // key = kind code + ":" + value
  private final Map<String, Entry> entries = new ConcurrentHashMap<>();
  private volatile Instant newestRead; // revoked_at of the newest row read, null before the first refresh
  private volatile boolean refreshFailing;

  public TokenRevocationService(TokenRevocationRepository repository, MtacProperties mtacProperties) {
    this.repository = repository;
    this.mtacProperties = mtacProperties;
  }

  /** Clock of the revocations and of the expiry (tests). */
  public void setClock(Clock clock) {
    this.clock = clock;
  }

  // ---------------------------------------------------------------- write

  /**
   * Refuses every access token of a user issued until now (account disabled or
   * deleted).
   *
   * @param sub    uid of the user
   * @param reason logged
   */
  public void revokeUser(String sub, String reason) {
    revoke(Kind.USER, List.of(sub), reason);
  }

  /** Refuses every access token issued in these SSO sessions ({@code sid}). */
  public void revokeSession(String sessionId, String reason) {
    revokeSessions(List.of(sessionId), reason);
  }

  public void revokeSessions(Collection<String> sessionIds, String reason) {
    revoke(Kind.SESSION, sessionIds, reason);
  }

  /**
   * Refuses every access token of a refresh token family ({@code fam}): one
   * device or connector, the other sessions of the user stay.
   *
   * @param familyId family id of the refresh tokens
   *                 ({@code oidc_refresh_token.family_id}), not the claim
   */
  public void revokeFamily(String familyId, String reason) {
    revokeFamilies(List.of(familyId), reason);
  }

  public void revokeFamilies(Collection<String> familyIds, String reason) {
    revoke(Kind.FAMILY, familyIds.stream().map(OidcCrypto::familyClaim).toList(), reason);
  }

  private void revoke(Kind kind, Collection<String> values, String reason) {
    var now = clock.instant();
    var expiresAt = now.plus(entryLifetime());
    var revocations = new ArrayList<TokenRevocation>();
    for (var value : new LinkedHashSet<>(values)) {
      if (value == null || value.isBlank()) {
        continue;
      }
      // a session or a family is never reused: revoked once is enough (a code replayed in a loop writes one row).
      // A user revocation is written again: it then refuses the tokens issued since the previous one
      if (kind != Kind.USER && isActive(entries.get(key(kind, value)), now)) {
        continue;
      }
      revocations.add(new TokenRevocation(kind, value, now, expiresAt));
    }
    if (revocations.isEmpty()) {
      return;
    }
    repository.insertAll(revocations);
    log.info("Access tokens revoked: {} {}(s), reason {}, refused until {}", revocations.size(), kind.code(),
        reason, expiresAt);
    // after the commit: a rolled back revocation must not refuse tokens on this instance
    afterCommit(() -> revocations.forEach(this::add));
  }

  // ---------------------------------------------------------------- check

  /**
   * The revoked claim of an access token, or null when it is accepted.
   *
   * @param sub      {@code sub} claim
   * @param sid      {@code sid} claim, null when none
   * @param fam      {@code fam} claim, null when none
   * @param issuedAt {@code iat} claim, null when none (then a revoked user
   *                 refuses it)
   */
  public Kind revokedClaim(String sub, String sid, String fam, Instant issuedAt) {
    if (entries.isEmpty()) {
      return null;
    }
    var now = clock.instant();
    if (fam != null && isActive(entries.get(key(Kind.FAMILY, fam)), now)) {
      return Kind.FAMILY;
    }
    if (sid != null && isActive(entries.get(key(Kind.SESSION, sid)), now)) {
      return Kind.SESSION;
    }
    if (sub != null) {
      var entry = entries.get(key(Kind.USER, sub));
      if (isActive(entry, now) && (issuedAt == null
          || !issuedAt.isAfter(entry.revokedAt().truncatedTo(ChronoUnit.SECONDS).plus(USER_ISSUED_AT_MARGIN)))) {
        return Kind.USER;
      }
    }
    return null;
  }

  private static boolean isActive(Entry entry, Instant now) {
    return entry != null && now.isBefore(entry.expiresAt());
  }

  // -------------------------------------------------------------- refresh

  /**
   * Reads the revocations written since the last refresh (by any instance),
   * drops the expired ones from memory and deletes them from the table. Never
   * throws: a failure is logged, and the next refresh reads from the same
   * point.
   */
  @Scheduled(fixedDelay = REFRESH_INTERVAL_MS, initialDelay = 0)
  public synchronized void refresh() {
    var now = clock.instant();
    try {
      // min(newest row read, now): a row stamped ahead of this clock must not move the read point past now
      var since = newestRead != null ? earliest(newestRead, now).minus(OVERLAP) : Instant.EPOCH;
      var newest = newestRead;
      for (var revocation : repository.listSince(since, now)) {
        add(revocation);
        if (newest == null || revocation.revokedAt().isAfter(newest)) {
          newest = revocation.revokedAt();
        }
      }
      // first refresh without any row: later rows are newer than now, minus the overlap for late commits
      newestRead = newest != null ? newest : now;
      entries.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
      repository.purgeExpired(now);
      if (refreshFailing) {
        refreshFailing = false;
        log.info("The revocation list of the access tokens is read again");
      }
    } catch (RuntimeException e) {
      // the table may not exist yet on a database that did not receive the migration
      if (!refreshFailing) {
        refreshFailing = true;
        log.warn("Unable to read the revocation list of the access tokens: {}", e.getMessage());
      } else {
        log.debug("Unable to read the revocation list of the access tokens: {}", e.getMessage());
      }
    }
  }

  private static Instant earliest(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }

  /**
   * Where the next refresh reads from, before the {@link #OVERLAP}: the newest
   * row read, never beyond the clock of this instance; null before the first
   * refresh (tests).
   */
  public Instant readPoint() {
    var newest = newestRead;
    return newest != null ? earliest(newest, clock.instant()) : null;
  }

  /** Number of revocations in force on this instance (health, tests). */
  public int size() {
    var now = clock.instant();
    return (int) entries.values().stream().filter(entry -> now.isBefore(entry.expiresAt())).count();
  }

  private void add(TokenRevocation revocation) {
    entries.merge(key(revocation.kind(), revocation.value()),
        new Entry(revocation.revokedAt(), revocation.expiresAt()),
        // the latest revocation of a value wins: it refuses more tokens and lives longer
        (previous, next) -> next.revokedAt().isAfter(previous.revokedAt()) ? next : previous);
  }

  private static String key(Kind kind, String value) {
    return kind.code() + ":" + value;
  }

  /**
   * How long a revocation must live: until the last token it may target has
   * expired, i.e. the longest access token lifetime (server, yml clients,
   * Console tokens, impersonation tokens) plus the accepted clock skew. Only
   * yml clients may have their own lifetime: the registry drops it from every
   * other client.
   */
  Duration entryLifetime() {
    var server = mtacProperties.getOpenId().getServer();
    var jwt = mtacProperties.getJwt();
    long longest = Math.max(server.getAccessTokenTtlSec(), jwt.getExpirationSec());
    longest = Math.max(longest, ImpersonateService.IMPERSONATION_SEC);
    for (var client : server.getClients().values()) {
      if (client.getAccessTokenTtlSec() != null) {
        longest = Math.max(longest, client.getAccessTokenTtlSec());
      }
    }
    return Duration.ofSeconds(longest + Math.max(0, jwt.getClockSkewSec()));
  }

  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          action.run();
        }
      });
    } else {
      action.run();
    }
  }
}
