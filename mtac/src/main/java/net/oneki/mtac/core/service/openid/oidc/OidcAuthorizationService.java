package net.oneki.mtac.core.service.openid.oidc;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.api.OidcRegistrationEndpoint;
import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.core.service.openid.PrivateKeyJwtVerifier;
import net.oneki.mtac.core.service.security.DelegatedScopePolicy;
import net.oneki.mtac.framework.repository.OidcRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.framework.service.TokenAudiences;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;
import net.oneki.mtac.model.core.openid.oidc.OidcAccessMode;
import net.oneki.mtac.model.core.openid.oidc.OidcAuthorizationCode;
import net.oneki.mtac.model.core.openid.oidc.OidcAuthorizationRequest;
import net.oneki.mtac.model.core.openid.oidc.OidcException;
import net.oneki.mtac.model.core.openid.oidc.OidcRefreshToken;
import net.oneki.mtac.model.core.openid.oidc.OidcSession;
import net.oneki.mtac.model.core.openid.oidc.OidcTokenResponse;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;
import net.oneki.mtac.resource.iam.oidc.OidcClientRepository;

/**
 * OpenID Connect provider: authorization code flow with PKCE.
 *
 * <p>
 * The same flow serves confidential clients (a backend holding a secret) and
 * public clients (JavaScript in a browser, no secret). The implicit and hybrid
 * flows, which return tokens in the redirect URL, are deliberately not
 * supported.
 *
 * <p>
 * Without a resource indicator, the tokens issued here only identify the user
 * for the client: the access token is bound to the userinfo endpoint and is
 * refused by every resource server path. A client in API access mode may ask
 * one resource (RFC 8707) among its allowed ones (the api or mcp audience,
 * never the Console): its access token then carries that audience, the granted
 * {@code scope} and its {@code client_id} (RFC 9068), and the user's rights
 * are capped by those scopes (see {@link DelegatedScopePolicy}).
 */
@Service
@Slf4j
public class OidcAuthorizationService {
  public static final String SCOPE_OPENID = "openid";
  public static final String REFRESH_TOKEN_PREFIX = "ort_";
  public static final String PROMPT_NONE = "none";
  public static final String PROMPT_LOGIN = "login";
  public static final String SCOPE_OFFLINE_ACCESS = "offline_access";
  public static final String PARAM_RESOURCE = "resource";
  /** RFC 8707 error code of a refused resource indicator. */
  public static final String ERROR_INVALID_TARGET = "invalid_target";
  /** Audiences a client may ask through a resource indicator: never the Console. */
  public static final Set<String> DELEGABLE_AUDIENCES = Set.of(TokenAudience.API, TokenAudience.MCP);
  /** lastUsedAt of a client resource is written at most once per period. */
  public static final Duration LAST_USED_AT_THROTTLE = Duration.ofHours(1);

  public static final String PATH_AUTHORIZE = "/oauth2/authorize";
  public static final String PATH_TOKEN = "/oauth2/token";
  public static final String PATH_USERINFO = "/oauth2/oidc/userinfo";
  public static final String PATH_LOGIN = "/oauth2/login";
  public static final String PATH_MFA = "/oauth2/mfa";
  public static final String PATH_LOGOUT = "/oauth2/logout";
  public static final String PATH_REVOKE = "/oauth2/revoke";
  public static final String PATH_JWKS = "/.well-known/jwks.json";
  public static final String PATH_DISCOVERY = "/.well-known/openid-configuration";

  // RFC 7636: 43 to 128 unreserved characters
  private static final Pattern PKCE_VALUE = Pattern.compile("[A-Za-z0-9\\-._~]{43,128}");

