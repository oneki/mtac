package net.oneki.mtac.model.resource.iam.identity;

public interface hasResetPassword {
  public String getPassword();
  public void setPassword(String password);
  public String getResetPasswordToken();
  public void setResetPasswordToken(String resetPasswordToken);
}
