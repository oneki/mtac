package net.oneki.mtac.core.service.openid;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.repository.ClientAssertionJtiRepository;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.oidc.OidcException;
import net.oneki.mtac.model.resource.iam.identity.application.Application;
import net.oneki.mtac.model.resource.iam.identity.application.ApplicationAuthMethod;

/**
 * Client authentication with {@code private_key_jwt} (RFC 7523 section 2.2,
 * OpenID Connect Core section 9): the client proves its identity with a JWT it
 * signed with a key it registered (inline {@code jwks} or {@code jwksUri}).
 *
 * <p>
 * Checks, in order: assertion type; JWS with RS256, PS256 or ES256 only (never
 * none or HMAC); {@code iss == sub == client_id} of an application whose method
 * is {@code private_key_jwt}; signature against the application keys (a
 * {@code jwksUri} set is cached and fetched again once when the {@code kid} is
 * unknown); {@code aud} contains one of the expected audiences (token endpoint
 * URLs, issuer); {@code exp} present, not past and at most
 * {@link #MAX_LIFETIME} ahead (plus the clock skew); {@code iat} and
 * {@code nbf} not in the future; {@code jti} present and never seen before,
 * stored last so that only fully valid assertions consume an identifier.
 *
 * <p>
 * Every failure is an {@link OidcException} {@code invalid_client} (HTTP 401).
 * The description stays generic; the precise reason is logged.
 */
@Service
@Slf4j
public class PrivateKeyJwtVerifier {
  public static final String CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
  public static final Set<JWSAlgorithm> SUPPORTED_ALGORITHMS = Set.of(JWSAlgorithm.RS256, JWSAlgorithm.PS256,
      JWSAlgorithm.ES256);
  public static final Duration MAX_LIFETIME = Duration.ofMinutes(5);
  static final int MAX_ASSERTION_LENGTH = 16 * 1024;
  static final int MAX_JTI_LENGTH = 255;
  static final int MAX_CLIENT_ID_LENGTH = 255;

  private final MtacProperties mtacProperties;
  private final ClientAssertionJtiRepository jtiRepository;
  private final ClientJwksCache jwksCache;
  private final Clock clock;

  @Autowired
  public PrivateKeyJwtVerifier(MtacProperties mtacProperties, ClientAssertionJtiRepository jtiRepository,
      ClientJwksCache jwksCache) {
    this(mtacProperties, jtiRepository, jwksCache, Clock.systemUTC());
  }

  public PrivateKeyJwtVerifier(MtacProperties mtacProperties, ClientAssertionJtiRepository jtiRepository,
      ClientJwksCache jwksCache, Clock clock) {
    this.mtacProperties = mtacProperties;
    this.jtiRepository = jtiRepository;
    this.jwksCache = jwksCache;
    this.clock = clock;
  }

  /** The signing algorithms to advertise in token_endpoint_auth_signing_alg_values_supported. */
  public static List<String> supportedAlgorithmNames() {
    return List.of(JWSAlgorithm.RS256.getName(), JWSAlgorithm.PS256.getName(), JWSAlgorithm.ES256.getName());
  }

  /** True when the request carries a client assertion (client_assertion_type or client_assertion present). */
  public static boolean isAssertionRequest(String clientAssertionType, String clientAssertion) {
    return clientAssertionType != null || clientAssertion != null;
  }