  private final OidcClientRegistry clientRegistry;
  private final OidcSessionService sessionService;
  private final OidcRepository oidcRepository;
  private final JwtTokenService tokenService;
  private final MtacProperties mtacProperties;
  private OpenIdService openIdService;
  private OidcClientRepository clientRepository; // null: lastUsedAt is not tracked
  private Clock clock = Clock.systemUTC(); // lastUsedAt throttling
  // last lastUsedAt write per client resource uid
  private final Map<String, Instant> lastUsedAtWrites = new ConcurrentHashMap<>();

  public OidcAuthorizationService(OidcClientRegistry clientRegistry, OidcSessionService sessionService,
      OidcRepository oidcRepository, JwtTokenService tokenService, MtacProperties mtacProperties) {
    this.clientRegistry = clientRegistry;
    this.sessionService = sessionService;
    this.oidcRepository = oidcRepository;
    this.tokenService = tokenService;
    this.mtacProperties = mtacProperties;
  }

  /**
   * The application can replace the default service by its own (the one its
   * OpenIdController returns), so that the claims come from its user service.
   */
  @Autowired
  public void setOpenIdService(OpenIdService openIdService) {
    this.openIdService = openIdService;
  }

  @Autowired(required = false)
  public void setClientRepository(OidcClientRepository clientRepository) {
    this.clientRepository = clientRepository;
  }

  /** Clock of the lastUsedAt throttling (tests). */
  public void setClock(Clock clock) {
    this.clock = clock;
  }

  /** Issuer, which is also the base URL of every endpoint. */
  public String getIssuer() {
    return mtacProperties.getJwt().getIssuer();
  }

  public String url(String path) {
    return getIssuer() + path;
  }

  // ------------------------------------------------------------ authorize

  /**
   * First validation of an authorization request: the client and its redirect
   * URI. When this fails the user must NOT be redirected, since the redirect URI
   * cannot be trusted: the error is shown by the provider itself.
   */
  public MtacOidcClient validateClient(String clientId, String redirectUri) {
    var client = clientRegistry.find(clientId);
    if (client == null) {
      throw OidcException.invalidRequest("Unknown client_id");
    }
    if (!clientRegistry.isRedirectUriRegistered(client, redirectUri)) {
      throw OidcException.invalidRequest("The redirect_uri is not registered for this client");
    }
    return client;
  }

  /**
   * Second validation, once the redirect URI is trusted: the errors raised here
   * are returned to the client through a redirect.
   *
   * @param params request parameters; a parameter present several times must be
   *               rejected by the caller before
   */
  public OidcAuthorizationRequest validateAuthorizationRequest(MtacOidcClient client, Map<String, String> params) {
    var resource = params.get(PARAM_RESOURCE);
    return validateAuthorizationRequest(client, params, resource != null ? List.of(resource) : null);
  }

