package net.oneki.mtac.model.core.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.openid.oidc.OidcAccessMode;
import net.oneki.mtac.model.core.openid.oidc.OidcClientKind;
import net.oneki.mtac.model.core.openid.oidc.OidcClientSource;
import net.oneki.mtac.model.core.openid.oidc.OidcConsentMode;

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
   * OpenID Connect provider (authorization code flow with PKCE). It is active
   * when enabled or as soon as one yml client is declared; the issuer is
   * {@code mtac.jwt.issuer}. Clients come from yml, from iam.oidc.client
   * resources, from dynamic registration or from metadata documents.
   */
  @Data
  public static class MtacOpenIdServer {
    private boolean enabled = false;
    private boolean corsEnabled = false; // true when no reverse proxy adds the CORS headers (local development)
    private int authorizationCodeTtlSec = 60;
    private int accessTokenTtlSec = 300; // also the lifetime of the id token
    // sliding: each refresh token expires that long after it was issued (a family unused that long ends)
    private int refreshTokenTtlSec = 28800; // 8 hours
    // absolute: a family ends that long after its first token, however often it is used; 0 = refreshTokenTtlSec
    private int refreshTokenMaxAgeSec = 0;
    private int loginMaxAttempts = 5; // failed attempts before the login is locked
    private int loginLockSec = 300;
    // a new consent needs an authentication this recent (seconds); older, the user proves himself again first
    private int consentStepUpSec = 900;
    private String tenantsScope = "tenants"; // scope that adds the tenant / role claims
    private String templatesPath = "mtac/templates/oidc"; // classpath folder of the hosted pages (login, mfa, logout, error)
    private String totpIssuerName = "MTAC"; // name shown by the authenticator application
    private MtacOidcSession session = new MtacOidcSession();
    private Map<String, MtacOidcClient> clients = new LinkedHashMap<>(); // key = client_id
    // text shown on the consent page for each scope; a scope without label is shown as is
    private Map<String, String> scopeLabels = new LinkedHashMap<>();
    private MtacOidcDynamic dynamic = new MtacOidcDynamic();
    private MtacOidcCimd cimd = new MtacOidcCimd();
  }

  /**
   * Policy forced on the clients nobody created by hand: registered through
   * Dynamic Client Registration (RFC 7591) or described by a Client ID Metadata
   * Document.
   */
  @Data
  public static class MtacOidcDynamicPolicy {
    private List<String> scopes = new ArrayList<>(Arrays.asList("openid", "profile", "email", "offline_access", "fuzz:read")); // maximum scopes
    private List<String> resources = new ArrayList<>(Arrays.asList("mcp")); // audience keys of mtac.jwt.audiences
    private boolean refreshToken = true;
    // false: a user without MFA may connect an agent; a user who enabled MFA is asked for the code at sign-in anyway
    private boolean requireMfa = false;
    private int sessionMaxDays = 30; // absolute maximum of a refresh token family of these clients
  }

  /** Dynamic Client Registration (RFC 7591), option d. */
  @Data
  public static class MtacOidcDynamic {
    private boolean dcrEnabled = false; // advertises registration_endpoint when true
    // HTTPS hosts a registered redirect URI may use; loopback URIs are always accepted
    private List<String> allowedRedirectHosts = new ArrayList<>();
    // private-use URI schemes (RFC 8252 section 7.1) a redirect URI of a native application may use, such as cursor;
    // also accepted for the clients a super admin creates. http, https and the schemes a browser runs are never allowed
    private List<String> allowedRedirectSchemes = new ArrayList<>();
    private int registrationsPerIpPerHour = 20;
    private int unusedClientTtlDays = 30; // registered clients never used for that long are deleted
    private MtacOidcDynamicPolicy policy = new MtacOidcDynamicPolicy();
  }

  /** Client ID Metadata Documents (client_id is an HTTPS URL), option c. */
  @Data
  public static class MtacOidcCimd {
    private boolean enabled = false; // advertises client_id_metadata_document_supported when true
    private List<String> allowedHosts = new ArrayList<>(); // hosts a client_id URL may use, compared as exact strings
    private int minCacheSec = 300;
    private int maxCacheSec = 86400;
    private MtacOidcDynamicPolicy policy = new MtacOidcDynamicPolicy();
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
    private OidcClientKind kind; // kind of application, informative for yml clients (null = not stated)
    private String name; // shown on the login page
    private String secretHash; // bcrypt hash, confidential clients only
    private List<String> secretHashes = new ArrayList<>(); // more accepted hashes (secret rotation of resource clients)
    // private_key_jwt (CIMD clients only): the client signs a client_assertion with one of these keys instead of
    // sending a secret; exactly one of jwks (inline JWK Set) and jwksUri is set, both null for every other client
    private String jwks;
    private String jwksUri;
    private List<String> redirectUris = new ArrayList<>(); // compared as exact strings, except loopback ones
    private List<String> postLogoutRedirectUris = new ArrayList<>();
    private List<String> allowedOrigins = new ArrayList<>(); // origins a public client may call the token endpoint from
    private List<String> scopes = new ArrayList<>(Arrays.asList("openid"));
    private boolean refreshToken = false;
    private boolean requireMfa = false;
    // a registered http://localhost|127.0.0.1|[::1] redirect URI matches any port (RFC 8252 section 7.3)
    private boolean loopback = false;
    private OidcConsentMode consentMode = OidcConsentMode.TRUSTED; // yml clients are first-party
    private OidcAccessMode accessMode = OidcAccessMode.IDENTITY;
    private List<String> resources = new ArrayList<>(); // audience keys of mtac.jwt.audiences, API access mode only
    // audience keys this first-party client obtains without the API access mode (yml clients only, today "mobile"):
    // its tokens get the first one when the authorization request names no resource. Never set for a client of
    // another source (iam.oidc.client, DCR, CIMD): the registry drops it and the validator refuses the audience
    private List<String> firstPartyAudiences = new ArrayList<>();
    private Integer sessionMaxDays; // maximum age of a refresh token family; null = refreshTokenMaxAgeSec only
    // lifetime of the access (and id) tokens of this client (yml clients only); null = accessTokenTtlSec of the server.
    // A long one stays revocable within about 30 s through the revocation list (TokenRevocationService)
    private Integer accessTokenTtlSec;
    // yml clients only: every authorization shows the hosted sign-in, an existing SSO session is never reused, and
    // the sign-in leaves no SSO session behind (no cookie; the session only carries the code until it is exchanged).
    // For first-party native applications whose private-use redirect scheme another application could register
    // (RFC 8252 section 8.6). Also applies, without the flag, to a trusted yml client whose redirect URIs all use a
    // private-use scheme (see OidcAuthorizationService.ignoresSsoSession). Requires consent-mode trusted
    private boolean ignoreSsoSession = false;
    // yml clients only: false removes a client declared in a shared file (application.yml) from one profile, since
    // Spring merges the client maps of the profiles
    private boolean enabled = true;
    private OidcClientSource source = OidcClientSource.YML;
    private String uid; // uid of the iam.oidc.client resource, null for yml and CIMD clients
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
    private MtacBrowserSession browserSession = new MtacBrowserSession();
  }

  /**
   * Sign-in of the Console (a browser application served with the API) through
   * the OpenID provider, the API being the client (backend for frontend): the
   * API exchanges the code and keeps the session in HttpOnly cookies, no token
   * ever reaches the browser storage.
   */
  @Data
  public static class MtacBrowserSession {
    private boolean enabled = false;
    private String clientId = "fuzz-console"; // OpenID client of the Console (yml client, public, PKCE, trusted)
    private String redirectUri; // <console>/api/auth/callback, a redirect URI of the client
    private String postLoginPath = "/login/callback"; // Console route the callback redirects to
    private String postLoginErrorPath = "/login"; // Console route shown with ?error=<code>
    private List<String> origins = new ArrayList<>(); // Console origins (scheme://host[:port]): CSRF, WebSocket, post-logout
    private String sessionCookieName = "fuzz.session"; // opaque Console session (refresh) token
    private String transactionCookieName = "fuzz.auth_tx"; // state + PKCE verifier between login and callback
    private boolean secure = true; // Secure attribute of the cookies (false only for http://localhost)
    private int renewBeforeSec = 60; // the access token cookie is renewed when it expires within that delay
    private boolean passwordLoginEnabled = false; // former Console login (/api/login, /api/totp/verify), transition only
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
    private int refreshExpirationSec = 2592000; // 30 days: a Console refresh token unused that long expires
    private int refreshMaxAgeSec = 7776000; // 90 days: a Console refresh token expires that long after the login
    private String secretKey = "secret";
    private String keystorePath;
    private String keystorePassword;
    private String keystoreKeyAlias;
    private int mfaDelaySec = Constants.MFA_DEFAULT_DELAY;
    private int resetPasswordExpirationSec = 86400; // 1 day
    // audience written in the access tokens, by key (see TokenAudience): console, api, mcp
    private Map<String, String> audiences = new LinkedHashMap<>();
    private AudienceEnforcement audienceEnforcement = AudienceEnforcement.log;
  }

  /**
   * log: a token without audience, or with the audience of another resource
   * server path, is still accepted and logged (migration). strict: refused.
   */
  public enum AudienceEnforcement {
    log, strict
  }

}
