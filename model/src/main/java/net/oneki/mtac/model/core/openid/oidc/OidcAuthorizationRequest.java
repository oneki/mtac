package net.oneki.mtac.model.core.openid.oidc;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A validated request to the authorization endpoint. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OidcAuthorizationRequest {
  private String clientId;
  private String redirectUri;
  private Set<String> scopes;
  private String state;
  private String nonce;
  private String codeChallenge;
  private Set<String> prompt;
  private Integer maxAge;
  private String loginHint;
}
