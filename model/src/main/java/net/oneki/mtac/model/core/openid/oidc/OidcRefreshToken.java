package net.oneki.mtac.model.core.openid.oidc;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OidcRefreshToken {
  private String tokenHash; // SHA-256 of the token, the raw value is never stored
  private String familyId; // hash of the authorization code the family started from
  private String clientId;
  private String sessionId;
  private String scope;
  private Instant expiresAt;
  private Instant rotatedAt; // set when the token was exchanged for its successor
  private Instant revokedAt;
}
