package net.oneki.mtac.model.core.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

  /**
   * OpenID Connect provider (authorization code flow with PKCE). It is active as
   * soon as one client is declared; the issuer is {@code mtac.jwt.issuer}.
   */
  @Data
  public static class MtacOpenIdServer {
    private boolean enabled = false;
    private boolean corsEnabled = false; // true when no reverse proxy adds the CORS headers (local development)
    private int authorizationCodeTtlSec = 60;
    private int accessTokenTtlSec = 300; // also the lifetime of the id token
    private int refreshTokenTtlSec = 28800; // 8 hours, never longer than the SSO session
    private int loginMaxAttempts = 5; // failed attempts before the login is locked
    private int loginLockSec = 300;
    private String tenantsScope = "tenants"; // scope that adds the tenant / role claims
    private String templatesPath = "mtac/templates/oidc"; // classpath folder of the hosted pages (login, mfa, logout, error)
    private String totpIssuerName = "MTAC"; // name shown by the authenticator application
    private MtacOidcSession session = new MtacOidcSession();
    private Map<String, MtacOidcClient> clients = new LinkedHashMap<>(); // key = client_id
  }

  @Data
  public static class MtacOidcSession {
    private String cookieName = "__Host-mtac_sso";
    private boolean secure = true;
    private int idleTimeoutSec = 28800; // 8 hours
    private int maxAgeSec = 43200; // 12 hours
  }

  @Data
  public static class MtacOidcClient {
    public enum ClientType {
      PUBLIC, CONFIDENTIAL
    }
    private ClientType type = ClientType.CONFIDENTIAL;
    private String name; // shown on the login page
    private String secretHash; // bcrypt hash, confidential clients only
    private List<String> redirectUris = new ArrayList<>(); // compared as exact strings
    private List<String> postLogoutRedirectUris = new ArrayList<>();
    private List<String> allowedOrigins = new ArrayList<>(); // origins a public client may call the token endpoint from
    private List<String> scopes = new ArrayList<>(Arrays.asList("openid"));
    private boolean refreshToken = false;
    private boolean requireMfa = false;
  }

  @Data
  public static class MtacIam {
    public enum TokenLocation {
      header, request_param, cookie
    }
    private List<TokenLocation> tokenLocation = Arrays.asList(new TokenLocation[]{TokenLocation.header});
    private String accessTokenCookieName = "mtac";
    private String impersonateTokenCookieName = "mtac_impersonate";
    private String impersonateActiveCookieName = "mtac_impersonate_active";
    private String requestParamTokenName = "mtac_token";
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
    private Integer requestCooldownSec = 10; // to avoid spam, user can only request reset password once every X seconds
  }

  @Data
  public static class MtacJwt {
    private String issuer = "mtac";
    private List<String> acceptedIssuers = new ArrayList<>(); // former issuers whose tokens are still accepted
    private int expirationSec = 900; // 15 minutes
    private int clockSkewSec = 300; // 5 minutes
    private int refreshExpirationSec = 2592000; // 30 days
    private String secretKey = "secret";
    private String keystorePath;
    private String keystorePassword;
    private String keystoreKeyAlias;
    private int mfaDelaySec = Constants.MFA_DEFAULT_DELAY;
    private int resetPasswordExpirationSec = 86400; // 1 day
  }

}
