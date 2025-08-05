package net.oneki.mtac.model.resource.iam.identity;

public interface hasMfa {
  public Boolean getMfa();
  public void setMfa(Boolean mfa);
  public Boolean getMfaActive();
  public void setMfaActive(Boolean mfaActive);
  public String getVerificationCode();
  public void setVerificationCode(String verificationCode);
  public String getTotpSecret();
  public void setTotpSecret(String totpSecret);
}
