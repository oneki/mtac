package net.oneki.mtac.model.core.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import net.oneki.mtac.model.core.Constants;

@Data
public class MtacProperties {
  private String baseUrl;
  private String apiBasePath = "/api";
  private MtacScan scan = new MtacScan();
  private MtacSqids sqids = new MtacSqids();
  private MtacOpenId openId = new MtacOpenId();
  private MtacIam iam = new MtacIam();
  private MtacResetPassword resetPassword = new MtacResetPassword();
  private MtacJwt jwt = new MtacJwt();

  @Data
  public static class MtacScan {
    private String basePackage;
  }

  @Data
  public static class MtacSqids {
    private String alphabet;
  }

  @Data
  public static class MtacOpenId {
    private MtacOpenIdServer server = new MtacOpenIdServer();
  }

  @Data static class MtacOpenIdServer {
    private boolean enabled = false;
  }

  @Data
  public static class MtacIam {
    private String tokenLocation = "header";
    private String cookieName = "mtac";
    private MtacIamTenantsInIdtoken tenantsInIdtoken = new MtacIamTenantsInIdtoken();
  }

  @Data
  public static class MtacIamTenantsInIdtoken {
    private boolean enabled = false;
    private List<String> schemaLabels = new ArrayList<>();
  }

  @Data
  public static class MtacResetPassword {
    private String link; // wetsite URL to reset password = the link sent by email
  }

  @Data
  public static class MtacJwt {
    private String issuer = "mtac";
    private int expirationSec = 900; // 15 minutes
    private int clockSkewSec = 300; // 5 minutes
    private int refreshExpirationSec = 2592000; // 30 days
    private String secretKey = "secret";
    private String keystorePath;
    private String keystorePassword;
    private String keystoreKeyAlias;
    private int mfaDelaySec = Constants.MFA_DEFAULT_DELAY;
    private int resetPasswordExpirationSec = 86400; // 1 hour
  }

}
