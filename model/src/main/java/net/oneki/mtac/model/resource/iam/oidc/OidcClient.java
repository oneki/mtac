package net.oneki.mtac.model.resource.iam.oidc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.openid.oidc.OidcAccessMode;
import net.oneki.mtac.model.core.openid.oidc.OidcClientKind;
import net.oneki.mtac.model.core.openid.oidc.OidcClientSource;
import net.oneki.mtac.model.core.openid.oidc.OidcConsentMode;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;
import net.oneki.mtac.model.core.util.json.view.External;
import net.oneki.mtac.model.core.util.json.view.Internal;
import net.oneki.mtac.model.resource.Resource;

/**
 * An OpenID Connect client of the provider (authorization code + PKCE), created
 * by a super admin or by Dynamic Client Registration. It is global: it lives in
 * the root tenant and any user may sign in to it. The label is the client_id.
 *
 * <p>
 * The secret hashes never leave the server: {@link #secrets} carries the
 * {@link Internal} view, which the API mapper (MTAC {@code DtoModule}) drops
 * from every resource it writes, while the database mapper keeps it. The API
 * shows {@link #getSecretInfos()} instead (id, hint and date only), which
 * carries the {@link External} view so that it is never stored.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.oidc.client")
public class OidcClient extends Resource {
  private String name; // shown on the login and consent pages
  private String description;
  private OidcClientKind kind;
  private boolean confidential;
  @Builder.Default
  @JsonView(Internal.class)
  @ToString.Exclude
  private List<OidcClientSecret> secrets = new ArrayList<>(); // confidential clients only, at most 2
  @Builder.Default
  private List<String> redirectUris = new ArrayList<>();
  @Builder.Default
  private List<String> postLogoutRedirectUris = new ArrayList<>();
  @Builder.Default
  private List<String> allowedOrigins = new ArrayList<>();
  private boolean loopback;
  @Builder.Default
  private List<String> scopes = new ArrayList<>(); // maximum scopes the client may obtain
  private boolean refreshToken;
  private boolean requireMfa;
  @Builder.Default
  private OidcConsentMode consentMode = OidcConsentMode.REMEMBERED;
  @Builder.Default
  private OidcAccessMode accessMode = OidcAccessMode.IDENTITY;
  @Builder.Default
  private List<String> resources = new ArrayList<>(); // audience keys (TokenAudience), API access mode only
  private Integer sessionMaxDays;
  @Builder.Default
  private OidcClientSource source = OidcClientSource.CONSOLE;
  private Instant lastUsedAt; // last token issued, used to delete unused DCR clients

  /** The client_id, i.e. the label. Written to the API only. */
  @JsonView(External.class)
  @JsonProperty(value = "clientId", access = JsonProperty.Access.READ_ONLY)
  public String getClientId() {
    return getLabel();
  }

  /** What the Console may show of the secrets: never the hash. Written to the API only. */
  @JsonView(External.class)
  @JsonProperty(value = "secretInfos", access = JsonProperty.Access.READ_ONLY)
  public List<OidcClientSecretInfo> getSecretInfos() {
    var result = new ArrayList<OidcClientSecretInfo>();
    if (secrets != null) {
      for (var secret : secrets) {
        result.add(OidcClientSecretInfo.of(secret));
      }
    }
    return result;
  }
}