  /**
   * Same as {@link #validateAuthorizationRequest(MtacOidcClient, Map)} with
   * every value of the resource parameter (RFC 8707), which may be present
   * several times in a request: more than one value is refused with
   * invalid_target, not invalid_request.
   *
   * @param resources values of the resource parameter, null or empty when absent
   */
  public OidcAuthorizationRequest validateAuthorizationRequest(MtacOidcClient client, Map<String, String> params,
      Collection<String> resources) {
    if (params.containsKey("request")) {
      throw new OidcException("request_not_supported", "Request objects are not supported");
    }
    if (params.containsKey("request_uri")) {
      throw new OidcException("request_uri_not_supported", "Request objects are not supported");
    }
    var responseType = params.get("response_type");
    if (!"code".equals(responseType)) {
      // implicit and hybrid flows are not supported: tokens never travel in a redirect
      throw new OidcException("unsupported_response_type", "Only the authorization code flow is supported");
    }
    var responseMode = params.get("response_mode");
    if (responseMode != null && !"query".equals(responseMode)) {
      throw OidcException.invalidRequest("Only response_mode=query is supported");
    }

    var requestedScopes = splitScopes(params.get("scope"));
    if (!requestedScopes.contains(SCOPE_OPENID)) {
      // An MCP client asks the scopes of the resource challenge (e.g. fuzz:read) without openid: a request for a
      // resource by an API client is a plain OAuth request, the sign-in identity (openid) is implied.
      if (client.getAccessMode() == OidcAccessMode.API && resources != null && !resources.isEmpty()) {
        requestedScopes = new LinkedHashSet<>(requestedScopes);
        requestedScopes.add(SCOPE_OPENID);
      } else {
        throw new OidcException("invalid_scope", "The scope must contain openid");
      }
    }
    var scopes = new LinkedHashSet<String>();
    for (var scope : requestedScopes) {
      // a scope the client is not allowed to ask for is ignored, not granted
      if (client.getScopes().contains(scope)) {
        scopes.add(scope);
      }
    }
    if (!scopes.contains(SCOPE_OPENID)) {
      throw new OidcException("invalid_scope", "The client is not allowed to use the openid scope");
    }

    // PKCE is mandatory for every client, with S256 only
    var codeChallenge = params.get("code_challenge");
    if (codeChallenge == null || !PKCE_VALUE.matcher(codeChallenge).matches()) {
      throw OidcException.invalidRequest("A PKCE code_challenge is required");
    }
    if (!"S256".equals(params.get("code_challenge_method"))) {
      throw OidcException.invalidRequest("code_challenge_method must be S256");
    }

    var prompt = splitScopes(params.get("prompt"));
    if (prompt.contains(PROMPT_NONE) && prompt.size() > 1) {
      throw OidcException.invalidRequest("prompt=none cannot be combined with another value");
    }
    Integer maxAge = null;
    if (params.get("max_age") != null) {
      try {
        maxAge = Integer.valueOf(params.get("max_age"));
      } catch (NumberFormatException e) {
        maxAge = -1;
      }
      if (maxAge < 0) {
        throw OidcException.invalidRequest("Invalid max_age");
      }
    }

    var resource = resolveResource(client, resources);

    return OidcAuthorizationRequest.builder()
        .clientId(params.get("client_id"))
        .redirectUri(params.get("redirect_uri"))
        .scopes(scopes)
        .state(params.get("state"))
        .nonce(params.get("nonce"))
        .codeChallenge(codeChallenge)
        .prompt(prompt)
        .maxAge(maxAge)
        .loginHint(params.get("login_hint"))
        .resource(resource)
        .build();
  }

  /**
   * Audience key asked through the resource parameter (RFC 8707).
   *
   * @param values values of the parameter, null or empty when absent
   * @return the key (api or mcp), or null when no resource is asked
   * @throws OidcException invalid_target when several resources are asked, the
   *                       value is not the absolute URL of a configured audience,
   *                       the audience is the Console, the client is not in API
   *                       access mode or the resource is not one of the client
   */
  public String resolveResource(MtacOidcClient client, Collection<String> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    if (values.size() > 1) {
      throw invalidTarget("Only one resource may be requested");
    }
    var value = values.iterator().next();
    if (value == null || value.isBlank()) {
      throw invalidTarget("Empty resource");
    }
    try {
      var uri = new URI(value);
      if (!uri.isAbsolute() || uri.getRawFragment() != null) {
        throw invalidTarget("The resource must be an absolute URI without fragment");
      }
    } catch (URISyntaxException e) {
      throw invalidTarget("The resource must be an absolute URI without fragment");
    }
    var key = new TokenAudiences(mtacProperties).keyOf(value).orElse(null);
    if (key == null || !DELEGABLE_AUDIENCES.contains(key)) {
      throw invalidTarget("Unknown resource");
    }
    if (!isResourceAllowed(client, key)) {
      throw invalidTarget("The client is not allowed to access this resource");
    }
    return key;
  }

  /** True when the client, in its current configuration, may obtain tokens for this audience key. */
  public static boolean isResourceAllowed(MtacOidcClient client, String key) {
    return client != null && key != null
        && client.getAccessMode() == OidcAccessMode.API
        && DELEGABLE_AUDIENCES.contains(key)
        && client.getResources() != null && client.getResources().contains(key);
  }

