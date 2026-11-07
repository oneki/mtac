package net.oneki.mtac.model.resource.iam.oidc;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Answer to {@code POST /api/oidc-clients/{uid}/secrets}: the new secret in
 * plain text, shown once.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcClientSecretResponse {
  @JsonProperty("client_id")
  private String clientId;
  @JsonProperty("client_secret")
  @ToString.Exclude
  private String clientSecret;
  private String id; // id of the new secret, used to remove it later
  private String hint;
  private Instant createdAt;
}
