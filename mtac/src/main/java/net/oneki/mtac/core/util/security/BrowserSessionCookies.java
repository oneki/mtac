package net.oneki.mtac.core.util.security;

import java.util.List;

import org.springframework.http.ResponseCookie;

import net.oneki.mtac.model.core.config.MtacProperties;

/**
 * The {@code Set-Cookie} values of the Console (browser session, see
 * {@link MtacProperties.MtacBrowserSession}), built in one place so that
 * every writer sets the same attributes:
 * <ul>
 * <li>access token ({@code mtac.iam.access-token-cookie-name}) and session
 * ({@code session-cookie-name}): HttpOnly, SameSite=Strict, Path=/,
 * Max-Age = token lifetime / session remaining lifetime;</li>
 * <li>transaction ({@code transaction-cookie-name}, between
 * {@code /auth/login} and {@code /auth/callback}): HttpOnly, SameSite=Lax (the
 * callback is a navigation coming from the OpenID provider), Path =
 * {@code <apiBasePath>/auth}, or Path=/ for a {@code __Host-} name (the prefix
 * requires it);</li>
 * <li>impersonation indicator ({@code mtac.iam.impersonate-active-cookie-name},
 * value {@code true}): NOT HttpOnly (the Console header reads it), set by the
 * API for the remaining lifetime of the impersonation, SameSite=Strict,
 * Path=/;</li>
 * <li>{@code Secure} on all of them when the browser session is enabled and
 * {@code secure} is true (false only for http://localhost).</li>
 * </ul>
 *
 * <p>Over HTTPS the names should carry the {@code __Host-} prefix: the browser
 * then refuses a cookie of that name set by a sibling host with
 * {@code Domain=} (cookie tossing: signing the victim into the attacker's
 * account, fixing its session). The prefix requires {@code Secure} and Path=/,
 * so http://localhost keeps plain names.
 */
public class BrowserSessionCookies {
  public static final String SAME_SITE_STRICT = "Strict";
  public static final String SAME_SITE_LAX = "Lax";
  /** Path of the transaction cookie, under the API base path. */
  public static final String TRANSACTION_PATH = "/auth";
  /** Cookie name prefix: Secure, Path=/, no Domain (RFC 6265bis). */
  public static final String HOST_PREFIX = "__Host-";

  private final MtacProperties mtacProperties;

  public BrowserSessionCookies(MtacProperties mtacProperties) {
    this.mtacProperties = mtacProperties;
  }

  /**
   * Secure attribute: set when the browser session is enabled and configured
   * secure. An application without browser session keeps its former cookies
   * (no Secure), which may be served over plain HTTP.
   */
  public boolean isSecure() {
    var config = config();
    return config.isEnabled() && config.isSecure();
  }

  /** Console access token. {@code maxAgeSec} null: a browser session cookie (former behaviour). */
  public String accessToken(String token, Integer maxAgeSec) {
    return cookie(iam().getAccessTokenCookieName(), token, maxAgeSec != null ? maxAgeSec : -1, SAME_SITE_STRICT,
        "/", true);
  }

  /** Opaque Console session (refresh) token, for the remaining lifetime of the session. */
  public String session(String token, long maxAgeSec) {
    return cookie(config().getSessionCookieName(), token, Math.max(0, maxAgeSec), SAME_SITE_STRICT, "/", true);
  }

  /** State, PKCE verifier and nonce between the login redirect and the callback. */
  public String transaction(String value, long maxAgeSec) {
    return cookie(config().getTransactionCookieName(), value, maxAgeSec, SAME_SITE_LAX, transactionPath(), true);
  }

  /**
   * Former impersonation cookie holding the original access token, only
   * without a Console session (former password login): with one, the session
   * keeps the impersonation.
   */
  public String impersonationToken(String token) {
    return cookie(iam().getImpersonateTokenCookieName(), token, -1, SAME_SITE_STRICT, "/", true);
  }

  public String clearImpersonationToken() {
    return cookie(iam().getImpersonateTokenCookieName(), "", 0, SAME_SITE_STRICT, "/", true);
  }

  /**
   * Impersonation indicator read by the Console ({@code true}), for the
   * remaining lifetime of the impersonation; {@code maxAgeSec} 0 clears it.
   */
  public String impersonationActive(long maxAgeSec) {
    if (maxAgeSec <= 0) {
      return clearImpersonationActive();
    }
    return cookie(iam().getImpersonateActiveCookieName(), "true", maxAgeSec, SAME_SITE_STRICT, "/", false);
  }

  public String clearImpersonationActive() {
    return cookie(iam().getImpersonateActiveCookieName(), "", 0, SAME_SITE_STRICT, "/", false);
  }

  public String clearAccessToken() {
    return cookie(iam().getAccessTokenCookieName(), "", 0, SAME_SITE_STRICT, "/", true);
  }

  public String clearSession() {
    return cookie(config().getSessionCookieName(), "", 0, SAME_SITE_STRICT, "/", true);
  }

  public String clearTransaction() {
    return cookie(config().getTransactionCookieName(), "", 0, SAME_SITE_LAX, transactionPath(), true);
  }

  /** The impersonation cookies: the former original token (HttpOnly) and the indicator read by the Console. */
  public List<String> clearImpersonation() {
    return List.of(clearImpersonationToken(), clearImpersonationActive());
  }

  public String transactionPath() {
    var name = config().getTransactionCookieName();
    return name != null && name.startsWith(HOST_PREFIX) ? "/" : mtacProperties.getApiBasePath() + TRANSACTION_PATH;
  }

  /**
   * @param maxAgeSec negative: no Max-Age (deleted when the browser closes); 0:
   *                  deletes the cookie (Max-Age=0 and an Expires in the past)
   */
  public String cookie(String name, String value, long maxAgeSec, String sameSite, String path, boolean httpOnly) {
    return ResponseCookie.from(name, value)
        .path(path)
        .maxAge(maxAgeSec)
        .httpOnly(httpOnly)
        .secure(isSecure())
        .sameSite(sameSite)
        .build()
        .toString();
  }

  private MtacProperties.MtacIam iam() {
    return mtacProperties.getIam();
  }

  private MtacProperties.MtacBrowserSession config() {
    return mtacProperties.getIam().getBrowserSession();
  }
}
