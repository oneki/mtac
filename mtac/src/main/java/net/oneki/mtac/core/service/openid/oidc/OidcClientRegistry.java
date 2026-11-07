package net.oneki.mtac.core.service.openid.oidc;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient.ClientType;
import net.oneki.mtac.model.core.openid.oidc.OidcAccessMode;
import net.oneki.mtac.model.core.openid.oidc.OidcClientSource;
import net.oneki.mtac.model.core.openid.oidc.OidcConsentMode;
import net.oneki.mtac.model.core.openid.oidc.OidcException;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.oidc.OidcClient;
import net.oneki.mtac.resource.iam.oidc.OidcClientRepository;

/**
 * The OpenID clients, from three sources, looked up in this order:
 * <ol>
 * <li>yml: {@code mtac.openid.server.clients} (bootstrap clients such as
 * fuzz-docs or local-test);</li>
 * <li>{@link OidcClientResolver} beans, for the client_ids they support (Client
 * ID Metadata Documents, step D4);</li>
 * <li>iam.oidc.client resources (created by a super admin or by Dynamic Client
 * Registration), cached and evicted by {@code OidcClientService} on every
 * change.</li>
 * </ol>
 * Every source is returned as the {@link MtacOidcClient} view, so the rest of the
 * provider does not know where a client comes from.
 *
 * <p>
 * The provider is enabled by {@code mtac.openid.server.enabled}, or as soon as
 * one yml client is declared.
 */
@Service
@Slf4j
public class OidcClientRegistry {
  /** Hosts of a loopback redirect URI, compared as exact strings (RFC 8252 section 7.3). */
  public static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
  /** Bounds how long a change made by another instance can go unnoticed. */
  static final long CACHE_TTL_MS = 5 * 60 * 1000L;
  private static final int MAX_URI_LENGTH = 2048;

  private final MtacProperties mtacProperties;
  private final OidcClientRepository clientRepository; // null: yml clients only
  private final ObjectProvider<OidcClientResolver> resolvers; // null: no resolver

  private final Map<String, CachedClient> cache = new ConcurrentHashMap<>();
  private final AtomicLong generation = new AtomicLong();
  private volatile CachedOrigins resourceOrigins;

  /** Registry of the yml clients only. */
  public OidcClientRegistry(MtacProperties mtacProperties) {
    this(mtacProperties, null, null);
  }

  @Autowired
  public OidcClientRegistry(MtacProperties mtacProperties, OidcClientRepository clientRepository,
      ObjectProvider<OidcClientResolver> resolvers) {
    this.mtacProperties = mtacProperties;
    this.clientRepository = clientRepository;
    this.resolvers = resolvers;
  }

  public boolean isEnabled() {
    var server = mtacProperties.getOpenId().getServer();
    return server.isEnabled() || !server.getClients().isEmpty();
  }

  public MtacOidcClient find(String clientId) {
    if (clientId == null || clientId.isBlank()) {
      return null;
    }
    var ymlClient = ymlClients().get(clientId);
    if (ymlClient != null) {
      return ymlClient;
    }
    var resolver = resolverFor(clientId);
    if (resolver != null) {
      return resolver.resolve(clientId);
    }
    return findResourceClient(clientId);
  }

  /**
   * Like {@link #find(String)}, but a resolver answers only from what it
   * already holds ({@link OidcClientResolver#peek(String)}): no document is
   * fetched. For display only (the consents of a user): never to authorize.
   * yml and resource clients are looked up as usual (no network).
   */
  public MtacOidcClient findCached(String clientId) {
    if (clientId == null || clientId.isBlank()) {
      return null;
    }
    var ymlClient = ymlClients().get(clientId);
    if (ymlClient != null) {
      return ymlClient;
    }
    var resolver = resolverFor(clientId);
    if (resolver != null) {
      return resolver.peek(clientId);
    }
    return findResourceClient(clientId);
  }

  /** True when the client_id is declared in yml: no resource may then use it. */
  public boolean isYmlClient(String clientId) {
    return clientId != null && ymlClients().containsKey(clientId);
  }

  /** Forgets the cached resource client. Called after any create, update or delete. */
  public void evict(String clientId) {
    generation.incrementAndGet();
    if (clientId != null) {
      cache.remove(clientId);
    }
    resourceOrigins = null;
  }

  public void evictAll() {
    generation.incrementAndGet();
    cache.clear();
    resourceOrigins = null;
  }

  /**
   * Redirect URIs are compared as exact strings: no prefix, wildcard or
   * normalisation. A loopback client also accepts a registered
   * {@code http://localhost|127.0.0.1|[::1]} URI on any port (see
   * {@link #matchesLoopback(String, String)}).
   */
  public boolean isRedirectUriRegistered(MtacOidcClient client, String redirectUri) {
    return isRegistered(client, client.getRedirectUris(), redirectUri);
  }

