package net.oneki.mtac.model.core.openid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ImpersonateUserResponse {
  private String accessToken;
  private String originalAccessToken;
}