  /**
   * Authenticates a client by its assertion.
   *
   * @param clientAssertionType the {@code client_assertion_type} form parameter
   * @param clientAssertion     the {@code client_assertion} form parameter
   * @param clientId            the {@code client_id} form parameter when the
   *                            client sent one (it must then equal the
   *                            assertion issuer), else null
   * @param expectedAudiences   the token endpoint URL(s) the client may name in
   *                            {@code aud}; the issuer ({@code mtac.jwt.issuer})
   *                            is always accepted as well
   * @param applicationLoader   loads an application by client_id (its label),
   *                            null when unknown; any other identity type must
   *                            be mapped to null
   * @return the authenticated application
   * @throws OidcException invalid_client (401) for any failure
   */
  public <A extends Application> A verify(String clientAssertionType, String clientAssertion, String clientId,
      Collection<String> expectedAudiences, Function<String, A> applicationLoader) {
    if (!CLIENT_ASSERTION_TYPE.equals(clientAssertionType)) {
      throw fail("unsupported client_assertion_type " + clientAssertionType);
    }
    if (clientAssertion == null || clientAssertion.isBlank() || clientAssertion.length() > MAX_ASSERTION_LENGTH) {
      throw fail("missing or oversized client_assertion");
    }

    SignedJWT jwt;
    JWTClaimsSet claims;
    try {
      // a JWT with alg none or a JWE is not a JWS: SignedJWT.parse refuses it
      jwt = SignedJWT.parse(clientAssertion);
      claims = jwt.getJWTClaimsSet();
    } catch (ParseException e) {
      throw fail("client_assertion is not a signed JWT: " + e.getMessage());
    }
    var header = jwt.getHeader();
    if (!SUPPORTED_ALGORITHMS.contains(header.getAlgorithm())) {
      throw fail("unsupported client_assertion alg " + header.getAlgorithm());
    }

    var issuer = claims.getIssuer();
    if (issuer == null || issuer.isBlank() || issuer.length() > MAX_CLIENT_ID_LENGTH) {
      throw fail("client_assertion without iss");
    }
    if (!issuer.equals(claims.getSubject())) {
      throw fail("client_assertion iss and sub differ");
    }
    if (clientId != null && !clientId.equals(issuer)) {
      throw fail("client_id parameter differs from the client_assertion issuer");
    }

    A application;
    try {
      application = applicationLoader.apply(issuer);
    } catch (RuntimeException e) {
      log.warn("private_key_jwt: unable to load client {}: {}", issuer, e.getMessage());
      application = null;
    }
    if (application == null) {
      throw fail("unknown client " + issuer);
    }
    if (ApplicationAuthMethod.of(application) != ApplicationAuthMethod.PRIVATE_KEY_JWT) {
      throw fail("client " + issuer + " does not use private_key_jwt");
    }

    verifySignature(jwt, application);
    verifyAudience(claims, expectedAudiences);
    var expiration = verifyTimes(claims);

    var jti = claims.getJWTID();
    if (jti == null || jti.isBlank() || jti.length() > MAX_JTI_LENGTH) {
      throw fail("client_assertion without a valid jti");
    }
    var keepUntil = expiration.plusSeconds(clockSkewSec());
    if (!jtiRepository.tryStore(jti, issuer, keepUntil)) {
      throw fail("client_assertion jti already used (replay) for client " + issuer);
    }
    return application;
  }

  // ------------------------------------------------------------ signature

  private void verifySignature(SignedJWT jwt, Application application) {
    var header = jwt.getHeader();
    if (application.getJwks() != null && !application.getJwks().isBlank()) {
      JWKSet jwkSet;
      try {
        jwkSet = ClientJwks.parse(application.getJwks());
      } catch (ClientJwks.InvalidJwksException e) {
        throw fail("stored jwks of client " + application.getLabel() + " is invalid: " + e.getMessage());
      }
      if (!verifyWithAny(jwt, candidates(jwkSet, header))) {
        throw fail("client_assertion signature does not match the keys of " + application.getLabel());
      }
      return;
    }

    var jwksUri = application.getJwksUri();
    if (jwksUri == null || jwksUri.isBlank()) {
      throw fail("client " + application.getLabel() + " has no key");
    }
    try {
      var candidates = candidates(jwksCache.get(jwksUri, false), header);
      if (candidates.isEmpty() && header.getKeyID() != null) {
        // the client may have rotated its keys since the set was cached
        candidates = candidates(jwksCache.get(jwksUri, true), header);
      }
      if (!verifyWithAny(jwt, candidates)) {
        throw fail("client_assertion signature does not match the keys of " + application.getLabel());
      }
    } catch (ClientJwksCache.JwksUnavailableException e) {
      throw fail(e.getMessage());
    }
  }