  public boolean isPostLogoutRedirectUriRegistered(MtacOidcClient client, String redirectUri) {
    return isRegistered(client, client.getPostLogoutRedirectUris(), redirectUri);
  }

  private boolean isRegistered(MtacOidcClient client, List<String> registeredUris, String uri) {
    if (uri == null || registeredUris == null) {
      return false;
    }
    if (registeredUris.contains(uri)) {
      return true;
    }
    return client.isLoopback() && registeredUris.stream().anyMatch(registered -> matchesLoopback(registered, uri));
  }

  /**
   * Loopback rule (RFC 8252 section 7.3): both URIs are {@code http}, with the
   * same host, exactly {@code localhost}, {@code 127.0.0.1} or {@code [::1]};
   * the requested port is free; path and query are equal (raw, no
   * normalisation). Userinfo, fragment and anything that does not parse as a
   * plain {@code host[:port]} authority are refused.
   */
  public static boolean matchesLoopback(String registered, String requested) {
    var registeredUri = parseLoopback(registered);
    var requestedUri = parseLoopback(requested);
    if (registeredUri == null || requestedUri == null) {
      return false;
    }
    return registeredUri.getHost().equals(requestedUri.getHost())
        && Objects.equals(registeredUri.getRawPath(), requestedUri.getRawPath())
        && Objects.equals(registeredUri.getRawQuery(), requestedUri.getRawQuery());
  }

  /** The URI when it is a well-formed loopback {@code http} URI, else null. */
  public static URI parseLoopback(String value) {
    if (value == null || value.isEmpty() || value.length() > MAX_URI_LENGTH
        || value.indexOf('#') >= 0 || value.indexOf('@') >= 0) {
      return null;
    }
    URI uri;
    try {
      uri = new URI(value);
    } catch (URISyntaxException e) {
      return null;
    }
    if (uri.isOpaque() || !"http".equals(uri.getScheme())
        || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
      return null;
    }
    var host = uri.getHost();
    if (host == null || !LOOPBACK_HOSTS.contains(host)) {
      return null;
    }
    var port = uri.getPort();
    if (port == 0 || port > 65535) {
      return null;
    }
    // the authority must be exactly host[:port]: nothing URI parsed leniently
    var expectedAuthority = port == -1 ? host : host + ":" + port;
    if (!expectedAuthority.equals(uri.getRawAuthority())) {
      return null;
    }
    return uri;
  }

  /**
   * A public client runs in a browser: when it lists its origins, a call from
   * another origin is refused. Calls without an Origin header (native or server
   * side) are not concerned.
   */
  public boolean isOriginAllowed(MtacOidcClient client, String origin) {
    if (origin == null || client.getType() != ClientType.PUBLIC || client.getAllowedOrigins().isEmpty()) {
      return true;
    }
    return client.getAllowedOrigins().contains(origin);
  }

  /** True if the origin belongs to one of the clients: only those get the CORS headers. */
  public boolean isKnownOrigin(String origin) {
    if (origin == null) {
      return false;
    }
    if (ymlClients().values().stream().anyMatch(client -> client.getAllowedOrigins().contains(origin))) {
      return true;
    }
    if (resourceOrigins().contains(origin)) {
      return true;
    }
    return resolverStream().anyMatch(resolver -> resolver.isKnownOrigin(origin));
  }

  /**
   * Authenticates the client on the token endpoint: a confidential client proves
   * its identity with one of its secrets (two may be valid during a rotation),
   * a public client has none (PKCE protects the exchange).
   */
  public MtacOidcClient authenticate(String clientId, String clientSecret, String origin) {
    var client = find(clientId);
    if (client == null) {
      throw OidcException.invalidClient("Unknown client");
    }
    if (client.getType() == ClientType.CONFIDENTIAL) {
      if (clientSecret == null || clientSecret.isEmpty() || !matchesAnySecret(client, clientSecret)) {
        throw OidcException.invalidClient("Client authentication failed");
      }
    } else if (!isOriginAllowed(client, origin)) {
      throw OidcException.invalidClient("Origin not allowed for this client");
    }
    return client;
  }

  private static boolean matchesAnySecret(MtacOidcClient client, String clientSecret) {
    var hashes = new ArrayList<String>();
    hashes.add(client.getSecretHash());
    if (client.getSecretHashes() != null) {
      hashes.addAll(client.getSecretHashes());
    }
    for (var hash : hashes) {
      if (hash != null && !hash.isBlank() && PasswordUtil.matches(clientSecret, hash)) {
        return true;
      }
    }
    return false;
  }