  private static OidcException invalidTarget(String description) {
    return new OidcException(ERROR_INVALID_TARGET, description);
  }

  /**
   * True if the SSO session is enough to answer the request without asking the
   * user to authenticate again.
   */
  public boolean isSessionSufficient(OidcAuthorizationRequest request, MtacOidcClient client, OidcSession session) {
    if (session == null) {
      return false;
    }
    if (request.getPrompt().contains(PROMPT_LOGIN)) {
      return false;
    }
    if (request.getMaxAge() != null
        && session.getAuthTime().plusSeconds(request.getMaxAge()).isBefore(Instant.now())) {
      return false;
    }
    if (client.isRequireMfa() && !session.getAmr().contains(OidcSessionService.AMR_OTP)) {
      return false;
    }
    return true;
  }

  /**
   * Issues the authorization code: random, short-lived, single use, bound to the
   * client, the redirect URI, the PKCE challenge and the session.
   */
  public String issueCode(OidcAuthorizationRequest request, OidcSession session) {
    var code = OidcCrypto.randomToken();
    oidcRepository.insertAuthorizationCode(OidcAuthorizationCode.builder()
        .codeHash(OidcCrypto.sha256Hex(code))
        .clientId(request.getClientId())
        .sessionId(session.getId())
        .redirectUri(request.getRedirectUri())
        .scope(String.join(" ", request.getScopes()))
        .nonce(request.getNonce())
        .codeChallenge(request.getCodeChallenge())
        .expiresAt(Instant.now().plusSeconds(config().getAuthorizationCodeTtlSec()))
        .resource(request.getResource())
        .build());
    sessionService.touch(session);
    return code;
  }

  // ---------------------------------------------------------------- token

  /** Token endpoint, grant_type=authorization_code, without resource parameter. */
  public OidcTokenResponse exchangeCode(String clientId, String clientSecret, String origin, String code,
      String redirectUri, String codeVerifier) {
    return exchangeCode(clientId, clientSecret, origin, code, redirectUri, codeVerifier, null);
  }

  /**
   * Token endpoint, grant_type=authorization_code.
   *
   * @param resources values of the resource parameter (RFC 8707): when present,
   *                  it must be the resource of the authorization request
   */
  public OidcTokenResponse exchangeCode(String clientId, String clientSecret, String origin, String code,
      String redirectUri, String codeVerifier, Collection<String> resources) {
    var client = clientRegistry.authenticate(clientId, clientSecret, origin);
    if (code == null || code.isBlank()) {
      throw OidcException.invalidRequest("Missing code");
    }
    var codeHash = OidcCrypto.sha256Hex(code);
    var authorizationCode = oidcRepository.getAuthorizationCode(codeHash);
    if (authorizationCode == null || !authorizationCode.getClientId().equals(clientId)) {
      throw OidcException.invalidGrant("Invalid authorization code");
    }
    if (authorizationCode.getConsumedAt() != null) {
      throw replayedCode(codeHash);
    }
    if (!Instant.now().isBefore(authorizationCode.getExpiresAt())) {
      throw OidcException.invalidGrant("The authorization code has expired");
    }
    if (redirectUri == null || !redirectUri.equals(authorizationCode.getRedirectUri())) {
      throw OidcException.invalidGrant("The redirect_uri does not match the authorization request");
    }
    if (codeVerifier == null || !PKCE_VALUE.matcher(codeVerifier).matches()
        || !OidcCrypto.constantTimeEquals(OidcCrypto.codeChallenge(codeVerifier),
            authorizationCode.getCodeChallenge())) {
      throw OidcException.invalidGrant("Invalid code_verifier");
    }
    var resource = authorizationCode.getResource();
    checkResource(client, resource, resources, "The resource does not match the authorization request");
    if (!oidcRepository.consumeAuthorizationCode(codeHash, Instant.now())) {
      // another request used the code in the meantime
      throw replayedCode(codeHash);
    }
    var session = sessionService.getAlive(authorizationCode.getSessionId());
    if (session == null) {
      throw OidcException.invalidGrant("The session has ended");
    }
    return issueTokens(clientId, client, session, splitScopes(authorizationCode.getScope()),
        authorizationCode.getNonce(), codeHash, resource, Instant.now());
  }

