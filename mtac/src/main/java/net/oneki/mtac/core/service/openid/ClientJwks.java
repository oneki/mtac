package net.oneki.mtac.core.service.openid;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.JSONObjectUtils;

/**
 * Parsing and validation of the public keys a service account registers for
 * {@code private_key_jwt} (inline JWK Set or URL of a JWK Set). Shared by the
 * application service (at create / update), the JWKS cache (on every fetch) and
 * the assertion verifier, so that a key accepted once is always held to the
 * same rules.
 */
public final class ClientJwks {
  public static final int MAX_JWKS_LENGTH = 64 * 1024;
  public static final int MAX_KEYS = 10;
  public static final int MAX_URI_LENGTH = 2048;
  public static final int MIN_RSA_BITS = 2048;
  public static final Set<JWSAlgorithm> RSA_ALGORITHMS = Set.of(JWSAlgorithm.RS256, JWSAlgorithm.PS256);
  public static final Set<JWSAlgorithm> EC_ALGORITHMS = Set.of(JWSAlgorithm.ES256);

  // members that only a private or symmetric key carries (RFC 7518 section 6)
  private static final Set<String> PRIVATE_MEMBERS = Set.of("d", "p", "q", "dp", "dq", "qi", "oth", "k");

  private ClientJwks() {
  }

  /** Thrown when a JWK Set or a JWKS URL breaks a rule; the message says which. */
  public static class InvalidJwksException extends RuntimeException {
    public InvalidJwksException(String message) {
      super(message);
    }
  }

  /**
   * Parses a JWK Set and checks every key: public only, RSA of at least 2048
   * bits or EC P-256, usable for signatures, at most {@value #MAX_KEYS} keys,
   * distinct kid values.
   */
  public static JWKSet parse(String json) {
    if (json == null || json.isBlank()) {
      throw new InvalidJwksException("The JWK Set is empty");
    }
    if (json.getBytes(StandardCharsets.UTF_8).length > MAX_JWKS_LENGTH) {
      throw new InvalidJwksException("The JWK Set is larger than " + MAX_JWKS_LENGTH + " bytes");
    }
    Map<String, Object> object;
    try {
      object = JSONObjectUtils.parse(json, MAX_JWKS_LENGTH);
    } catch (ParseException e) {
      throw new InvalidJwksException("The JWK Set is not a JSON object");
    }
    if (!(object.get("keys") instanceof List<?> rawKeys)) {
      throw new InvalidJwksException("The JWK Set has no keys array");
    }
    if (rawKeys.isEmpty()) {
      throw new InvalidJwksException("The JWK Set has no key");
    }
    if (rawKeys.size() > MAX_KEYS) {
      throw new InvalidJwksException("The JWK Set has more than " + MAX_KEYS + " keys");
    }
    for (var rawKey : rawKeys) {
      if (!(rawKey instanceof Map<?, ?> members)) {
        throw new InvalidJwksException("A key of the JWK Set is not a JSON object");
      }
      for (var member : members.keySet()) {
        if (PRIVATE_MEMBERS.contains(member)) {
          throw new InvalidJwksException("The JWK Set contains private or symmetric key material (member \"" + member
              + "\"): register the public keys only");
        }
      }
    }

    JWKSet jwkSet;
    try {
      jwkSet = JWKSet.parse(object);
    } catch (ParseException e) {
      throw new InvalidJwksException("Invalid JWK Set: " + e.getMessage());
    }
    // nimbus silently skips the keys of an unknown type: refuse them instead
    if (jwkSet.getKeys().size() != rawKeys.size()) {
      throw new InvalidJwksException("The JWK Set contains a key of an unsupported type (RSA or EC P-256 only)");
    }
    var kids = new HashSet<String>();
    for (var key : jwkSet.getKeys()) {
      validateKey(key);
      if (key.getKeyID() != null && !kids.add(key.getKeyID())) {
        throw new InvalidJwksException("Two keys of the JWK Set have the same kid: " + key.getKeyID());
      }
    }
    return jwkSet;
  }

  private static void validateKey(JWK key) {
    if (key.isPrivate()) {
      throw new InvalidJwksException("The JWK Set contains a private key: register the public keys only");
    }
    if (key.getKeyUse() != null && !KeyUse.SIGNATURE.equals(key.getKeyUse())) {
      throw new InvalidJwksException("A key of the JWK Set is not a signature key (use must be sig)");
    }
    if (key.getKeyOperations() != null && !key.getKeyOperations().isEmpty()
        && key.getKeyOperations().stream().noneMatch(op -> "verify".equals(op.identifier()))) {
      throw new InvalidJwksException("A key of the JWK Set does not allow verify in key_ops");
    }
    var alg = key.getAlgorithm();
    switch (key) {
      case RSAKey rsa -> {
        if (rsa.size() < MIN_RSA_BITS) {
          throw new InvalidJwksException("An RSA key of the JWK Set is shorter than " + MIN_RSA_BITS + " bits");
        }
        if (alg != null && !RSA_ALGORITHMS.contains(JWSAlgorithm.parse(alg.getName()))) {
          throw new InvalidJwksException("An RSA key of the JWK Set declares an unsupported alg: " + alg.getName()
              + " (RS256 or PS256)");
        }
      }
      case ECKey ec -> {
        if (!Curve.P_256.equals(ec.getCurve())) {
          throw new InvalidJwksException("An EC key of the JWK Set is not on the P-256 curve");
        }
        if (alg != null && !EC_ALGORITHMS.contains(JWSAlgorithm.parse(alg.getName()))) {
          throw new InvalidJwksException("An EC key of the JWK Set declares an unsupported alg: " + alg.getName()
              + " (ES256)");
        }
      }
      default -> throw new InvalidJwksException("The JWK Set contains a key of an unsupported type (RSA or EC P-256 only)");
    }
  }

  /**
   * Checks the form of a JWKS URL: absolute https URL with a host, no user
   * info, no fragment. Whether its address is public is checked when it is
   * fetched (SafeHttpFetcher), since DNS answers change.
   */
  public static String validateUri(String uri) {
    if (uri == null || uri.isBlank()) {
      throw new InvalidJwksException("The JWKS URL is empty");
    }
    if (uri.length() > MAX_URI_LENGTH) {
      throw new InvalidJwksException("The JWKS URL is longer than " + MAX_URI_LENGTH + " characters");
    }
    URI parsed;
    try {
      parsed = new URI(uri);
    } catch (URISyntaxException e) {
      throw new InvalidJwksException("The JWKS URL is not a valid URL");
    }
    if (!parsed.isAbsolute() || !"https".equalsIgnoreCase(parsed.getScheme())) {
      throw new InvalidJwksException("The JWKS URL must be an https URL");
    }
    if (parsed.getHost() == null || parsed.getHost().isBlank()) {
      throw new InvalidJwksException("The JWKS URL has no host");
    }
    if (parsed.getRawUserInfo() != null) {
      throw new InvalidJwksException("The JWKS URL must not contain user info");
    }
    if (parsed.getRawFragment() != null) {
      throw new InvalidJwksException("The JWKS URL must not contain a fragment");
    }
    return uri;
  }
}
