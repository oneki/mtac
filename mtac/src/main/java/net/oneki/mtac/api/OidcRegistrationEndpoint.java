package net.oneki.mtac.api;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.oidc.OidcClientRegistry;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcDynamic;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcDynamicPolicy;
import net.oneki.mtac.model.core.openid.oidc.OidcAccessMode;
import net.oneki.mtac.model.core.openid.oidc.OidcClientKind;
import net.oneki.mtac.model.core.openid.oidc.OidcConsentMode;
import net.oneki.mtac.model.core.openid.oidc.OidcException;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.iam.oidc.OidcClient;
import net.oneki.mtac.resource.iam.oidc.OidcClientService;
import net.oneki.mtac.resource.iam.oidc.OidcClientValidator;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dynamic Client Registration (RFC 7591), {@code POST <basePath>/oauth2/register},
 * registered by {@link OidcEndpoints}. It answers 404 unless
 * {@code mtac.openid.server.dynamic.dcr-enabled}.
 *
 * <p>
 * Anyone may call it, so nothing the caller sends is trusted:
 * <ul>
 * <li>only public clients ({@code token_endpoint_auth_method = none}), grant
 * {@code authorization_code} (+ {@code refresh_token}), response type
 * {@code code};</li>
 * <li>each redirect URI is either a loopback {@code http} URI (localhost,
 * 127.0.0.1, [::1], any port at authorization time, RFC 8252) or an
 * {@code https} URI whose host is exactly one of
 * {@code dynamic.allowed-redirect-hosts} (case-insensitive, no wildcard,
 * default port only);</li>
 * <li>the policy of {@code dynamic.policy} is forced (consent remembered, API
 * access, resources, refresh token, MFA, session length); the requested scope
 * can only narrow the policy scopes, and {@code openid} is always kept;</li>
 * <li>{@code client_name} is sanitised (control and formatting characters
 * removed, at most {@value OidcClientValidator#MAX_NAME_LENGTH} characters);
 * {@code client_uri}, {@code logo_uri}, {@code contacts}, {@code tos_uri},
 * {@code policy_uri}, {@code software_statement} and any other metadata are
 * ignored: they would be phishing vectors on the consent page;</li>
 * <li>a body of at most {@value #MAX_BODY_BYTES} bytes, and at most
 * {@code dynamic.registrations-per-ip-per-hour} calls per client address and
 * hour (fixed window, in memory, per instance; IPv6 addresses are counted per
 * /64).</li>
 * </ul>
 * The client address is the {@code X-Real-IP} header set by the reverse proxy
 * (like the hosted login pages), else the remote address of the connection.
 *
 * <p>
 * Errors follow RFC 7591 section 3.2.2: 400 with {@code invalid_redirect_uri}
 * or {@code invalid_client_metadata}; 413 for a too large body, 415 for a
 * content type other than {@code application/json}, 429 with
 * {@code Retry-After} when the address is rate limited.
 */
@Component
@Slf4j
public class OidcRegistrationEndpoint {
  public static final String PATH_REGISTER = "/oauth2/register";
  public static final int MAX_BODY_BYTES = 16 * 1024;
  public static final int MAX_REDIRECT_URIS = 10;
  public static final String DEFAULT_CLIENT_NAME = "Dynamic client";
  public static final String AUTH_METHOD_NONE = "none";
  public static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
  public static final String GRANT_REFRESH_TOKEN = "refresh_token";
  public static final String RESPONSE_TYPE_CODE = "code";
  static final String INVALID_REDIRECT_URI = "invalid_redirect_uri";
  static final String INVALID_CLIENT_METADATA = "invalid_client_metadata";
  /** Addresses tracked by the rate limiter; beyond that, unknown addresses are refused until windows expire. */
  static final int MAX_TRACKED_ADDRESSES = 10_000;
  private static final Duration RATE_WINDOW = Duration.ofHours(1);
  private static final Set<String> ALLOWED_GRANT_TYPES = Set.of(GRANT_AUTHORIZATION_CODE, GRANT_REFRESH_TOKEN);
  private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private static final JsonMapper JSON = JsonMapper.builder()
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
      .build();

  private final MtacProperties mtacProperties;
  private final OidcClientService clientService;
  private final Clock clock;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  private record Window(Instant start, int count) {
  }

  /** Metadata of a registration request once validated. */
  private record Registration(List<String> redirectUris, boolean allLoopback, boolean anyLoopback, String name,
      List<String> scopes, boolean refreshGrant) {
  }

  @Autowired
  public OidcRegistrationEndpoint(MtacProperties mtacProperties, OidcClientService clientService) {
    this(mtacProperties, clientService, Clock.systemUTC());
  }

  public OidcRegistrationEndpoint(MtacProperties mtacProperties, OidcClientService clientService, Clock clock) {
    this.mtacProperties = mtacProperties;
    this.clientService = clientService;
    this.clock = clock;
  }

  public ResponseEntity<Map<String, Object>> register(HttpServletRequest request) {
    var config = dynamicConfig();
    if (config == null || !config.isDcrEnabled()) {
      return ResponseEntity.notFound().build();
    }

    var address = clientAddress(request);
    var retryAfter = acquire(rateKey(address), config.getRegistrationsPerIpPerHour());
    if (retryAfter > 0) {
      log.warn("Dynamic client registration rate limited for {}", address);
      var headers = noStoreHeaders();
      headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers)
          .body(errorBody("temporarily_unavailable", "Too many registrations from this address, try again later"));
    }

    try {
      var metadata = readBody(request);
      var registration = parse(metadata, config);
      var client = buildClient(registration, config.getPolicy());
      var created = clientService.createDynamicUnsecure(client);
      log.info("Dynamic client {} registered from {} ({} redirect URI(s), kind {})", created.getLabel(), address,
          created.getRedirectUris().size(), created.getKind());
      return ResponseEntity.status(HttpStatus.CREATED).headers(noStoreHeaders())
          .contentType(MediaType.APPLICATION_JSON).body(response(created));
    } catch (OidcException e) {
      log.info("Dynamic client registration refused for {}: {} {}", address, e.getError(), e.getMessage());
      return error(HttpStatus.valueOf(e.getStatus()), e.getError(), e.getMessage());
    } catch (BusinessException e) {
      // a rule of OidcClientValidator: the request asked for something the policy does not allow
      log.info("Dynamic client registration refused for {}: {} {}", address, e.getErrorCode(), e.getMessage());
      var code = e.getErrorCode() != null && e.getErrorCode().contains("REDIRECT_URI") ? INVALID_REDIRECT_URI
          : INVALID_CLIENT_METADATA;
      return error(HttpStatus.BAD_REQUEST, code, e.getMessage());
    } catch (RuntimeException e) {
      log.error("Dynamic client registration failed for {}", address, e);
      return error(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", "The client could not be registered");
    }
  }

  // ------------------------------------------------------------ request

  private JsonNode readBody(HttpServletRequest request) {
    var contentType = request.getContentType();
    if (contentType == null || contentType.isBlank()) {
      throw new OidcException("invalid_request", "Content-Type must be application/json", 415);
    }
    MediaType mediaType;
    try {
      mediaType = MediaType.parseMediaType(contentType);
    } catch (InvalidMediaTypeException e) {
      throw new OidcException("invalid_request", "Content-Type must be application/json", 415);
    }
    var charset = mediaType.getParameter("charset");
    if (!MediaType.APPLICATION_JSON.equalsTypeAndSubtype(mediaType)
        || (charset != null && !"utf-8".equalsIgnoreCase(charset.replace("\"", "")))) {
      throw new OidcException("invalid_request", "Content-Type must be application/json (UTF-8)", 415);
    }
    if (request.getContentLengthLong() > MAX_BODY_BYTES) {
      throw tooLarge();
    }
    byte[] body;
    try (var input = request.getInputStream()) {
      body = input.readNBytes(MAX_BODY_BYTES + 1);
    } catch (IOException e) {
      throw new OidcException(INVALID_CLIENT_METADATA, "The request body cannot be read");
    }
    if (body.length > MAX_BODY_BYTES) {
      throw tooLarge();
    }
    JsonNode node;
    try {
      node = JSON.readTree(new String(body, StandardCharsets.UTF_8));
    } catch (JacksonException e) {
      throw new OidcException(INVALID_CLIENT_METADATA, "The request body is not valid JSON");
    }
    if (node == null || !node.isObject()) {
      throw new OidcException(INVALID_CLIENT_METADATA, "The request body must be a JSON object");
    }
    return node;
  }

  private static OidcException tooLarge() {
    return new OidcException(INVALID_CLIENT_METADATA,
        "The request body cannot be larger than " + MAX_BODY_BYTES + " bytes", 413);
  }

  private Registration parse(JsonNode metadata, MtacOidcDynamic config) {
    // public clients only
    var authMethod = optionalString(metadata, "token_endpoint_auth_method");
    if (authMethod != null && !AUTH_METHOD_NONE.equals(authMethod)) {
      throw new OidcException(INVALID_CLIENT_METADATA, "Only public clients (token_endpoint_auth_method none) "
          + "can register");
    }

    var grantTypes = optionalStrings(metadata, "grant_types");
    if (grantTypes != null) {
      for (var grantType : grantTypes) {
        if (!ALLOWED_GRANT_TYPES.contains(grantType)) {
          throw new OidcException(INVALID_CLIENT_METADATA, "Unsupported grant type " + grantType);
        }
      }
      if (!grantTypes.contains(GRANT_AUTHORIZATION_CODE)) {
        throw new OidcException(INVALID_CLIENT_METADATA, "The grant type authorization_code is required");
      }
    }

    var responseTypes = optionalStrings(metadata, "response_types");
    if (responseTypes != null && (responseTypes.isEmpty()
        || responseTypes.stream().anyMatch(type -> !RESPONSE_TYPE_CODE.equals(type)))) {
      throw new OidcException(INVALID_CLIENT_METADATA, "Only the response type code is supported");
    }

    var allowedHosts = new LinkedHashSet<String>();
    if (config.getAllowedRedirectHosts() != null) {
      for (var host : config.getAllowedRedirectHosts()) {
        if (host != null && !host.isBlank()) {
          allowedHosts.add(host.trim().toLowerCase(Locale.ROOT));
        }
      }
    }
    var redirects = metadata.get("redirect_uris");
    if (redirects == null || redirects.isNull() || !redirects.isArray() || redirects.size() == 0) {
      throw new OidcException(INVALID_REDIRECT_URI, "At least one redirect URI is required");
    }
    if (redirects.size() > MAX_REDIRECT_URIS) {
      throw new OidcException(INVALID_REDIRECT_URI, "No more than " + MAX_REDIRECT_URIS + " redirect URIs");
    }
    var uris = new LinkedHashSet<String>();
    var loopbackCount = 0;
    for (var item : redirects.values()) {
      if (item == null || !item.isString()) {
        throw new OidcException(INVALID_REDIRECT_URI, "A redirect URI must be a string");
      }
      var value = item.stringValue();
      var loopback = OidcClientRegistry.parseLoopback(value);
      uris.add(loopback != null ? canonicalLoopback(value, loopback) : checkHttps(value, allowedHosts));
      if (loopback != null) {
        loopbackCount++;
      }
    }

    return new Registration(new ArrayList<>(uris), loopbackCount == redirects.size(), loopbackCount > 0,
        sanitizeName(optionalString(metadata, "client_name")),
        scopes(optionalString(metadata, "scope"), config.getPolicy()),
        // grant_types absent: refresh allowed as for metadata documents; present: only if it lists refresh_token
        grantTypes == null || grantTypes.contains(GRANT_REFRESH_TOKEN));
  }

  /**
   * A loopback URI in the form {@link OidcClientRegistry#matchesLoopback} compares:
   * lower-case {@code http}, exact loopback host, the port kept as registered
   * (any port is accepted at authorization time), raw path and query.
   */
  private static String canonicalLoopback(String value, URI uri) {
    var reason = OidcClientValidator.checkRedirectUri(value, true);
    if (reason != null) {
      throw new OidcException(INVALID_REDIRECT_URI, "Invalid redirect URI: " + reason);
    }
    var result = new StringBuilder("http://").append(uri.getHost());
    if (uri.getPort() != -1) {
      result.append(':').append(uri.getPort());
    }
    if (uri.getRawPath() != null) {
      result.append(uri.getRawPath());
    }
    if (uri.getRawQuery() != null) {
      result.append('?').append(uri.getRawQuery());
    }
    return result.toString();
  }

  /** An https URI on an allowed host, default port; returned as sent (the registry compares exact strings). */
  private static String checkHttps(String value, Set<String> allowedHosts) {
    var reason = OidcClientValidator.checkRedirectUri(value, false);
    if (reason != null) {
      throw new OidcException(INVALID_REDIRECT_URI, "Invalid redirect URI: " + reason);
    }
    var uri = URI.create(value);
    if (!"https".equals(uri.getScheme())) {
      throw new OidcException(INVALID_REDIRECT_URI, "Invalid redirect URI: the scheme must be https");
    }
    var host = uri.getHost().toLowerCase(Locale.ROOT);
    if (!allowedHosts.contains(host)) {
      throw new OidcException(INVALID_REDIRECT_URI,
          "The redirect host " + host + " is not allowed for dynamic registration");
    }
    if (uri.getPort() != -1 && uri.getPort() != 443) {
      throw new OidcException(INVALID_REDIRECT_URI, "Invalid redirect URI: only the default https port is allowed");
    }
    return value;
  }

  /** The policy scopes, narrowed by the requested ones when given; openid is always kept. */
  private static List<String> scopes(String requested, MtacOidcDynamicPolicy policy) {
    var policyScopes = new LinkedHashSet<String>();
    if (policy.getScopes() != null) {
      policyScopes.addAll(policy.getScopes());
    }
    var result = new LinkedHashSet<String>();
    result.add(OidcClientValidator.SCOPE_OPENID);
    if (requested == null || requested.isBlank()) {
      result.addAll(policyScopes);
    } else {
      var asked = Set.of(WHITESPACE.split(requested.trim()));
      for (var scope : policyScopes) {
        if (asked.contains(scope)) {
          result.add(scope);
        }
      }
    }
    return new ArrayList<>(result);
  }

  /**
   * Removes control, formatting (bidirectional overrides, zero-width...) and
   * separator characters, collapses the whitespace and cuts the name at
   * {@value OidcClientValidator#MAX_NAME_LENGTH} characters.
   */
  static String sanitizeName(String value) {
    if (value == null) {
      return DEFAULT_CLIENT_NAME;
    }
    var builder = new StringBuilder();
    value.codePoints().forEach(codePoint -> {
      var type = Character.getType(codePoint);
      if (Character.isISOControl(codePoint) || type == Character.FORMAT || type == Character.SURROGATE
          || type == Character.PRIVATE_USE || type == Character.UNASSIGNED
          || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
        builder.append(' ');
      } else {
        builder.appendCodePoint(codePoint);
      }
    });
    var name = WHITESPACE.matcher(builder).replaceAll(" ").trim();
    if (name.length() > OidcClientValidator.MAX_NAME_LENGTH) {
      var end = OidcClientValidator.MAX_NAME_LENGTH;
      if (Character.isHighSurrogate(name.charAt(end - 1))) {
        end--;
      }
      name = name.substring(0, end).trim();
    }
    return name.isEmpty() ? DEFAULT_CLIENT_NAME : name;
  }

  private static String optionalString(JsonNode metadata, String field) {
    var node = metadata.get(field);
    if (node == null || node.isNull()) {
      return null;
    }
    if (!node.isString()) {
      throw new OidcException(INVALID_CLIENT_METADATA, field + " must be a string");
    }
    return node.stringValue();
  }

  private static List<String> optionalStrings(JsonNode metadata, String field) {
    var node = metadata.get(field);
    if (node == null || node.isNull()) {
      return null;
    }
    if (!node.isArray()) {
      throw new OidcException(INVALID_CLIENT_METADATA, field + " must be an array of strings");
    }
    var result = new ArrayList<String>();
    for (var item : node.values()) {
      if (item == null || !item.isString()) {
        throw new OidcException(INVALID_CLIENT_METADATA, field + " must be an array of strings");
      }
      result.add(item.stringValue());
    }
    return result;
  }

  // ------------------------------------------------------------ client

  private static OidcClient buildClient(Registration registration, MtacOidcDynamicPolicy policy) {
    var client = new OidcClient();
    client.setName(registration.name());
    client.setKind(registration.allLoopback() ? OidcClientKind.NATIVE : OidcClientKind.AI_CONNECTOR);
    client.setConfidential(false);
    client.setRedirectUris(new ArrayList<>(registration.redirectUris()));
    client.setPostLogoutRedirectUris(new ArrayList<>());
    client.setAllowedOrigins(new ArrayList<>());
    client.setLoopback(registration.anyLoopback());
    client.setScopes(new ArrayList<>(registration.scopes()));
    client.setRefreshToken(policy.isRefreshToken() && registration.refreshGrant());
    client.setRequireMfa(policy.isRequireMfa());
    client.setConsentMode(OidcConsentMode.REMEMBERED);
    client.setAccessMode(OidcAccessMode.API);
    client.setResources(policy.getResources() != null ? new ArrayList<>(policy.getResources()) : new ArrayList<>());
    client.setSessionMaxDays(policy.getSessionMaxDays());
    return client;
  }

  private Map<String, Object> response(OidcClient created) {
    var issuedAt = created.getCreatedAt() != null ? created.getCreatedAt() : clock.instant();
    var grantTypes = new ArrayList<String>(List.of(GRANT_AUTHORIZATION_CODE));
    if (created.isRefreshToken()) {
      grantTypes.add(GRANT_REFRESH_TOKEN);
    }
    var body = new LinkedHashMap<String, Object>();
    body.put("client_id", created.getLabel());
    body.put("client_id_issued_at", issuedAt.getEpochSecond());
    body.put("client_name", created.getName());
    body.put("redirect_uris", new ArrayList<>(created.getRedirectUris()));
    body.put("grant_types", grantTypes);
    body.put("response_types", List.of(RESPONSE_TYPE_CODE));
    body.put("token_endpoint_auth_method", AUTH_METHOD_NONE);
    body.put("scope", String.join(" ", created.getScopes()));
    return body;
  }

  // ------------------------------------------------------------ rate limit

  /**
   * Counts a call of the key. Returns 0 when it is allowed, else the number of
   * seconds before the window of the key ends. A limit of 0 or less disables
   * the rate limit.
   */
  long acquire(String key, int limit) {
    if (limit <= 0) {
      return 0;
    }
    var now = clock.instant();
    if (windows.size() >= MAX_TRACKED_ADDRESSES && !windows.containsKey(key)) {
      windows.values().removeIf(window -> !now.isBefore(window.start().plus(RATE_WINDOW)));
      if (windows.size() >= MAX_TRACKED_ADDRESSES) {
        // too many addresses at once: refuse the new ones rather than forget the counts
        return RATE_WINDOW.toSeconds();
      }
    }
    var refusedFor = new long[] { 0 };
    windows.compute(key, (k, window) -> {
      if (window == null || !now.isBefore(window.start().plus(RATE_WINDOW))) {
        return new Window(now, 1);
      }
      if (window.count() >= limit) {
        refusedFor[0] = Math.max(1, Duration.between(now, window.start().plus(RATE_WINDOW)).toSeconds());
        return window;
      }
      return new Window(window.start(), window.count() + 1);
    });
    return refusedFor[0];
  }

  /** The address an IPv4 client is counted under; an IPv6 client is counted per /64 network. */
  static String rateKey(String address) {
    if (address == null || address.isBlank()) {
      return "unknown";
    }
    var value = address.trim();
    if (value.indexOf(':') >= 0 && IPV6_LITERAL.matcher(value).matches()) {
      try {
        // a literal with ':' is parsed as an IPv6 address, never resolved
        var bytes = InetAddress.getByName(value).getAddress();
        if (bytes.length == 4) {
          return InetAddress.getByAddress(bytes).getHostAddress();
        }
        var prefix = new StringBuilder();
        for (var i = 0; i < 8; i += 2) {
          prefix.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff))).append(':');
        }
        return prefix.append(":/64").toString();
      } catch (UnknownHostException e) {
        return value;
      }
    }
    return value;
  }

  /** Address of the caller: the reverse proxy sets X-Real-IP and overwrites whatever the client sent. */
  static String clientAddress(HttpServletRequest request) {
    var forwarded = request.getHeader("X-Real-IP");
    return forwarded != null && !forwarded.isBlank() ? forwarded.trim() : request.getRemoteAddr();
  }

  // ------------------------------------------------------------ responses

  private MtacOidcDynamic dynamicConfig() {
    var openId = mtacProperties.getOpenId();
    return openId != null && openId.getServer() != null ? openId.getServer().getDynamic() : null;
  }

  private static HttpHeaders noStoreHeaders() {
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.add(HttpHeaders.PRAGMA, "no-cache");
    return headers;
  }

  private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String error, String description) {
    return ResponseEntity.status(status).headers(noStoreHeaders()).contentType(MediaType.APPLICATION_JSON)
        .body(errorBody(error, description));
  }

  private static Map<String, Object> errorBody(String error, String description) {
    var body = new LinkedHashMap<String, Object>();
    body.put("error", error);
    body.put("error_description", description);
    return body;
  }
}