  /** The {@link MtacOidcClient} view of a resource client. */
  public static MtacOidcClient toClient(OidcClient resource) {
    var client = new MtacOidcClient();
    client.setType(resource.isConfidential() ? ClientType.CONFIDENTIAL : ClientType.PUBLIC);
    client.setName(resource.getName() != null && !resource.getName().isBlank() ? resource.getName()
        : resource.getLabel());
    client.setSecretHash(null);
    var secretHashes = new ArrayList<String>();
    if (resource.isConfidential() && resource.getSecrets() != null) {
      for (var secret : resource.getSecrets()) {
        if (secret != null && secret.getHash() != null && !secret.getHash().isBlank()) {
          secretHashes.add(secret.getHash());
        }
      }
    }
    client.setSecretHashes(secretHashes);
    client.setRedirectUris(copy(resource.getRedirectUris()));
    client.setPostLogoutRedirectUris(copy(resource.getPostLogoutRedirectUris()));
    client.setAllowedOrigins(copy(resource.getAllowedOrigins()));
    client.setScopes(copy(resource.getScopes()));
    client.setRefreshToken(resource.isRefreshToken());
    client.setRequireMfa(resource.isRequireMfa());
    client.setLoopback(resource.isLoopback());
    client.setConsentMode(resource.getConsentMode() != null ? resource.getConsentMode()
        : OidcConsentMode.REMEMBERED);
    client.setAccessMode(resource.getAccessMode() != null ? resource.getAccessMode() : OidcAccessMode.IDENTITY);
    client.setResources(copy(resource.getResources()));
    client.setSessionMaxDays(resource.getSessionMaxDays());
    client.setSource(resource.getSource() != null ? resource.getSource() : OidcClientSource.CONSOLE);
    client.setUid(resource.getUid() != null ? resource.getUid()
        : resource.getId() != null ? Resource.toUid(resource.getId()) : null);
    return client;
  }

  /**
   * Resource lookup, cached. Unknown client_ids are not cached: the cache only
   * grows with the number of existing clients.
   */
  protected MtacOidcClient findResourceClient(String clientId) {
    if (clientRepository == null) {
      return null;
    }
    var now = System.currentTimeMillis();
    var cached = cache.get(clientId);
    if (cached != null && now - cached.loadedAt() < CACHE_TTL_MS) {
      return cached.client();
    }
    var generationBefore = generation.get();
    OidcClient resource;
    try {
      resource = clientRepository.findByClientId(clientId);
    } catch (RuntimeException e) {
      log.warn("Cannot load the OIDC client {}: {}", clientId, e.getMessage());
      return null;
    }
    if (resource == null) {
      cache.remove(clientId);
      return null;
    }
    var client = toClient(resource);
    var entry = new CachedClient(client, now);
    cache.put(clientId, entry);
    if (generation.get() != generationBefore) {
      // a change happened while loading: the value may be stale, do not keep it
      cache.remove(clientId, entry);
    }
    return client;
  }

  private Set<String> resourceOrigins() {
    if (clientRepository == null) {
      return Set.of();
    }
    var now = System.currentTimeMillis();
    var cached = resourceOrigins;
    if (cached != null && now - cached.loadedAt() < CACHE_TTL_MS) {
      return cached.origins();
    }
    var generationBefore = generation.get();
    var origins = new HashSet<String>();
    try {
      for (var client : clientRepository.listAll()) {
        if (client.getAllowedOrigins() != null) {
          origins.addAll(client.getAllowedOrigins());
        }
      }
    } catch (RuntimeException e) {
      log.warn("Cannot load the origins of the OIDC clients: {}", e.getMessage());
      return Set.of();
    }
    var result = Set.copyOf(origins);
    if (generation.get() == generationBefore) {
      resourceOrigins = new CachedOrigins(result, now);
    }
    return result;
  }

  private Map<String, MtacOidcClient> ymlClients() {
    return mtacProperties.getOpenId().getServer().getClients();
  }

  private OidcClientResolver resolverFor(String clientId) {
    return resolverStream().filter(resolver -> resolver.supports(clientId)).findFirst().orElse(null);
  }

  private Stream<OidcClientResolver> resolverStream() {
    return resolvers != null ? resolvers.orderedStream() : Stream.empty();
  }

  private static List<String> copy(List<String> values) {
    return values != null ? new ArrayList<>(values) : new ArrayList<>();
  }

  private record CachedClient(MtacOidcClient client, long loadedAt) {
  }

  private record CachedOrigins(Set<String> origins, long loadedAt) {
  }
}
