package net.oneki.mtac.core.service.openid.oidc;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;

/**
 * Hook of the application: the lifetime of a refresh token family, decided
 * per user and client (for example a longer session for a user whose only
 * rights are low-risk ones). Optional bean: without it, or when it answers
 * empty, the server values apply ({@code refresh-token-ttl-sec},
 * {@code refresh-token-max-age-sec}).
 *
 * <p>
 * It is called each time a refresh token is issued (code exchange, refresh)
 * for a client that receives refresh tokens, so a change of the user's rights
 * applies at the next refresh. A family older than the returned maximum is
 * revoked and the refresh gets {@code invalid_grant}. The client's
 * {@code sessionMaxDays}, when set, still bounds the family. A provider that
 * throws is logged and the server values apply.
 */
@FunctionalInterface
public interface RefreshTokenPolicyProvider {

  /**
   * @return the lifetime of the family, empty for the server values
   */
  Optional<Policy> policy(Request request);

  /**
   * What the decision may depend on.
   *
   * @param clientId        the client the tokens are issued to
   * @param client          its configuration
   * @param sub             uid of the user
   * @param identityId      id of the user, null for a family stored before it
   *                        was kept
   * @param familyStartedAt first token of the family (now at a code exchange)
   */
  record Request(String clientId, MtacOidcClient client, String sub, Integer identityId, Instant familyStartedAt) {
  }

  /**
   * Lifetime of a refresh token family.
   *
   * @param slidingTtl each refresh token expires that long after it was issued
   *                   (a family unused that long ends); must be positive
   * @param maxAge     the family ends that long after its first token, however
   *                   often it is used; null = no maximum
   */
  record Policy(Duration slidingTtl, Duration maxAge) {
    public Policy {
      if (slidingTtl == null || slidingTtl.isNegative() || slidingTtl.isZero()) {
        throw new IllegalArgumentException("The sliding lifetime of a refresh token must be positive");
      }
      if (maxAge != null && (maxAge.isNegative() || maxAge.isZero())) {
        throw new IllegalArgumentException("The maximum age of a refresh token family must be positive or null");
      }
    }

    /** A family that lives as long as it is used at least once every {@code slidingTtl}. */
    public static Policy sliding(Duration slidingTtl) {
      return new Policy(slidingTtl, null);
    }
  }
}