  /**
   * A resource sent to the token endpoint must be the one already granted (a
   * code or a refresh token family is never widened nor narrowed to another
   * audience), and the client must still be allowed to use the granted one.
   */
  private void checkResource(MtacOidcClient client, String granted, Collection<String> requested,
      String mismatch) {
    var key = resolveResource(client, requested);
    if (key != null && !key.equals(granted)) {
      throw invalidTarget(mismatch);
    }
    if (granted != null && !isResourceAllowed(client, granted)) {
      throw invalidTarget("The client is no longer allowed to access this resource");
    }
  }

  /**
   * A code presented twice means it leaked: the tokens issued from its first use
   * are revoked (RFC 6749 section 4.1.2).
   */
  private OidcException replayedCode(String codeHash) {
    log.warn("An OpenID authorization code was presented twice: the tokens issued from it are revoked");
    oidcRepository.revokeRefreshTokenFamily(codeHash, Instant.now());
    return OidcException.invalidGrant("The authorization code has already been used");
  }

  /**
   * Token endpoint, grant_type=refresh_token. The refresh token is replaced at
   * each use; presenting a replaced one again revokes the whole family.
   */
  public OidcTokenResponse refresh(String clientId, String clientSecret, String origin, String refreshToken) {
    return refresh(clientId, clientSecret, origin, refreshToken, null);
  }

  /**
   * Same as {@link #refresh(String, String, String, String)}. The new tokens
   * keep the resource of the family; a resource sent again must be that one.
   * A family older than the client's {@code sessionMaxDays} is revoked
   * (invalid_grant).
   *
   * @param resources values of the resource parameter (RFC 8707), null or empty
   *                  when absent
   */
  public OidcTokenResponse refresh(String clientId, String clientSecret, String origin, String refreshToken,
      Collection<String> resources) {
    var client = clientRegistry.authenticate(clientId, clientSecret, origin);
    if (refreshToken == null || !refreshToken.startsWith(REFRESH_TOKEN_PREFIX)) {
      throw OidcException.invalidGrant("Invalid refresh token");
    }
    var tokenHash = OidcCrypto.sha256Hex(refreshToken);
    var stored = oidcRepository.getRefreshToken(tokenHash);
    if (stored == null || !stored.getClientId().equals(clientId)) {
      throw OidcException.invalidGrant("Invalid refresh token");
    }
    if (stored.getRevokedAt() != null) {
      throw OidcException.invalidGrant("The refresh token has been revoked");
    }
    if (stored.getRotatedAt() != null) {
      throw reusedRefreshToken(stored);
    }
    if (!Instant.now().isBefore(stored.getExpiresAt())) {
      throw OidcException.invalidGrant("The refresh token has expired");
    }
    if (isFamilyTooOld(client, stored, Instant.now())) {
      log.info("The refresh token family of client {} reached its maximum duration of {} days: it is revoked",
          clientId, client.getSessionMaxDays());
      oidcRepository.revokeRefreshTokenFamily(stored.getFamilyId(), Instant.now());
      throw OidcException.invalidGrant("The maximum session duration of the client is reached");
    }
    checkResource(client, stored.getResource(), resources, "The resource does not match the refresh token");
    // checked before the rotation, like the resource: a refused refresh does not consume the token.
    // The scopes granted at sign-in, minus those the client lost since (a super admin or a policy narrowed it)
    var scopes = splitScopes(stored.getScope());
    scopes.retainAll(client.getScopes());
    if (!scopes.contains(SCOPE_OPENID)) {
      throw OidcException.invalidGrant("The client is no longer allowed to use the openid scope");
    }
    var session = sessionService.getAlive(stored.getSessionId());
    if (session == null) {
      throw OidcException.invalidGrant("The session has ended");
    }
    if (!oidcRepository.rotateRefreshToken(tokenHash, Instant.now())) {
      throw reusedRefreshToken(stored);
    }
    sessionService.touch(session);
    // a family stored before family_started_at existed starts counting now
    var familyStartedAt = stored.getFamilyStartedAt() != null ? stored.getFamilyStartedAt() : Instant.now();
    return issueTokens(clientId, client, session, scopes, null, stored.getFamilyId(),
        stored.getResource(), familyStartedAt);
  }

