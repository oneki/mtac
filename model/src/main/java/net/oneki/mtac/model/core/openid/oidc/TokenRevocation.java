package net.oneki.mtac.model.core.openid.oidc;

import java.time.Instant;

/**
 * One entry of the revocation list of the access tokens (table
 * {@code token_revocation}): the access tokens whose claim of this kind has
 * this value are refused until {@code expiresAt}, when every token it targets
 * has expired anyway.
 *
 * @param kind      which claim is revoked
 * @param value     user uid ({@code sub}), SSO session id ({@code sid}) or
 *                  refresh family claim ({@code fam})
 * @param revokedAt when the revocation was written
 * @param expiresAt {@code revokedAt} plus the longest access token lifetime
 *                  and the accepted clock skew
 */
public record TokenRevocation(Kind kind, String value, Instant revokedAt, Instant expiresAt) {

  /** Claim a revocation applies to; {@link #code()} is the value of the {@code kind} column. */
  public enum Kind {
    /** Every access token of a user ({@code sub}) issued until the revocation. */
    USER("user", "sub"),
    /** Every access token issued in an SSO session ({@code sid}). */
    SESSION("session", "sid"),
    /** Every access token of a refresh token family ({@code fam}): one device or connector. */
    FAMILY("family", "fam");

    private final String code;
    private final String claim;

    Kind(String code, String claim) {
      this.code = code;
      this.claim = claim;
    }

    public String code() {
      return code;
    }

    /** Access token claim checked against the entries of this kind. */
    public String claim() {
      return claim;
    }

    /** @return the kind of a {@code kind} column value, null when unknown */
    public static Kind ofCode(String code) {
      for (var kind : values()) {
        if (kind.code.equals(code)) {
          return kind;
        }
      }
      return null;
    }
  }
}
