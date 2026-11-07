package net.oneki.mtac.model.resource.iam.oidc;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One accepted secret of a confidential client. Two may coexist during a
 * rotation. The hash must never leave the server: the API shows
 * {@link OidcClientSecretInfo} instead.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcClientSecret {
  private String id; // short random id, used to remove the secret
  @ToString.Exclude
  private String hash; // bcrypt hash
  private String hint; // last 4 characters of the secret, shown in the Console
  private Instant createdAt;
}
