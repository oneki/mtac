package net.oneki.mtac.model.resource.iam.oidc;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.openid.oidc.OidcAccessMode;
import net.oneki.mtac.model.core.openid.oidc.OidcClientKind;
import net.oneki.mtac.model.core.openid.oidc.OidcConsentMode;
import net.oneki.mtac.model.core.util.introspect.annotation.ApiRequest;
import net.oneki.mtac.model.resource.UpsertRequest;

/**
 * Create or update an OIDC client. The client_id (label), the secrets and the
 * source are set by the server; kind and confidential cannot change after
 * creation.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiRequest("req.mtac.iam.oidc.client:upsert")
public class OidcClientUpsertRequest extends UpsertRequest {
  private String name;
  private String description;
  private OidcClientKind kind;
  private Boolean confidential;
  private List<String> redirectUris;
  private List<String> postLogoutRedirectUris;
  private List<String> allowedOrigins;
  private Boolean loopback;
  private List<String> scopes;
  private Boolean refreshToken;
  private Boolean requireMfa;
  private OidcConsentMode consentMode;
  private OidcAccessMode accessMode;
  private List<String> resources;
  private Integer sessionMaxDays;
}