  /** True when the client limits its sessions and the family started more than sessionMaxDays ago. */
  static boolean isFamilyTooOld(MtacOidcClient client, OidcRefreshToken stored, Instant now) {
    var maxDays = client.getSessionMaxDays();
    if (maxDays == null || maxDays <= 0 || stored.getFamilyStartedAt() == null) {
      return false;
    }
    return !now.isBefore(stored.getFamilyStartedAt().plus(Duration.ofDays(maxDays)));
  }

  private OidcException reusedRefreshToken(OidcRefreshToken stored) {
    log.warn("An OpenID refresh token of client {} was used twice: its family is revoked", stored.getClientId());
    oidcRepository.revokeRefreshTokenFamily(stored.getFamilyId(), Instant.now());
    return OidcException.invalidGrant("The refresh token has already been used");
  }

  /**
   * @param resource        audience key of the access token, null for an
   *                        identity-only token (aud = userinfo)
   * @param familyStartedAt first token of the refresh token family
   */
  private OidcTokenResponse issueTokens(String clientId, MtacOidcClient client, OidcSession session,
      Set<String> scopes, String nonce, String familyId, String resource, Instant familyStartedAt) {
    var config = config();
    var scope = String.join(" ", scopes);
    var ttl = config.getAccessTokenTtlSec();

    var accessClaims = new HashMap<String, Object>();
    accessClaims.put("jti", UUID.randomUUID().toString());
    accessClaims.put("sub", session.getSub());
    if (resource == null) {
      // identity only: usable at the userinfo endpoint, refused by every API
      accessClaims.put("aud", url(PATH_USERINFO));
    } else {
      // delegated access to an API (RFC 8707 + RFC 9068)
      var audience = new TokenAudiences(mtacProperties).url(resource);
      if (audience == null) {
        throw invalidTarget("The resource is no longer configured");
      }
      accessClaims.put("aud", audience);
      accessClaims.put("auth_time", session.getAuthTime().getEpochSecond());
      accessClaims.put("amr", session.getAmr());
    }
    accessClaims.put("client_id", clientId);
    accessClaims.put("scope", scope);
    accessClaims.put("sid", session.getId());
    var accessToken = tokenService.newAccessToken(accessClaims, ttl); // typ at+jwt (RFC 9068)

    var idClaims = new HashMap<String, Object>(openIdService.oidcClaims(session.getSub(), scopes));
    idClaims.put("aud", clientId);
    idClaims.put("auth_time", session.getAuthTime().getEpochSecond());
    idClaims.put("amr", session.getAmr());
    idClaims.put("sid", session.getId());
    idClaims.put("at_hash", OidcCrypto.accessTokenHash(accessToken));
    if (nonce != null) {
      idClaims.put("nonce", nonce);
    }
    var idToken = tokenService.newToken(idClaims, ttl);

    String refreshToken = null;
    if (client.isRefreshToken()) {
      refreshToken = REFRESH_TOKEN_PREFIX + OidcCrypto.randomToken();
      var expiresAt = Instant.now().plusSeconds(config.getRefreshTokenTtlSec());
      if (expiresAt.isAfter(session.getExpiresAt())) {
        expiresAt = session.getExpiresAt();
      }
      oidcRepository.insertRefreshToken(OidcRefreshToken.builder()
          .tokenHash(OidcCrypto.sha256Hex(refreshToken))
          .familyId(familyId)
          .clientId(clientId)
          .sessionId(session.getId())
          .scope(scope)
          .expiresAt(expiresAt)
          .resource(resource)
          .familyStartedAt(familyStartedAt)
          .build());
    }
    recordClientUse(clientId, client);

    return OidcTokenResponse.builder()
        .accessToken(accessToken)
        .expiresIn(ttl)
        .idToken(idToken)
        .refreshToken(refreshToken)
        .scope(scope)
        .build();
  }

