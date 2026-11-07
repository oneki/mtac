package net.oneki.mtac.model.core.openid;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A Console refresh token: one per login (browser). The token itself is never
 * stored, only its SHA-256. The id is the csid claim of the access tokens of
 * that Console session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsoleRefreshToken {
  private String id;
  private String tokenHash;
  private Integer identityId;
  private Instant createdAt;
  private Instant lastUsedAt;
  private Instant expiresAt;
  private Instant revokedAt;
  private String userAgent;
}
