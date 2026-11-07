package net.oneki.mtac.core.service.openid;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.oidc.OidcSessionService;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.ConsoleRefreshToken;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;
import net.oneki.mtac.model.core.util.exception.ForbiddenException;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.identity.user.User;

/**
 * Console session kept by the API in HttpOnly cookies (backend for frontend,
 * {@code mtac.iam.browser-session}): opened by the OpenID callback, renewed by
 * the cookie filter, closed by the logout. The Console session
 * ({@link ConsoleSessionService}) is linked to the SSO session of the OpenID
 * provider it comes from.
 *
 * <p>An impersonation started from a Console session is kept by the session
 * row ({@code impersonated_identity_id}, {@code impersonation_expires_at}):
 * while it lasts, every renewal mints an impersonation token (sub = the
 * impersonated user, claims {@code imp} and {@code onbehalf}, same
 * {@code csid}); once it has expired, the renewal clears it and mints the
 * token of the session user again.
 */
@Slf4j
@Service
public class BrowserSessionService {
  private final MtacProperties mtacProperties;
  private final JwtTokenService tokenService;
  private final ConsoleSessionService consoleSessions;
  private final OidcSessionService oidcSessions;
  private OpenIdService openIdService;
  private Clock clock = Clock.systemUTC();

  public BrowserSessionService(MtacProperties mtacProperties, JwtTokenService tokenService,
      ConsoleSessionService consoleSessions, OidcSessionService oidcSessions) {
    this.mtacProperties = mtacProperties;
    this.tokenService = tokenService;
    this.consoleSessions = consoleSessions;
    this.oidcSessions = oidcSessions;
  }

  /** MTAC default; the application's one is set by {@link #use} (its OpenIdController). */
  @Autowired
  public void setOpenIdService(OpenIdService openIdService) {
    this.openIdService = openIdService;
  }