  /**
   * Sets lastUsedAt of a client resource (the cleanup of unused dynamic clients
   * relies on it), at most once per {@link #LAST_USED_AT_THROTTLE} and client
   * on this instance. A failure never prevents the tokens from being issued.
   */
  void recordClientUse(String clientId, MtacOidcClient client) {
    var uid = client.getUid();
    if (uid == null || clientRepository == null) {
      return; // yml or CIMD client: nothing stored
    }
    var now = clock.instant();
    var write = new boolean[1];
    lastUsedAtWrites.compute(uid, (key, previous) -> {
      if (previous != null && now.isBefore(previous.plus(LAST_USED_AT_THROTTLE))) {
        return previous;
      }
      write[0] = true;
      return now;
    });
    if (!write[0]) {
      return;
    }
    try {
      clientRepository.touchLastUsedAt(uid, clientId, now);
    } catch (RuntimeException e) {
      log.warn("Cannot record the last use of OpenID client {}: {}", clientId, e.getMessage());
    }
  }

  /** Revocation endpoint (RFC 7009). An unknown token is not an error. */
  public void revoke(String clientId, String clientSecret, String origin, String token) {
    clientRegistry.authenticate(clientId, clientSecret, origin);
    if (token == null || !token.startsWith(REFRESH_TOKEN_PREFIX)) {
      // access tokens are short-lived JWT: there is nothing to revoke
      return;
    }
    var stored = oidcRepository.getRefreshToken(OidcCrypto.sha256Hex(token));
    if (stored != null && stored.getClientId().equals(clientId)) {
      oidcRepository.revokeRefreshTokenFamily(stored.getFamilyId(), Instant.now());
    }
  }

  // ------------------------------------------------------------- userinfo

  /** Userinfo endpoint: the claims allowed by the scopes of the access token. */
  public Map<String, Object> userinfo(String accessToken) {
    io.jsonwebtoken.Claims claims;
    try {
      claims = tokenService.verify(accessToken);
    } catch (Exception e) {
      throw OidcException.invalidToken("Invalid access token");
    }
    // only the access tokens issued by this provider for this endpoint are accepted
    if (!url(PATH_USERINFO).equals(claims.getAudience()) || claims.getSubject() == null) {
      throw OidcException.invalidToken("Invalid access token");
    }
    if (sessionService.getAlive(claims.get("sid", String.class)) == null) {
      throw OidcException.invalidToken("The session has ended");
    }
    return openIdService.oidcClaims(claims.getSubject(), splitScopes(claims.get("scope", String.class)));
  }

  // --------------------------------------------------------------- logout

