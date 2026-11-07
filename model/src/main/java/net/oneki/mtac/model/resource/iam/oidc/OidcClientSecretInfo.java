package net.oneki.mtac.model.resource.iam.oidc;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Public view of an {@link OidcClientSecret}: everything but the hash. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcClientSecretInfo {
  private String id;
  private String hint;
  private Instant createdAt;

  public static OidcClientSecretInfo of(OidcClientSecret secret) {
    return OidcClientSecretInfo.builder()
        .id(secret.getId())
        .hint(secret.getHint())
        .createdAt(secret.getCreatedAt())
        .build();
  }
}
