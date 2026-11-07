package net.oneki.mtac.model.core.openid.oidc;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A consent given to an OpenID client, as listed for a super admin. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OidcClientConsent {
  private String identityUid;
  private String identityLabel; // email of the user
  private List<String> scopes;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant lastUsedAt;
}
