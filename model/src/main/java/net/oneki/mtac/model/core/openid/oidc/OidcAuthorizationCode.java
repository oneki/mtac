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
public class OidcAuthorizationCode {
  private String codeHash; // SHA-256 of the code, the raw value is never stored
  private String clientId;
  private String sessionId;
  private String redirectUri;
  private String scope;
  private String nonce;
  private String codeChallenge;
  private Instant expiresAt;
  private Instant consumedAt;
}
