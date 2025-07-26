package net.oneki.mtac.model.core.openid;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TokenResponse {
  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("id_token")
  private String idToken;

  @JsonProperty("refresh_token")
  private String refreshToken;

  @JsonProperty("expires_in")
  private Integer expiresIn;

  @JsonProperty("token_type")
  @Builder.Default private String tokenType = "Bearer";

  @JsonProperty("mfa_token")
  private String mfaToken;

  @JsonProperty("mfa_user")
  private String mfaUser;

  @JsonProperty("mfa_required")
  private Boolean mfaRequired;

  @JsonProperty("mfa_delay")
  private Integer mfaDelay;

  @JsonProperty("no_mfa_token")
  private String noMfaToken;

  @JsonProperty("no_mfa_token_expires_in")
  private Integer noMfaTokenExpiresIn;

  @JsonProperty("totp_secret")
  private String totpSecret;
}
