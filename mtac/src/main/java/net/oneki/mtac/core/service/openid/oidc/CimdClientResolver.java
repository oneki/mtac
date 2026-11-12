package net.oneki.mtac.core.service.openid.oidc;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.ClientJwks;
import net.oneki.mtac.core.service.openid.PrivateKeyJwtVerifier;
import net.oneki.mtac.core.util.http.SafeHttpFetcher;
import net.oneki.mtac.core.util.http.SafeHttpFetcher.SafeFetchException;
import net.oneki.mtac.framework.service.TokenAudiences;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcCimd;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient.ClientType;
import net.oneki.mtac.model.core.openid.oidc.OidcAccessMode;
import net.oneki.mtac.model.core.openid.oidc.OidcClientSource;
import net.oneki.mtac.model.core.openid.oidc.OidcConsentMode;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client ID Metadata Documents (draft-ietf-oauth-client-id-metadata-document,
 * adopted by the MCP authorization specification): the {@code client_id} is an
 * HTTPS URL and the document it serves describes the client.
 *
 * <p>
 * Active when {@code mtac.openid.server.cimd.enabled}. A client_id is accepted
 * only when:
 * <ul>
 * <li>the URL is {@code https}, its host is one of
 * {@code mtac.openid.server.cimd.allowed-hosts} (exact, case-insensitive
 * compare, no port), it has no user info, query or fragment, its path is not
 * empty nor {@code /} and has no dot segment, and it is at most 2048
 * characters;</li>
 * <li>the document is fetched through {@link SafeHttpFetcher} (public address,
 * no redirect, bounded size and time) and is served as JSON
 * ({@code application/json} or {@code application/*+json});</li>
 * <li>the document is a JSON object (no duplicate member, bounded depth and
 * string length) whose {@code client_id} equals the URL, as an exact string;
 * </li>
 * <li>{@code redirect_uris} is a non-empty array of at most 20 absolute URIs:
 * {@code https} ones (no user info, no fragment), or loopback {@code http}
 * ones ({@code localhost}, {@code 127.0.0.1}, {@code [::1]}), which then match
 * any port (RFC 8252 section 7.3);</li>
 * <li>no {@code client_secret} nor {@code client_secret_expires_at} member
 * (the draft forbids them), and {@code token_endpoint_auth_method} is absent
 * or {@code none} (public client, PKCE only) or {@code private_key_jwt}
 * (ChatGPT). A {@code private_key_jwt} client declares exactly one of
 * {@code jwks} (public keys, same rules as a service account) and
 * {@code jwks_uri} (https, on the host of the client_id), its client_id is at
 * most {@value PrivateKeyJwtVerifier#MAX_CLIENT_ID_LENGTH} characters, and it
 * must then authenticate on the token endpoint with a client assertion signed
 * by one of these keys;</li>
 * <li>{@code grant_types}, when present, holds {@code authorization_code} and
 * nothing beyond {@code refresh_token}; {@code response_types}, when present,
 * is exactly {@code ["code"]}.</li>
 * </ul>
 *
 * <p>
 * The document only says who the client is and where it may be redirected:
 * what it may do is the forced policy {@code mtac.openid.server.cimd.policy}
 * (scopes, resources, refresh token, MFA, session length), with a remembered
 * consent (never trusted) and the API access mode. The scopes, contacts, logo
 * and URIs the document declares are ignored, and so is any
 * {@code post_logout_redirect_uris}.
 *
 * <p>
 * Every failure is logged and answered with null (unknown client); it is
 * remembered for {@link CimdClientCache#NEGATIVE_TTL}. A resolved client is
 * kept for the document's {@code max-age}, bounded to
 * {@code min-cache-sec}..{@code max-cache-sec} ({@code min-cache-sec} when the
 * server gives none or forbids caching). See {@link CimdClientCache}.
 */
@Component
@Order(100)
@Slf4j
public class CimdClientResolver implements OidcClientResolver {
  public static final int MAX_URL_LENGTH = 2048;
  public static final int MAX_REDIRECT_URIS = 20;
  public static final int MAX_NAME_LENGTH = 100;
  private static final String ACCEPT = "application/json";
  private static final String AUTH_NONE = "none";
  private static final String AUTH_PRIVATE_KEY_JWT = "private_key_jwt";
  private static final Set<String> ALLOWED_GRANT_TYPES = Set.of("authorization_code", "refresh_token");

  private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
      .streamReadConstraints(StreamReadConstraints.builder()
          .maxNestingDepth(16)
          .maxStringLength(8 * 1024)
          .maxNameLength(256)
          .maxNumberLength(64)
          .maxDocumentLength(64 * 1024)
          .build())
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      .build())
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
      .build();

  private final MtacProperties mtacProperties;
  private final SafeHttpFetcher fetcher;
  private final CimdClientCache cache;

  @Autowired
  public CimdClientResolver(MtacProperties mtacProperties, SafeHttpFetcher fetcher) {
    this(mtacProperties, fetcher, Clock.systemUTC());
  }

  public CimdClientResolver(MtacProperties mtacProperties, SafeHttpFetcher fetcher, Clock clock) {
    this(mtacProperties, fetcher, new CimdClientCache(clock));
  }

  public CimdClientResolver(MtacProperties mtacProperties, SafeHttpFetcher fetcher, CimdClientCache cache) {
    this.mtacProperties = mtacProperties;
    this.fetcher = fetcher;
    this.cache = cache;
  }

  @Override
  public boolean supports(String clientId) {
    return config().isEnabled() && clientId != null && clientId.startsWith("https://");
  }

  @Override
  public MtacOidcClient resolve(String clientId) {
    if (!supports(clientId)) {
      return null;
    }
    // checked on every call, so that removing an allowed host takes effect at once
    var reason = checkUrl(clientId, config());
    if (reason == null && new TokenAudiences(mtacProperties).isReserved(clientId)) {
      // the id tokens of the client carry aud = client_id: it can never be the URL of a resource server
      reason = "reserved: the URL of a token audience";
    }
    if (reason != null) {
      log.info("CIMD client_id {} refused: {}", printable(clientId), reason);
      return null;
    }
    return cache.get(clientId, this::load);
  }

  /** The client already in the cache, without any fetch (display of the consents). */
  @Override
  public MtacOidcClient peek(String clientId) {
    if (!supports(clientId) || checkUrl(clientId, config()) != null
        || new TokenAudiences(mtacProperties).isReserved(clientId)) {
      return null;
    }
    return cache.peek(clientId);
  }

  /**
   * Always false. The CORS answer of the token endpoint is for browser
   * applications listed by their origins; a CIMD client is a native
   * application (Claude Code) or the back end of a hosted service (claude.ai,
   * ChatGPT) that calls the token endpoint server side, so it needs no CORS
   * header. Saying yes for a document's hosts would also require fetching
   * documents while answering a pre-flight request.
   */
  @Override
  public boolean isKnownOrigin(String origin) {
    return false;
  }

  /** The cache, for tests and eviction. */
  public CimdClientCache cache() {
    return cache;
  }

  // ------------------------------------------------------------------- URL

  /**
   * Why the client_id URL is refused, or null when it is acceptable.
   * Draft section 3: https, a path, no dot segment, no fragment, no user info
   * (and should not have a query: refused here).
   */
  public static String checkUrl(String url, MtacOidcCimd cimd) {
    if (url == null || url.isEmpty()) {
      return "empty";
    }
    if (url.length() > MAX_URL_LENGTH) {
      return "longer than " + MAX_URL_LENGTH + " characters";
    }
    if (url.indexOf('#') >= 0) {
      return "fragment";
    }
    if (url.indexOf('@') >= 0) {
      return "user info";
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      return "not a valid URI";
    }
    if (uri.isOpaque() || !"https".equals(uri.getScheme())) {
      return "not https";
    }
    if (uri.getRawUserInfo() != null) {
      return "user info";
    }
    if (uri.getRawFragment() != null) {
      return "fragment";
    }
    if (uri.getRawQuery() != null) {
      return "query";
    }
    var host = uri.getHost();
    if (host == null || host.isEmpty()) {
      return "no host";
    }
    // the authority must be the host alone: no port, nothing parsed leniently
    if (!host.equals(uri.getRawAuthority())) {
      return "authority other than a host";
    }
    var lowerHost = host.toLowerCase(Locale.ROOT);
    var allowed = cimd.getAllowedHosts() != null && cimd.getAllowedHosts().stream()
        .anyMatch(allowedHost -> allowedHost != null && lowerHost.equals(allowedHost.trim().toLowerCase(Locale.ROOT)));
    if (!allowed) {
      return "host " + lowerHost + " not allowed";
    }
    var path = uri.getRawPath();
    if (path == null || path.isEmpty() || path.equals("/")) {
      return "no path";
    }
    for (var segment : path.split("/", -1)) {
      var decoded = segment.replace("%2e", ".").replace("%2E", ".");
      if (decoded.equals(".") || decoded.equals("..")) {
        return "dot segment";
      }
    }
    return null;
  }

  // ------------------------------------------------------------- loading

  private CimdClientCache.Loaded load(String url) {
    var cimd = config();
    SafeHttpFetcher.Response response;
    try {
      response = fetcher.fetch(url, ACCEPT);
    } catch (SafeFetchException e) {
      log.warn("CIMD document {} cannot be fetched: {}", printable(url), printable(e.getMessage()));
      return CimdClientCache.Loaded.failure();
    } catch (RuntimeException e) {
      log.warn("CIMD document {} cannot be fetched: {}", printable(url), printable(String.valueOf(e)));
      return CimdClientCache.Loaded.failure();
    }
    if (response == null || response.body() == null) {
      log.warn("CIMD document {} refused: empty response", printable(url));
      return CimdClientCache.Loaded.failure();
    }
    if (!isJson(response.contentType())) {
      log.warn("CIMD document {} refused: content type {} is not JSON", printable(url),
          printable(response.contentType()));
      return CimdClientCache.Loaded.failure();
    }
    MtacOidcClient client;
    try {
      client = toClient(url, parse(response.body()), cimd);
    } catch (InvalidDocumentException e) {
      log.warn("CIMD document {} refused: {}", printable(url), printable(e.getMessage()));
      return CimdClientCache.Loaded.failure();
    }
    log.info("CIMD client {} resolved ({} redirect URIs, loopback {})", printable(url),
        client.getRedirectUris().size(), client.isLoopback());
    return new CimdClientCache.Loaded(client, ttl(response.maxAge(), cimd));
  }

  /** max-age bounded to [minCacheSec, maxCacheSec]; minCacheSec when absent. */
  public static Duration ttl(Duration maxAge, MtacOidcCimd cimd) {
    var min = Duration.ofSeconds(Math.max(0, cimd.getMinCacheSec()));
    var max = Duration.ofSeconds(Math.max(cimd.getMinCacheSec(), cimd.getMaxCacheSec()));
    if (maxAge == null || maxAge.compareTo(min) < 0) {
      return min;
    }
    return maxAge.compareTo(max) > 0 ? max : maxAge;
  }

  static boolean isJson(String contentType) {
    if (contentType == null) {
      return false;
    }
    var semicolon = contentType.indexOf(';');
    var mediaType = (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType).trim()
        .toLowerCase(Locale.ROOT);
    if (mediaType.equals("application/json")) {
      return true;
    }
    if (!mediaType.startsWith("application/") || !mediaType.endsWith("+json")) {
      return false;
    }
    var subtype = mediaType.substring("application/".length());
    return subtype.length() > "+json".length() && subtype.chars()
        .allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+' || c == '_');
  }

  private static JsonNode parse(byte[] body) throws InvalidDocumentException {
    JsonNode root;
    try {
      root = MAPPER.readTree(body);
    } catch (JacksonException e) {
      throw new InvalidDocumentException("not valid JSON (" + e.getOriginalMessage() + ")");
    }
    if (root == null || !root.isObject()) {
      throw new InvalidDocumentException("not a JSON object");
    }
    return root;
  }

  /** Validates the document and maps it to the provider view, with the forced policy. */
  public static MtacOidcClient toClient(String url, JsonNode doc, MtacOidcCimd cimd) throws InvalidDocumentException {
    var clientId = doc.get("client_id");
    if (clientId == null || !clientId.isString()) {
      throw new InvalidDocumentException("client_id missing");
    }
    if (!url.equals(clientId.stringValue())) {
      throw new InvalidDocumentException("client_id " + clientId.stringValue() + " differs from the URL");
    }
    if (doc.has("client_secret") || doc.has("client_secret_expires_at")) {
      throw new InvalidDocumentException("a client secret is declared");
    }
    var authMethod = doc.get("token_endpoint_auth_method");
    if (authMethod != null && !(authMethod.isString() && (AUTH_NONE.equals(authMethod.stringValue())
        || AUTH_PRIVATE_KEY_JWT.equals(authMethod.stringValue())))) {
      throw new InvalidDocumentException("token_endpoint_auth_method " + authMethod
          + " not supported (none or private_key_jwt)");
    }
    var keys = authMethod != null && AUTH_PRIVATE_KEY_JWT.equals(authMethod.stringValue()) ? keys(url, doc) : null;

    var grantTypes = stringArray(doc, "grant_types", 10);
    if (grantTypes != null) {
      if (!grantTypes.contains("authorization_code")) {
        throw new InvalidDocumentException("grant_types without authorization_code");
      }
      // Other grant types are ignored, not refused: the document describes the client for every provider (Claude
      // declares urn:ietf:params:oauth:grant-type:jwt-bearer), and the token endpoint only ever accepts
      // authorization_code and refresh_token.
      grantTypes = grantTypes.stream().filter(ALLOWED_GRANT_TYPES::contains).toList();
    }
    var responseTypes = stringArray(doc, "response_types", 10);
    if (responseTypes != null && !responseTypes.equals(List.of("code"))) {
      throw new InvalidDocumentException("response_types " + responseTypes + " not supported (only code)");
    }

    var declared = stringArray(doc, "redirect_uris", MAX_REDIRECT_URIS);
    if (declared == null || declared.isEmpty()) {
      throw new InvalidDocumentException("redirect_uris missing or empty");
    }
    var redirectUris = new LinkedHashSet<String>();
    var loopback = false;
    for (var redirectUri : declared) {
      if (OidcClientRegistry.parseLoopback(redirectUri) != null) {
        loopback = true;
      } else {
        var reason = checkHttpsRedirect(redirectUri);
        if (reason != null) {
          throw new InvalidDocumentException("redirect URI " + redirectUri + " refused: " + reason);
        }
      }
      redirectUris.add(redirectUri);
    }

    var policy = cimd.getPolicy();
    var client = new MtacOidcClient();
    client.setType(keys != null ? ClientType.CONFIDENTIAL : ClientType.PUBLIC);
    client.setName(name(doc.get("client_name"), URI.create(url).getHost().toLowerCase(Locale.ROOT)));
    client.setSecretHash(null);
    client.setSecretHashes(new ArrayList<>());
    client.setJwks(keys != null ? keys.jwks() : null);
    client.setJwksUri(keys != null ? keys.jwksUri() : null);
    client.setRedirectUris(new ArrayList<>(redirectUris));
    client.setPostLogoutRedirectUris(new ArrayList<>());
    client.setAllowedOrigins(new ArrayList<>());
    client.setScopes(copy(policy.getScopes()));
    // a client that declares its grant types without refresh_token gets none
    client.setRefreshToken(policy.isRefreshToken() && (grantTypes == null || grantTypes.contains("refresh_token")));
    client.setRequireMfa(policy.isRequireMfa());
    client.setLoopback(loopback);
    client.setConsentMode(OidcConsentMode.REMEMBERED);
    client.setAccessMode(OidcAccessMode.API);
    client.setResources(copy(policy.getResources()));
    client.setSessionMaxDays(policy.getSessionMaxDays());
    client.setSource(OidcClientSource.CIMD);
    client.setUid(null);
    return client;
  }

  /** The keys of a private_key_jwt document: exactly one of jwks and jwks_uri. */
  private static PrivateKeyJwtVerifier.ClientKeys keys(String url, JsonNode doc) throws InvalidDocumentException {
    if (url.length() > PrivateKeyJwtVerifier.MAX_CLIENT_ID_LENGTH) {
      throw new InvalidDocumentException("private_key_jwt with a client_id longer than "
          + PrivateKeyJwtVerifier.MAX_CLIENT_ID_LENGTH + " characters");
    }
    var signingAlg = doc.get("token_endpoint_auth_signing_alg");
    if (signingAlg != null && !(signingAlg.isString()
        && PrivateKeyJwtVerifier.supportedAlgorithmNames().contains(signingAlg.stringValue()))) {
      throw new InvalidDocumentException("token_endpoint_auth_signing_alg " + signingAlg + " not supported "
          + PrivateKeyJwtVerifier.supportedAlgorithmNames());
    }
    var jwks = doc.get("jwks");
    var jwksUri = doc.get("jwks_uri");
    if ((jwks == null) == (jwksUri == null)) {
      throw new InvalidDocumentException("private_key_jwt needs exactly one of jwks and jwks_uri");
    }
    try {
      if (jwks != null) {
        if (!jwks.isObject()) {
          throw new InvalidDocumentException("jwks is not a JSON object");
        }
        var json = jwks.toString();
        ClientJwks.parse(json);
        return new PrivateKeyJwtVerifier.ClientKeys(url, json, null);
      }
      if (!jwksUri.isString()) {
        throw new InvalidDocumentException("jwks_uri is not a string");
      }
      var value = ClientJwks.validateUri(jwksUri.stringValue());
      // the keys of a client come from the client itself: never from another host the document points to
      var clientHost = URI.create(url).getHost();
      var keysHost = URI.create(value).getHost();
      if (!clientHost.equalsIgnoreCase(keysHost) || URI.create(value).getPort() != -1) {
        throw new InvalidDocumentException("jwks_uri " + value + " is not on the host of the client_id");
      }
      return new PrivateKeyJwtVerifier.ClientKeys(url, null, value);
    } catch (ClientJwks.InvalidJwksException e) {
      throw new InvalidDocumentException(e.getMessage());
    }
  }

  /** Why an https redirect URI is refused, or null. */
  static String checkHttpsRedirect(String value) {
    if (value.length() > MAX_URL_LENGTH) {
      return "too long";
    }
    if (value.indexOf('#') >= 0) {
      return "fragment";
    }
    URI uri;
    try {
      uri = new URI(value);
    } catch (URISyntaxException e) {
      return "not a valid URI";
    }
    if (!uri.isAbsolute() || uri.isOpaque()) {
      return "not absolute";
    }
    if (!"https".equals(uri.getScheme())) {
      return "neither https nor loopback http";
    }
    if (uri.getRawUserInfo() != null || value.indexOf('@') >= 0) {
      return "user info";
    }
    var host = uri.getHost();
    if (host == null || host.isEmpty()) {
      return "no host";
    }
    var port = uri.getPort();
    var expectedAuthority = port == -1 ? host : host + ":" + port;
    if (!expectedAuthority.equals(uri.getRawAuthority())) {
      return "authority other than host[:port]";
    }
    return null;
  }

  /** The array of strings of a member, null when absent; refused when it is something else. */
  private static List<String> stringArray(JsonNode doc, String member, int maxItems) throws InvalidDocumentException {
    var node = doc.get(member);
    if (node == null) {
      return null;
    }
    if (!node.isArray()) {
      throw new InvalidDocumentException(member + " is not an array");
    }
    if (node.size() > maxItems) {
      throw new InvalidDocumentException(member + " has more than " + maxItems + " items");
    }
    var values = new ArrayList<String>();
    for (var item : node.values()) {
      if (!item.isString() || item.stringValue().isEmpty()) {
        throw new InvalidDocumentException(member + " holds something other than a string");
      }
      values.add(item.stringValue());
    }
    return values;
  }

  /**
   * client_name without control or format characters (bidi overrides, zero
   * width), white space collapsed, at most {@link #MAX_NAME_LENGTH} code
   * points; the host when nothing is left.
   */
  public static String name(JsonNode clientName, String fallback) {
    if (clientName == null || !clientName.isString()) {
      return fallback;
    }
    var out = new StringBuilder();
    var count = 0;
    var pendingSpace = false;
    for (var it = clientName.stringValue().codePoints().iterator(); it.hasNext() && count < MAX_NAME_LENGTH;) {
      int cp = it.nextInt();
      var type = Character.getType(cp);
      if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
        pendingSpace = out.length() > 0;
        continue;
      }
      if (Character.isISOControl(cp) || type == Character.FORMAT || type == Character.PRIVATE_USE
          || type == Character.SURROGATE || type == Character.UNASSIGNED) {
        continue;
      }
      if (pendingSpace) {
        out.append(' ');
        count++;
        pendingSpace = false;
        if (count >= MAX_NAME_LENGTH) {
          break;
        }
      }
      out.appendCodePoint(cp);
      count++;
    }
    var name = out.toString().strip();
    return name.isEmpty() ? fallback : name;
  }

  private MtacOidcCimd config() {
    return mtacProperties.getOpenId().getServer().getCimd();
  }

  private static List<String> copy(List<String> values) {
    return values != null ? new ArrayList<>(values) : new ArrayList<>();
  }

  /** A value from the network or a request, safe for one log line. */
  static String printable(String value) {
    if (value == null) {
      return "null";
    }
    var cut = value.length() > 200 ? value.substring(0, 200) + "..." : value;
    var out = new StringBuilder(cut.length());
    for (var i = 0; i < cut.length(); i++) {
      var c = cut.charAt(i);
      out.append(Character.isISOControl(c) ? '?' : c);
    }
    return out.toString();
  }

  /** The document breaks a rule. */
  public static class InvalidDocumentException extends Exception {
    public InvalidDocumentException(String message) {
      super(message);
    }
  }
}
