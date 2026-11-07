package net.oneki.mtac.model.core.openid.oidc;

import java.time.Instant;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The scopes a user approved for an OpenID client on the consent page (clients
 * whose consent mode is not TRUSTED). One row per (identity, client).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OidcConsent {
  private Integer identityId;
  private String clientId;
  private Set<String> scopes;
  private Instant createdAt;
  private Instant updatedAt; // last time the user approved new scopes
  private Instant lastUsedAt; // last authorization code issued with this consent
  private String identityLabel; // label of the identity (email of a user), filled by the listings of a client only
}
