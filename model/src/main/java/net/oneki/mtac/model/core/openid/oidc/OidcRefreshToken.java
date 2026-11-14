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
  private String resource; // audience key of the family (RFC 8707), null = identity only
  private Instant familyStartedAt; // first token of the family, bounds it with the client's sessionMaxDays
  // identity of the family: a refresh no longer needs the SSO session (null when ended), the grant lives on its own
  private Integer identityId;
  private String sub;
  private Instant authTime;
  private java.util.List<String> amr;
  // hash of the refresh token this one replaced at a refresh; null for the first token of a family (code
  // exchange) and for the tokens stored before the column existed. Finds the successor of a rotated token
  // (grace window of a reused refresh token, refreshTokenReuseGraceSec)
  private String previousHash;
}