  /**
   * Claims of the id_token_hint of a logout request, or null if it is missing
   * or not a token issued by this provider. An expired id token is accepted.
   */
  public io.jsonwebtoken.Claims parseIdTokenHint(String idTokenHint) {
    if (idTokenHint == null || idTokenHint.isBlank()) {
      return null;
    }
    try {
      var claims = tokenService.verifyIgnoringExpiration(idTokenHint);
      if (claims.getAudience() == null || clientRegistry.find(claims.getAudience()) == null) {
        return null;
      }
      return claims;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Where to send the user after a logout: the post_logout_redirect_uri if it is
   * registered for the client, otherwise null (the provider shows its own page).
   */
  public String postLogoutRedirect(String clientId, String postLogoutRedirectUri, String state) {
    var client = clientRegistry.find(clientId);
    if (client == null || !clientRegistry.isPostLogoutRedirectUriRegistered(client, postLogoutRedirectUri)) {
      return null;
    }
    // The confirmation form posts an empty state when the client sent none.
    if (state == null || state.isBlank()) {
      return postLogoutRedirectUri;
    }
    return appendQuery(postLogoutRedirectUri, Map.of("state", state));
  }

  // ------------------------------------------------------------ discovery

  public Map<String, Object> discovery() {
    var scopes = new LinkedHashSet<String>(List.of(SCOPE_OPENID, "profile", "email"));
    scopes.add(config().getTenantsScope());
    scopes.add(SCOPE_OFFLINE_ACCESS);
    // delegated API access (resource indicator), see DelegatedScopePolicy
    scopes.addAll(DelegatedScopePolicy.API_SCOPES);
    var metadata = new LinkedHashMap<String, Object>();
    metadata.put("issuer", getIssuer());
    metadata.put("authorization_endpoint", url(PATH_AUTHORIZE));
    metadata.put("token_endpoint", url(PATH_TOKEN));
    metadata.put("userinfo_endpoint", url(PATH_USERINFO));
    metadata.put("jwks_uri", url(PATH_JWKS));
    metadata.put("end_session_endpoint", url(PATH_LOGOUT));
    metadata.put("revocation_endpoint", url(PATH_REVOKE));
    metadata.put("response_types_supported", List.of("code"));
    metadata.put("response_modes_supported", List.of("query"));
    metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token", "client_credentials"));
    metadata.put("subject_types_supported", List.of("public"));
    metadata.put("id_token_signing_alg_values_supported", List.of("RS256"));
    metadata.put("scopes_supported", scopes);
    metadata.put("token_endpoint_auth_methods_supported",
        List.of("client_secret_basic", "client_secret_post", "private_key_jwt", "none"));
    // private_key_jwt is offered to the service accounts (client_credentials grant)
    metadata.put("token_endpoint_auth_signing_alg_values_supported", PrivateKeyJwtVerifier.supportedAlgorithmNames());
    metadata.put("code_challenge_methods_supported", List.of("S256"));
    metadata.put("claims_supported", List.of("sub", "iss", "aud", "exp", "iat", "auth_time", "nonce", "amr", "sid",
        "at_hash", "name", "given_name", "family_name", "preferred_username", "email"));
    metadata.put("authorization_response_iss_parameter_supported", true);
    if (config().getDynamic().isDcrEnabled()) {
      metadata.put("registration_endpoint", url(OidcRegistrationEndpoint.PATH_REGISTER));
    }
    if (config().getCimd().isEnabled()) {
      // the client_id may be the https URL of a document describing the client
      metadata.put("client_id_metadata_document_supported", true);
    }
    metadata.put("request_parameter_supported", false);
    metadata.put("request_uri_parameter_supported", false);
    return metadata;
  }

  // -------------------------------------------------------------- helpers

  public static Set<String> splitScopes(String value) {
    var result = new LinkedHashSet<String>();
    if (value != null && !value.isBlank()) {
      result.addAll(Arrays.asList(value.trim().split("\\s+")));
    }
    return result;
  }

  /** Appends query parameters to an URL that may already have a query string. */
  public static String appendQuery(String url, Map<String, String> params) {
    var result = new StringBuilder(url);
    var separator = url.contains("?") ? '&' : '?';
    for (var entry : params.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      result.append(separator)
          .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
      separator = '&';
    }
    return result.toString();
  }

  private MtacProperties.MtacOpenIdServer config() {
    return mtacProperties.getOpenId().getServer();
  }
}
