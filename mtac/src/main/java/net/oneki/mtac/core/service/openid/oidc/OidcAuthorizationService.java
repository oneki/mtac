package net.oneki.mtac.core.service.openid.oidc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.framework.repository.OidcRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;
import net.oneki.mtac.model.core.openid.oidc.OidcAuthorizationCode;
import net.oneki.mtac.model.core.openid.oidc.OidcAuthorizationRequest;
import net.oneki.mtac.model.core.openid.oidc.OidcException;
import net.oneki.mtac.model.core.openid.oidc.OidcRefreshToken;
import net.oneki.mtac.model.core.openid.oidc.OidcSession;
import net.oneki.mtac.model.core.openid.oidc.OidcTokenResponse;

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
 * The tokens issued here identify the user for the client: they carry an
 * audience and are therefore refused by the resource server of the API.
 */
@Service
@Slf4j
public class OidcAuthorizationService {
  public static final String SCOPE_OPENID = "openid";
  public static final String REFRESH_TOKEN_PREFIX = "ort_";
  public static final String PROMPT_NONE = "none";
  public static final String PROMPT_LOGIN = "login";

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
      throw new OidcException("invalid_scope", "The scope must contain openid");
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
        .build();
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
        .build());
    sessionService.touch(session);
    return code;
  }

  // ---------------------------------------------------------------- token

  /** Token endpoint, grant_type=authorization_code. */
  public OidcTokenResponse exchangeCode(String clientId, String clientSecret, String origin, String code,
      String redirectUri, String codeVerifier) {
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
    if (!oidcRepository.consumeAuthorizationCode(codeHash, Instant.now())) {
      // another request used the code in the meantime
      throw replayedCode(codeHash);
    }
    var session = sessionService.getAlive(authorizationCode.getSessionId());
    if (session == null) {
      throw OidcException.invalidGrant("The session has ended");
    }
    return issueTokens(clientId, client, session, splitScopes(authorizationCode.getScope()),
        authorizationCode.getNonce(), codeHash);
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
    var session = sessionService.getAlive(stored.getSessionId());
    if (session == null) {
      throw OidcException.invalidGrant("The session has ended");
    }
    if (!oidcRepository.rotateRefreshToken(tokenHash, Instant.now())) {
      throw reusedRefreshToken(stored);
    }
    sessionService.touch(session);
    return issueTokens(clientId, client, session, splitScopes(stored.getScope()), null, stored.getFamilyId());
  }

  private OidcException reusedRefreshToken(OidcRefreshToken stored) {
    log.warn("An OpenID refresh token of client {} was used twice: its family is revoked", stored.getClientId());
    oidcRepository.revokeRefreshTokenFamily(stored.getFamilyId(), Instant.now());
    return OidcException.invalidGrant("The refresh token has already been used");
  }

  private OidcTokenResponse issueTokens(String clientId, MtacOidcClient client, OidcSession session,
      Set<String> scopes, String nonce, String familyId) {
    var config = config();
    var scope = String.join(" ", scopes);
    var ttl = config.getAccessTokenTtlSec();

    var accessClaims = new HashMap<String, Object>();
    accessClaims.put("jti", UUID.randomUUID().toString());
    accessClaims.put("sub", session.getSub());
    accessClaims.put("aud", url(PATH_USERINFO));
    accessClaims.put("client_id", clientId);
    accessClaims.put("scope", scope);
    accessClaims.put("sid", session.getId());
    var accessToken = tokenService.newToken(accessClaims, ttl);

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
          .build());
    }

    return OidcTokenResponse.builder()
        .accessToken(accessToken)
        .expiresIn(ttl)
        .idToken(idToken)
        .refreshToken(refreshToken)
        .scope(scope)
        .build();
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
        List.of("client_secret_basic", "client_secret_post", "none"));
    metadata.put("code_challenge_methods_supported", List.of("S256"));
    metadata.put("claims_supported", List.of("sub", "iss", "aud", "exp", "iat", "auth_time", "nonce", "amr", "sid",
        "at_hash", "name", "given_name", "family_name", "preferred_username", "email"));
    metadata.put("authorization_response_iss_parameter_supported", true);
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