  /** Keys that may have produced the signature: same kid when the header has one, compatible type and alg. */
  private static List<JWK> candidates(JWKSet jwkSet, JWSHeader header) {
    var kid = header.getKeyID();
    var alg = header.getAlgorithm();
    var result = new ArrayList<JWK>();
    for (var key : jwkSet.getKeys()) {
      if (kid != null && !kid.equals(key.getKeyID())) {
        continue;
      }
      if (key.getAlgorithm() != null && !alg.getName().equals(key.getAlgorithm().getName())) {
        continue;
      }
      var compatible = switch (key) {
        case RSAKey rsa -> ClientJwks.RSA_ALGORITHMS.contains(alg);
        case ECKey ec -> ClientJwks.EC_ALGORITHMS.contains(alg) && Curve.P_256.equals(ec.getCurve());
        default -> false;
      };
      if (compatible) {
        result.add(key);
      }
    }
    return result;
  }

  private static boolean verifyWithAny(SignedJWT jwt, List<JWK> keys) {
    for (var key : keys) {
      try {
        JWSVerifier verifier = switch (key) {
          case RSAKey rsa -> new RSASSAVerifier(rsa.toRSAPublicKey());
          case ECKey ec -> new ECDSAVerifier(ec.toECPublicKey());
          default -> null;
        };
        // the verifiers refuse any "crit" header parameter they do not understand
        if (verifier != null && jwt.verify(verifier)) {
          return true;
        }
      } catch (JOSEException e) {
        log.debug("private_key_jwt: verification with key {} failed: {}", key.getKeyID(), e.getMessage());
      }
    }
    return false;
  }

  // --------------------------------------------------------------- claims

  private void verifyAudience(JWTClaimsSet claims, Collection<String> expectedAudiences) {
    var accepted = new LinkedHashSet<String>();
    if (expectedAudiences != null) {
      expectedAudiences.stream().filter(a -> a != null && !a.isBlank()).forEach(accepted::add);
    }
    var issuer = mtacProperties.getJwt().getIssuer();
    if (issuer != null && !issuer.isBlank()) {
      accepted.add(issuer);
    }
    var audience = claims.getAudience();
    if (audience == null || audience.isEmpty() || audience.stream().noneMatch(accepted::contains)) {
      throw fail("client_assertion aud " + audience + " names neither the token endpoint nor the issuer");
    }
  }

  /** @return the expiration of the assertion */
  private Instant verifyTimes(JWTClaimsSet claims) {
    var now = clock.instant();
    var skew = Duration.ofSeconds(clockSkewSec());
    var exp = instant(claims.getExpirationTime());
    if (exp == null) {
      throw fail("client_assertion without exp");
    }
    if (!exp.isAfter(now.minus(skew))) {
      throw fail("client_assertion expired at " + exp);
    }
    if (exp.isAfter(now.plus(MAX_LIFETIME).plus(skew))) {
      throw fail("client_assertion exp " + exp + " is more than " + MAX_LIFETIME.toMinutes() + " minutes ahead");
    }
    var iat = instant(claims.getIssueTime());
    if (iat != null && iat.isAfter(now.plus(skew))) {
      throw fail("client_assertion iat " + iat + " is in the future");
    }
    if (iat != null && iat.isAfter(exp)) {
      throw fail("client_assertion iat is after exp");
    }
    var nbf = instant(claims.getNotBeforeTime());
    if (nbf != null && nbf.isAfter(now.plus(skew))) {
      throw fail("client_assertion not valid before " + nbf);
    }
    return exp;
  }

  private int clockSkewSec() {
    return Math.max(0, mtacProperties.getJwt().getClockSkewSec());
  }

  private static Instant instant(Date date) {
    return date != null ? date.toInstant() : null;
  }

  private static OidcException fail(String reason) {
    log.info("private_key_jwt client authentication refused: {}", reason);
    return OidcException.invalidClient("Client authentication failed");
  }
}
