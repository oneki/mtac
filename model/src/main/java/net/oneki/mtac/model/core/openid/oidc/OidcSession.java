package net.oneki.mtac.model.core.openid.oidc;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single sign-on session of a user on the OpenID provider. It is created once
 * the user is fully authenticated (password, and second factor when required).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OidcSession {
  private String id; // SHA-256 of the cookie value, the raw value is never stored
  private Integer identityId;
  private String sub;
  private Instant authTime;
  private List<String> amr; // authentication methods: pwd, otp
  private Instant lastSeenAt;
  private Instant expiresAt;
}
