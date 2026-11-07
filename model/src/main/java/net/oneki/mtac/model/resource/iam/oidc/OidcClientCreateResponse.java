package net.oneki.mtac.model.resource.iam.oidc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;

/**
 * Answer to the creation of an OIDC client: the client plus, for a confidential
 * client, its secret in plain text. It is the only time the secret is shown;
 * nothing stores this object. It is an entity only because the API mapper
 * serializes resources from their registered description.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.oidc.client.response")
public class OidcClientCreateResponse extends OidcClient {
  private String client_id;
  @ToString.Exclude
  private String client_secret; // null for a public client
}
