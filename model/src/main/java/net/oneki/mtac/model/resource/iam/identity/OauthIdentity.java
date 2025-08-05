package net.oneki.mtac.model.resource.iam.identity;

import net.oneki.mtac.model.core.framework.HasSchema;
import net.oneki.mtac.model.core.resource.HasId;
import net.oneki.mtac.model.core.resource.HasLabel;
import net.oneki.mtac.model.core.resource.HasUid;

public interface OauthIdentity extends HasLabel, HasId, HasUid, HasSchema{
  public String getPassword();
  public void setPassword(String password);
  public String getRefreshToken();
  public void setRefreshToken(String refreshToken);
  public Integer getTokenExpiresIn();
  public void setTokenExpiresIn(Integer expiresIn);
}