  /** Makes the service find the users through the application's user service. */
  public void use(OpenIdService openIdService) {
    this.openIdService = openIdService;
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  /**
   * A signed-in browser: the Console access token, the Console session token
   * and the lifetimes of their cookies.
   */
  public record SignedIn(String sessionId, String accessToken, int accessTokenTtlSec, String sessionToken,
      long sessionMaxAgeSec) {
  }

  /**
   * A renewed access token, and the new remaining lifetime of the session
   * cookie.
   *
   * @param impersonationMaxAgeSec the impersonation indicator cookie to set:
   *                               null leaves it alone, 0 clears it (the
   *                               impersonation just ended), otherwise the
   *                               remaining lifetime of the impersonation
   */
  public record Renewed(String sessionId, String accessToken, int accessTokenTtlSec, long sessionMaxAgeSec,
      Long impersonationMaxAgeSec) {
    public Renewed(String sessionId, String accessToken, int accessTokenTtlSec, long sessionMaxAgeSec) {
      this(sessionId, accessToken, accessTokenTtlSec, sessionMaxAgeSec, null);
    }
  }

  /**
   * The access token of a Console session whose impersonation just started or
   * ended, and the lifetime of the impersonation indicator cookie (0: cleared).
   */
  public record ImpersonationChange(String accessToken, int accessTokenTtlSec, long impersonationMaxAgeSec) {
  }

  /** The user of an OpenID subject, null when it is not a user (an application, a deleted user). */
  public User user(String sub) {
    if (sub == null) {
      return null;
    }
    try {
      return openIdService.getUserService().getByUidUnsecure(sub) instanceof User user ? user : null;
    } catch (RuntimeException e) {
      log.warn("Browser session: no user for sub {}: {}", sub, e.getMessage());
      return null;
    }
  }

  /**
   * Opens the Console session of a user who signed in through the OpenID
   * provider, linked to the SSO session {@code ssoSessionId}.
   */
  public SignedIn open(User user, String ssoSessionId) {
    var issued = consoleSessions.open(user.getId(), ssoSessionId);
    // the claims of the user are computed again, as a password login does
    openIdService.getUserService().userinfo(user.getUid(), true);
    var ttl = accessTokenTtl(user);
    return new SignedIn(issued.sessionId(), accessToken(user, issued.sessionId(), ttl), ttl, issued.refreshToken(),
        secondsUntil(issued.expiresAt()));
  }

  /**
   * Renews the access token from the Console session token of the cookie: the
   * session must be live and its SSO session too; the session then slides and
   * the SSO session records the activity.
   *
   * @return null when the session can no longer be used
   */
  public Renewed renew(String sessionToken) {
    if (!ConsoleSessionService.isSessionToken(sessionToken)) {
      return null;
    }
    var session = consoleSessions.find(sessionToken);
    if (session == null) {
      return null;
    }
    var user = user(Resource.toUid(session.getIdentityId()));
    if (user == null) {
      return null;
    }
    // every check passed: only now does the session slide
    consoleSessions.slide(session);
    var ttl = accessTokenTtl(user);
    var impersonated = impersonatedUser(session);
    if (impersonated != null) {
      // the impersonation goes on: same token as at its start, never beyond its end
      var remaining = secondsUntil(session.getImpersonationExpiresAt());
      var impersonationTtl = (int) Math.min(ttl, remaining);
      return new Renewed(session.getId(), impersonationToken(impersonated, user, session.getId(), impersonationTtl),
          impersonationTtl, secondsUntil(session.getExpiresAt()), remaining);
    }
    Long indicator = null;
    if (session.getImpersonatedIdentityId() != null || session.getImpersonationExpiresAt() != null) {
      // expired (or the impersonated user no longer exists): back to the session user
      log.info("Console session {}: impersonation of identity {} ended", session.getId(),
          session.getImpersonatedIdentityId());
      consoleSessions.stopImpersonation(session);
      indicator = 0L;
    }
    return new Renewed(session.getId(), accessToken(user, session.getId(), ttl), ttl,
        secondsUntil(session.getExpiresAt()), indicator);
  }

  /**
   * The live Console session of the session cookie of a request, provided it
   * is the session of the access token of that request ({@code csid} claim);
   * null otherwise (no browser session, former password login, another
   * session).
   */
  public ConsoleRefreshToken sessionOf(HttpServletRequest request, String consoleSessionId) {
    if (!mtacProperties.getIam().getBrowserSession().isEnabled()) {
      return null;
    }
    var cookie = WebUtils.getCookie(request, mtacProperties.getIam().getBrowserSession().getSessionCookieName());
    return sessionOf(cookie != null ? cookie.getValue() : null, consoleSessionId);
  }

  /** See {@link #sessionOf(HttpServletRequest, String)}. */
  public ConsoleRefreshToken sessionOf(String sessionToken, String consoleSessionId) {
    if (consoleSessionId == null || !ConsoleSessionService.isSessionToken(sessionToken)) {
      return null;
    }
    var session = consoleSessions.find(sessionToken);
    return session != null && consoleSessionId.equals(session.getId()) ? session : null;
  }

  /**
   * The user of the session starts impersonating {@code target} for
   * {@link ImpersonateService#IMPERSONATION_SEC} seconds (the caller checked
   * the permission and refused a nested impersonation): stored on the session
   * row, so that the renewals keep impersonating.
   *
   * @return the impersonation token, null when the session can no longer be
   *         used (revoked meanwhile, user gone)
   * @throws ForbiddenException when the session already impersonates someone
   */
  public ImpersonationChange startImpersonation(ConsoleRefreshToken session, User target) {
    if (impersonatedUser(session) != null) {
      // the token of the request should already be an impersonation token: no nested impersonation
      throw new ForbiddenException("You are already impersonating a user.");
    }
    var user = user(Resource.toUid(session.getIdentityId()));
    if (user == null) {
      return null;
    }
    var expiresAt = clock.instant().plusSeconds(ImpersonateService.IMPERSONATION_SEC);
    if (!consoleSessions.startImpersonation(session, target.getId(), expiresAt)) {
      return null;
    }
    log.info("Console session {}: {} impersonates {} until {}", session.getId(), user.getLabel(), target.getLabel(),
        expiresAt);
    var ttl = (int) Math.min(accessTokenTtl(user), ImpersonateService.IMPERSONATION_SEC);
    return new ImpersonationChange(impersonationToken(target, user, session.getId(), ttl), ttl,
        ImpersonateService.IMPERSONATION_SEC);
  }

  /**
   * Ends the impersonation of the session (whether one is in progress or not)
   * and gives the token of the session user back.
   *
   * @return null when the session user no longer exists
   */
  public ImpersonationChange stopImpersonation(ConsoleRefreshToken session) {
    consoleSessions.stopImpersonation(session);
    var user = user(Resource.toUid(session.getIdentityId()));
    if (user == null) {
      return null;
    }
    var ttl = accessTokenTtl(user);
    return new ImpersonationChange(accessToken(user, session.getId(), ttl), ttl, 0);
  }

  /** The user impersonated by the session, null when none, expired or no longer a user. */
  User impersonatedUser(ConsoleRefreshToken session) {
    if (session.getImpersonatedIdentityId() == null || session.getImpersonationExpiresAt() == null
        || secondsUntil(session.getImpersonationExpiresAt()) <= 0) {
      return null;
    }
    return user(Resource.toUid(session.getImpersonatedIdentityId()));
  }

  /**
   * Impersonation token of a Console session: the claims of
   * {@link ImpersonateService}, plus the Console session id.
   */
  public String impersonationToken(User target, User sessionUser, String sessionId, int ttl) {
    var claims = ImpersonateService.impersonationClaims(sessionUser.getLabel());
    claims.put(ConsoleSessionService.CLAIM, sessionId);
    return tokenService.generateAccessToken(target.getUid(), target.getLabel(), ttl, claims, TokenAudience.CONSOLE);
  }

  /**
   * Logout of the browser: closes the Console session of the cookie and ends the
   * SSO session it comes from (which closes the other Console sessions opened
   * from it). The connector grants do not depend on the SSO session.
   */
  public void logout(String sessionToken) {
    if (!ConsoleSessionService.isSessionToken(sessionToken)) {
      return;
    }
    var session = consoleSessions.lookup(sessionToken);
    if (session == null) {
      return;
    }
    consoleSessions.close(session.getId());
    if (session.getSsoSessionId() != null) {
      oidcSessions.endById(session.getSsoSessionId());
    }
  }

  /**
   * A new sign-in of a browser that still had a Console session cookie: closes
   * that previous Console session only (its SSO session goes on, the new
   * sign-in may come from it), so that the replaced cookie value cannot be used
   * any more.
   */
  public void closePrevious(String sessionToken) {
    if (!ConsoleSessionService.isSessionToken(sessionToken)) {
      return;
    }
    var session = consoleSessions.lookup(sessionToken);
    if (session != null && session.getRevokedAt() == null) {
      consoleSessions.close(session.getId());
    }
  }

  /** Same lifetime as the tokens of a password login. */
  public int accessTokenTtl(User user) {
    return user.getTokenExpiresIn() != null ? user.getTokenExpiresIn() : mtacProperties.getJwt().getExpirationSec();
  }

  /** Same claims as the tokens of a password login: the Console session id and the console audience. */
  public String accessToken(User user, String sessionId, int ttl) {
    return tokenService.generateAccessToken(user.getUid(), user.getLabel(), ttl,
        Map.of(ConsoleSessionService.CLAIM, sessionId), TokenAudience.CONSOLE);
  }

  private long secondsUntil(Instant instant) {
    if (instant == null) {
      return 0;
    }
    return Math.max(0, Duration.between(clock.instant(), instant).getSeconds());
  }
}
