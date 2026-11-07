package net.oneki.mtac.core.service.openid.oidc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Random values, hashes and comparisons used by the OpenID provider. */
public final class OidcCrypto {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private OidcCrypto() {
  }

  /** 256 bits of randomness, base64url encoded (43 characters). */
  public static String randomToken() {
    var bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return BASE64_URL.encodeToString(bytes);
  }

  /** Hash under which a secret (session id, code, refresh token) is stored. */
  public static String sha256Hex(String value) {
    return HexFormat.of().formatHex(sha256(value));
  }

  /** PKCE S256: BASE64URL(SHA256(ASCII(code_verifier))). */
  public static String codeChallenge(String codeVerifier) {
    return BASE64_URL.encodeToString(sha256(codeVerifier));
  }

  /** OIDC at_hash: left half of the SHA-256 of the access token, base64url encoded. */
  public static String accessTokenHash(String accessToken) {
    var digest = sha256(accessToken);
    var half = new byte[digest.length / 2];
    System.arraycopy(digest, 0, half, 0, half.length);
    return BASE64_URL.encodeToString(half);
  }

  public static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
