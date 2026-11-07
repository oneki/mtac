package net.oneki.mtac.model.core.openid.oidc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A consent of the current user, as listed on the "Connected applications" page. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OidcUserConsent {
  private String clientId;
  private String clientName; // null when the client no longer exists
  private String clientHost; // host of the client_id URL (metadata document) or of its first redirect URI
  private boolean loopback; // every redirect URI of the client is on this computer (localhost)
  private List<String> scopes;
  private Map<String, String> scopeLabels; // scope -> text shown to the user
  private Instant createdAt;
  private Instant updatedAt;
  private Instant lastUsedAt;
}
