package net.oneki.mtac.core.service.openid.oidc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;

/**
 * Cache of the clients described by Client ID Metadata Documents, keyed by
 * client_id URL.
 *
 * <ul>
 * <li>A resolved client lives for the TTL its loader gives (the document's
 * {@code Cache-Control: max-age}, bounded by the caller).</li>
 * <li>A failure (fetch error, invalid document) is remembered for
 * {@link #NEGATIVE_TTL}, so a broken or hostile URL is not fetched again on
 * every request.</li>
 * <li>Single flight: concurrent first lookups of one URL share one load; the
 * others wait for its result (at most {@link #WAIT_TIMEOUT}).</li>
 * <li>Bounded: at most {@link #MAX_ENTRIES} resolved clients and as many
 * failures, each set in its own map, so a flood of failing URLs cannot push the
 * working clients out. When a map is full, the oldest entry goes.</li>
 * </ul>
 */
public class CimdClientCache {
  public static final Duration NEGATIVE_TTL = Duration.ofSeconds(60);
  public static final int MAX_ENTRIES = 500;
  /** Longer than a whole fetch (DNS, connect and request timeouts of the fetcher). */
  static final Duration WAIT_TIMEOUT = Duration.ofSeconds(15);

  /** What a load returns: the client, or null when it failed, and how long to keep that answer. */
  public record Loaded(MtacOidcClient client, Duration ttl) {
    public static Loaded failure() {
      return new Loaded(null, NEGATIVE_TTL);
    }
  }

  private record Cached(MtacOidcClient client, Instant expiresAt) {
  }

  private final Clock clock;
  private final int maxEntries;
  private final Map<String, Cached> clients;
  private final Map<String, Cached> failures;
  private final ConcurrentHashMap<String, CompletableFuture<MtacOidcClient>> inFlight = new ConcurrentHashMap<>();

  public CimdClientCache(Clock clock) {
    this(clock, MAX_ENTRIES);
  }

  public CimdClientCache(Clock clock, int maxEntries) {
    this.clock = clock;
    this.maxEntries = maxEntries;
    this.clients = boundedMap(maxEntries);
    this.failures = boundedMap(maxEntries);
  }

  /**
   * The cached answer for the URL when it is still valid, else the result of
   * {@code loader} (run once for all the threads asking at the same time). A
   * loader that throws counts as a failure.
   *
   * @return the client, or null (failure, cached or not)
   */
  public MtacOidcClient get(String url, Function<String, Loaded> loader) {
    var cached = lookup(url);
    if (cached != null) {
      return cached.client();
    }
    var mine = new CompletableFuture<MtacOidcClient>();
    var running = inFlight.putIfAbsent(url, mine);
    if (running != null) {
      return await(running);
    }
    try {
      // another thread may have stored the answer between the lookup and putIfAbsent
      cached = lookup(url);
      if (cached != null) {
        mine.complete(cached.client());
        return cached.client();
      }
      Loaded loaded;
      try {
        loaded = loader.apply(url);
      } catch (RuntimeException e) {
        loaded = null;
      }
      if (loaded == null) {
        loaded = Loaded.failure();
      }
      store(url, loaded);
      mine.complete(loaded.client());
      return loaded.client();
    } catch (RuntimeException | Error e) {
      mine.complete(null);
      throw e;
    } finally {
      inFlight.remove(url, mine);
    }
  }

  /**
   * The resolved client the cache holds for the URL, even when its TTL is over;
   * null otherwise. Never loads anything: for display only.
   */
  public MtacOidcClient peek(String url) {
    synchronized (this) {
      var entry = clients.get(url);
      return entry != null ? entry.client() : null;
    }
  }

  public synchronized void evict(String url) {
    clients.remove(url);
    failures.remove(url);
  }

  public synchronized void clear() {
    clients.clear();
    failures.clear();
  }

  /** Number of resolved clients held (expired ones included until they are replaced or pushed out). */
  public synchronized int size() {
    return clients.size();
  }

  public int maxEntries() {
    return maxEntries;
  }

  private synchronized Cached lookup(String url) {
    var now = clock.instant();
    var entry = clients.get(url);
    if (entry == null) {
      entry = failures.get(url);
    }
    return entry != null && now.isBefore(entry.expiresAt()) ? entry : null;
  }

  private synchronized void store(String url, Loaded loaded) {
    var entry = new Cached(loaded.client(), clock.instant().plus(loaded.ttl()));
    // remove first, so that the entry moves to the young end of the insertion order
    clients.remove(url);
    failures.remove(url);
    if (loaded.client() != null) {
      clients.put(url, entry);
    } else {
      failures.put(url, entry);
    }
  }

  private static MtacOidcClient await(CompletableFuture<MtacOidcClient> running) {
    try {
      return running.get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (ExecutionException | TimeoutException e) {
      return null;
    }
  }

  private static Map<String, Cached> boundedMap(int maxEntries) {
    return new LinkedHashMap<>(16, 0.75f, false) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
        return size() > maxEntries;
      }
    };
  }
}
