package net.oneki.mtac.core.service.openid.oidc;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.model.core.config.MtacProperties;

/**
 * Slows down password and verification code guessing on the hosted login pages:
 * after too many consecutive failures a key (login + address, or user) is locked
 * for a while. Kept in memory: a restart clears it.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {
  private static final int MAX_TRACKED_KEYS = 100_000;

  private final MtacProperties mtacProperties;
  private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

  private record Attempts(int failures, Instant lockedUntil, Instant lastFailure) {
  }

  public boolean isLocked(String key) {
    var entry = attempts.get(key);
    if (entry == null || entry.lockedUntil() == null) {
      return false;
    }
    if (Instant.now().isBefore(entry.lockedUntil())) {
      return true;
    }
    attempts.remove(key);
    return false;
  }

  public void recordFailure(String key) {
    var config = mtacProperties.getOpenId().getServer();
    var now = Instant.now();
    if (attempts.size() > MAX_TRACKED_KEYS) {
      var limit = now.minusSeconds(config.getLoginLockSec());
      attempts.values().removeIf(entry -> entry.lastFailure().isBefore(limit));
    }
    attempts.compute(key, (k, entry) -> {
      // failures older than the lock duration are forgotten
      var failures = entry == null || entry.lastFailure().isBefore(now.minusSeconds(config.getLoginLockSec()))
          ? 1
          : entry.failures() + 1;
      var lockedUntil = failures >= config.getLoginMaxAttempts() ? now.plusSeconds(config.getLoginLockSec()) : null;
      return new Attempts(failures, lockedUntil, now);
    });
  }

  public void recordSuccess(String key) {
    attempts.remove(key);
  }
}
