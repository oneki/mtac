package net.oneki.mtac.core.service.openid;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.JWKSet;

import net.oneki.mtac.core.util.http.SafeHttpFetcher;
import net.oneki.mtac.core.util.http.SafeHttpFetcher.SafeFetchException;

/**
 * Cache of the JWK Sets that service accounts publish at their {@code jwksUri}.
 * An entry lives as long as the {@code Cache-Control: max-age} of the response,
 * bounded to {@link #MIN_TTL} - {@link #MAX_TTL} ({@link #MIN_TTL} when the
 * server gives none or forbids caching). A refresh asked because an assertion
 * names an unknown {@code kid} is honoured at most once per
 * {@link #MIN_REFRESH_INTERVAL} and URL, so that forged assertions cannot turn
 * the provider into a request amplifier against the client's server.
 */
@Component
public class ClientJwksCache {
  public static final Duration MIN_TTL = Duration.ofMinutes(5);
  public static final Duration MAX_TTL = Duration.ofHours(1);
  public static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(60);
  static final int MAX_ENTRIES = 1000;
  private static final String ACCEPT = "application/jwk-set+json, application/json";

  private final SafeHttpFetcher fetcher;
  private final Clock clock;
  private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

  private record Entry(JWKSet jwkSet, Instant fetchedAt, Instant expiresAt) {
  }

  /** Thrown when the JWK Set cannot be fetched or is invalid. */
  public static class JwksUnavailableException extends Exception {
    public JwksUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @Autowired
  public ClientJwksCache(SafeHttpFetcher fetcher) {
    this(fetcher, Clock.systemUTC());
  }

  public ClientJwksCache(SafeHttpFetcher fetcher, Clock clock) {
    this.fetcher = fetcher;
    this.clock = clock;
  }

  /**
   * @param refresh true when the cached set lacks the key an assertion asks for
   *                (key rotation); ignored when the set was fetched less than
   *                {@link #MIN_REFRESH_INTERVAL} ago
   */
  public JWKSet get(String jwksUri, boolean refresh) throws JwksUnavailableException {
    var now = clock.instant();
    var entry = entries.get(jwksUri);
    if (entry != null) {
      var fresh = now.isBefore(entry.expiresAt());
      var recentlyFetched = now.isBefore(entry.fetchedAt().plus(MIN_REFRESH_INTERVAL));
      if ((fresh && !refresh) || recentlyFetched) {
        return entry.jwkSet();
      }
    }
    var fetched = fetch(jwksUri, now);
    if (entries.size() >= MAX_ENTRIES) {
      entries.values().removeIf(e -> !now.isBefore(e.expiresAt()));
      if (entries.size() >= MAX_ENTRIES) {
        entries.clear();
      }
    }
    entries.put(jwksUri, fetched);
    return fetched.jwkSet();
  }

  /** Forgets a URL, for example when the application changes its keys. */
  public void evict(String jwksUri) {
    entries.remove(jwksUri);
  }

  private Entry fetch(String jwksUri, Instant now) throws JwksUnavailableException {
    try {
      ClientJwks.validateUri(jwksUri);
      var response = fetcher.fetch(jwksUri, ACCEPT);
      var jwkSet = ClientJwks.parse(new String(response.body(), StandardCharsets.UTF_8));
      return new Entry(jwkSet, now, now.plus(ttl(response.maxAge())));
    } catch (SafeFetchException | ClientJwks.InvalidJwksException e) {
      throw new JwksUnavailableException("Unable to load the JWK Set " + jwksUri + ": " + e.getMessage(), e);
    }
  }

  static Duration ttl(Duration maxAge) {
    if (maxAge == null || maxAge.compareTo(MIN_TTL) < 0) {
      return MIN_TTL;
    }
    return maxAge.compareTo(MAX_TTL) > 0 ? MAX_TTL : maxAge;
  }
}
