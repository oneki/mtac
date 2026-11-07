package net.oneki.mtac.api;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.MfaService;
import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.core.service.openid.ResetPasswordService;
import net.oneki.mtac.core.service.openid.oidc.LoginAttemptService;
import net.oneki.mtac.core.service.openid.oidc.OidcAuthorizationService;
import net.oneki.mtac.core.service.openid.oidc.OidcClientRegistry;
import net.oneki.mtac.core.service.openid.oidc.OidcConsentService;
import net.oneki.mtac.core.service.openid.oidc.OidcCrypto;
import net.oneki.mtac.core.service.openid.oidc.OidcPageRenderer;
import net.oneki.mtac.core.service.openid.oidc.OidcSessionService;
import net.oneki.mtac.core.service.openid.oidc.TotpQrCode;
import net.oneki.mtac.framework.repository.OidcConsentRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;
import net.oneki.mtac.model.core.openid.ResetPasswordRequest;
import net.oneki.mtac.model.core.openid.oidc.OidcAuthorizationRequest;
import net.oneki.mtac.model.core.openid.oidc.OidcException;
import net.oneki.mtac.model.core.openid.oidc.OidcSession;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.iam.identity.user.User;

/**
 * HTTP endpoints of the OpenID Connect provider, registered by
 * {@link OpenIdController} when at least one client is declared.
 *
 * <p>
 * Two kinds of endpoints live here. The interactive ones (authorize, login,
 * mfa, consent, logout) are visited by the browser of the user and rely on the
 * SSO session cookie. The others (token, userinfo, revoke, discovery) are called by
 * the clients, never read a cookie, and are the only ones that may be called
 * cross-origin (see {@link OidcCorsPreflightFilter} for the preflight requests).
 *
 * <p>
 * Consent (clients whose consent mode is not TRUSTED, see
 * {@link OidcConsentService}): once the user is signed in, the authorization
 * endpoint renders the consent page instead of issuing the code. The page
 * posts to {@value #PATH_CONSENT} with a signed consent context (audience
 * {@value #CONSENT_CONTEXT_AUDIENCE}, bound to the SSO session and to the
 * scopes shown) and the CSRF token. {@code prompt=none} answers
 * {@code consent_required} when a consent is missing. While the browser holds an
 * impersonation cookie of the Console, a non-trusted client is refused with
 * {@code access_denied}: an administrator acting as a user must not connect
 * applications in the name of that user.
 *
 * <p>
 * Step-up (see {@link OidcConsentService#isStepUpRequired}): when the consent
 * page is due and the authentication of the SSO session is too old, the user
 * proves himself again before the page, on the hosted pages of the login: the
 * MFA page (TOTP only) when he has MFA, else the login page with his username
 * fixed. Their login context then carries the id and the subject of the
 * session ({@value #STEP_UP_SESSION_CLAIM}, {@value #STEP_UP_SUBJECT_CLAIM}):
 * only the user of that session can complete it, and a success updates the
 * authentication time and methods of that same session (no new session, same
 * cookie) before the authorization request resumes. {@code prompt=none}
 * answers {@code login_required} when a step-up is needed.
 *
 * <p>
 * Password reset ({@value #PATH_FORGOT_PASSWORD}, {@value #PATH_RESET_PASSWORD}):
 * the login page links to the forgot password page with its login context.
 * That page always gives the same answer, known address or not, and a request
 * within the cooldown included (no account enumeration); the requests of an
 * address are limited by {@link LoginAttemptService}. The link of the email
 * opens the reset page, which checks the link (signature, expiration, last
 * link of the user) and sets the new password through
 * {@link ResetPasswordService#resetPassword}, which ends the sessions and the
 * delegated accesses of the user. When the request started from a sign-in, the
 * link also carries a return context (audience {@value #RESET_RETURN_AUDIENCE}):
 * the final "Sign in" button resumes that authorization request; otherwise it
 * opens the Console ({@code mtac.base-url}).
 */
@Component
@Slf4j
public class OidcEndpoints {
  private static final String LOGIN_CONTEXT_AUDIENCE = "oidc-login";
  public static final String CONSENT_CONTEXT_AUDIENCE = "oidc-consent";
  public static final String PATH_CONSENT = "/oauth2/consent";
  private static final String DECISION_ALLOW = "allow";
  private static final String DECISION_DENY = "deny";
  private static final int LOGIN_CONTEXT_TTL_SEC = 600;
  private static final String CSRF_COOKIE = "mtac_oidc_csrf";
  private static final String MFA_COOKIE = "mtac_oidc_mfa";
  private static final int MFA_COOKIE_TTL_SEC = 300;
  private static final String EXPIRED_REQUEST = "This sign-in request has expired. Go back to the application and try again.";
  private static final String STEP_UP_OTHER_USER = "Only the account that is signed in can confirm this request. "
      + "Go back to the application and try again.";
  /** Claims of a step-up login context: the SSO session to re-authenticate and its subject. */
  private static final String STEP_UP_SESSION_CLAIM = "su_sid";
  private static final String STEP_UP_SUBJECT_CLAIM = "su_sub";
  public static final String PATH_FORGOT_PASSWORD = "/oauth2/forgot-password";
  public static final String PATH_RESET_PASSWORD = "/oauth2/reset-password";
  /** Audience of the context carried by a reset link to resume a sign-in once the password is changed. */
  public static final String RESET_RETURN_AUDIENCE = "oidc-reset-return";
  /** Longest delay after which a reset still resumes the sign-in it started from. */
  private static final int RESET_RETURN_TTL_SEC = 3600;
  private static final int MAX_EMAIL_LENGTH = 254;
  private static final String TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes.";
  private static final String PAGE_EXPIRED = "This page has expired. Try again.";

  private final OidcAuthorizationService authorizationService;
  private final OidcSessionService sessionService;
  private final OidcClientRegistry clientRegistry;
  private final OidcPageRenderer pageRenderer;
  private final LoginAttemptService loginAttemptService;
  private final JwtTokenService tokenService;
  private final MtacProperties mtacProperties;
  private final OidcConsentService consentService;
  private OpenIdService openIdService;
  private MfaService mfaService;
  private OidcRegistrationEndpoint registrationEndpoint;
  private ResetPasswordService resetPasswordService;
  /** Runs the reset requests (lookup + email) off the request: the answer time does not tell whether the account exists. */
  private Executor resetRequestExecutor = task -> Thread.ofVirtual().name("oidc-reset-request").start(task);
  /** Reset requests in progress at most (lookup + email); beyond, a request is dropped with the same answer. */
  private static final int MAX_RESET_REQUESTS_IN_FLIGHT = 4;
  private final Semaphore resetRequestsInFlight = new Semaphore(MAX_RESET_REQUESTS_IN_FLIGHT);

  public OidcEndpoints(OidcAuthorizationService authorizationService, OidcSessionService sessionService,
      OidcClientRegistry clientRegistry, OidcPageRenderer pageRenderer, LoginAttemptService loginAttemptService,
      JwtTokenService tokenService, MtacProperties mtacProperties, OidcConsentService consentService) {
    this.authorizationService = authorizationService;
    this.sessionService = sessionService;
    this.clientRegistry = clientRegistry;
    this.pageRenderer = pageRenderer;
    this.loginAttemptService = loginAttemptService;
    this.tokenService = tokenService;
    this.mtacProperties = mtacProperties;
    this.consentService = consentService;
  }

  @Autowired
  public void setOpenIdService(OpenIdService openIdService) {
    this.openIdService = openIdService;
  }

  @Autowired
  public void setMfaService(MfaService mfaService) {
    this.mfaService = mfaService;
  }

  /** Dynamic Client Registration (RFC 7591); the endpoint itself answers 404 unless enabled. */
  @Autowired(required = false)
  public void setRegistrationEndpoint(OidcRegistrationEndpoint registrationEndpoint) {
    this.registrationEndpoint = registrationEndpoint;
  }

  /**
   * The reset password service of the application: the most specific one when
   * several are declared (an application subclass sends the emails, the MTAC
   * default only logs the link).
   */
  @Autowired(required = false)
  public void setResetPasswordServices(List<ResetPasswordService> services) {
    ResetPasswordService selected = null;
    var selectedDepth = -1;
    for (var service : services) {
      var depth = 0;
      for (Class<?> type = ClassUtils.getUserClass(service); type != null
          && type != ResetPasswordService.class; type = type.getSuperclass()) {
        depth++;
      }
      if (depth > selectedDepth) {
        selected = service;
        selectedDepth = depth;
      }
    }
    this.resetPasswordService = selected;
  }

  /** Makes the hosted reset pages use this service (the one the OpenIdController of the application returns). */
  public void useResetPasswordService(ResetPasswordService resetPasswordService) {
    this.resetPasswordService = resetPasswordService;
  }

  /** Where the reset requests run; tests run them in the calling thread. */
  public void setResetRequestExecutor(Executor resetRequestExecutor) {
    this.resetRequestExecutor = resetRequestExecutor;
  }

  public boolean isEnabled() {
    return clientRegistry.isEnabled();
  }

  /**
   * Makes the endpoints use the services of the application (the ones its
   * OpenIdController returns) instead of the MTAC defaults.
   */
  public void use(OpenIdService openIdService, MfaService mfaService) {
    this.openIdService = openIdService;
    this.mfaService = mfaService;
    authorizationService.setOpenIdService(openIdService);
  }

  public void register(RequestMappingHandlerMapping handlerMapping, String basePath) throws NoSuchMethodException {
    var form = MediaType.APPLICATION_FORM_URLENCODED_VALUE;

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_DISCOVERY).methods(RequestMethod.GET).build(),
        this, OidcEndpoints.class.getDeclaredMethod("discovery"));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_AUTHORIZE).methods(RequestMethod.GET).build(),
        this, OidcEndpoints.class.getDeclaredMethod("authorize", MultiValueMap.class, HttpServletRequest.class));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_LOGIN).methods(RequestMethod.GET).build(),
        this, OidcEndpoints.class.getDeclaredMethod("loginPage", String.class, String.class,
            HttpServletRequest.class));
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_LOGIN).methods(RequestMethod.POST)
            .consumes(form).build(),
        this, OidcEndpoints.class.getDeclaredMethod("login", MultiValueMap.class, HttpServletRequest.class));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_MFA).methods(RequestMethod.GET).build(),
        this, OidcEndpoints.class.getDeclaredMethod("mfaPage", String.class, HttpServletRequest.class));
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_MFA).methods(RequestMethod.POST)
            .consumes(form).build(),
        this, OidcEndpoints.class.getDeclaredMethod("mfa", MultiValueMap.class, HttpServletRequest.class));

    if (resetPasswordService != null) {
      handlerMapping.registerMapping(
          RequestMappingInfo.paths(basePath + PATH_FORGOT_PASSWORD).methods(RequestMethod.GET).build(),
          this, OidcEndpoints.class.getDeclaredMethod("forgotPasswordPage", String.class, String.class,
              HttpServletRequest.class));
      handlerMapping.registerMapping(
          RequestMappingInfo.paths(basePath + PATH_FORGOT_PASSWORD).methods(RequestMethod.POST).consumes(form).build(),
          this, OidcEndpoints.class.getDeclaredMethod("forgotPassword", MultiValueMap.class,
              HttpServletRequest.class));
      handlerMapping.registerMapping(
          RequestMappingInfo.paths(basePath + PATH_RESET_PASSWORD).methods(RequestMethod.GET).build(),
          this, OidcEndpoints.class.getDeclaredMethod("resetPasswordPage", String.class, String.class,
              HttpServletRequest.class));
      handlerMapping.registerMapping(
          RequestMappingInfo.paths(basePath + PATH_RESET_PASSWORD).methods(RequestMethod.POST).consumes(form).build(),
          this, OidcEndpoints.class.getDeclaredMethod("resetPassword", MultiValueMap.class,
              HttpServletRequest.class));
    }

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + PATH_CONSENT).methods(RequestMethod.POST).consumes(form).build(),
        this, OidcEndpoints.class.getDeclaredMethod("consent", MultiValueMap.class, HttpServletRequest.class));

    // more specific than the mapping of OpenIdController on the same path, which keeps the other grants
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_TOKEN).methods(RequestMethod.POST)
            .consumes(form).params("grant_type=authorization_code").build(),
        this, OidcEndpoints.class.getDeclaredMethod("token", MultiValueMap.class, String.class,
            HttpServletRequest.class));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_USERINFO)
            .methods(RequestMethod.GET, RequestMethod.POST).build(),
        this, OidcEndpoints.class.getDeclaredMethod("userinfo", String.class, HttpServletRequest.class));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_LOGOUT).methods(RequestMethod.GET).build(),
        this, OidcEndpoints.class.getDeclaredMethod("logoutRequest", MultiValueMap.class, HttpServletRequest.class));
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_LOGOUT).methods(RequestMethod.POST)
            .consumes(form).build(),
        this, OidcEndpoints.class.getDeclaredMethod("logout", MultiValueMap.class, HttpServletRequest.class));

    handlerMapping.registerMapping(
        RequestMappingInfo.paths(basePath + OidcAuthorizationService.PATH_REVOKE).methods(RequestMethod.POST)
            .consumes(form).build(),
        this, OidcEndpoints.class.getDeclaredMethod("revoke", MultiValueMap.class, String.class,
            HttpServletRequest.class));

    // JSON body read by the endpoint itself (size limit, content type and errors of RFC 7591)
    if (registrationEndpoint != null) {
      handlerMapping.registerMapping(
          RequestMappingInfo.paths(basePath + OidcRegistrationEndpoint.PATH_REGISTER).methods(RequestMethod.POST)
              .build(),
          registrationEndpoint, OidcRegistrationEndpoint.class.getDeclaredMethod("register",
              HttpServletRequest.class));
    }
  }

  // ------------------------------------------------------------ discovery

  public ResponseEntity<Map<String, Object>> discovery() {
    return ResponseEntity.ok(authorizationService.discovery());
  }

  // ------------------------------------------------------------ authorize

  public ResponseEntity<String> authorize(@RequestParam MultiValueMap<String, String> query,
      HttpServletRequest request) {
    var params = query.toSingleValueMap();
    var redirectUri = params.get("redirect_uri");
    var state = params.get("state");

    // 1. client and redirect URI: an error here is shown, never redirected
    MtacOidcClient client;
    try {
      if (hasSeveralValues(query, "client_id") || hasSeveralValues(query, "redirect_uri")) {
        throw OidcException.invalidRequest("Duplicated parameter");
      }
      client = authorizationService.validateClient(params.get("client_id"), redirectUri);
    } catch (OidcException e) {
      return errorPage(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // 2. the rest of the request: an error is returned to the client
    OidcAuthorizationRequest authorizationRequest;
    try {
      for (var name : query.keySet()) {
        // several resource values are answered with invalid_target (RFC 8707) by the validation
        if (!OidcAuthorizationService.PARAM_RESOURCE.equals(name) && hasSeveralValues(query, name)) {
          throw OidcException.invalidRequest("Duplicated parameter " + name);
        }
      }
      authorizationRequest = authorizationService.validateAuthorizationRequest(client, params,
          query.get(OidcAuthorizationService.PARAM_RESOURCE));
    } catch (OidcException e) {
      return redirectError(redirectUri, e.getError(), e.getMessage(), state);
    }

    // 3. a sufficient SSO session answers the request, after the consent of the user when needed
    var session = sessionService.resolve(cookieValue(request, sessionCookieName()));
    if (authorizationService.isSessionSufficient(authorizationRequest, client, session)) {
      return grant(client, authorizationRequest, params, session, request);
    }
    if (authorizationRequest.getPrompt().contains(OidcAuthorizationService.PROMPT_NONE)) {
      return redirectError(redirectUri, "login_required", "The user is not signed in", state);
    }

    // 4. otherwise the user signs in, then comes back to this endpoint. prompt and max_age
    // are satisfied by that fresh login and are dropped, or the request would loop;
    // prompt=consent is kept, the consent page comes after the login.
    var resumeParams = new LinkedHashMap<>(params);
    resumeParams.remove("prompt");
    resumeParams.remove("max_age");
    if (authorizationRequest.getPrompt().contains(OidcConsentService.PROMPT_CONSENT)) {
      resumeParams.put("prompt", OidcConsentService.PROMPT_CONSENT);
    }
    var context = new HashMap<String, Object>();
    context.put("aud", LOGIN_CONTEXT_AUDIENCE);
    context.put("cid", authorizationRequest.getClientId());
    context.put("q", OidcAuthorizationService.appendQuery("", resumeParams).substring(1));
    var loginContext = tokenService.newToken(context, LOGIN_CONTEXT_TTL_SEC);
    return redirect(pageUrl(OidcAuthorizationService.PATH_LOGIN, loginContext), new HttpHeaders());
  }

  // ---------------------------------------------------------------- login

  public ResponseEntity<String> loginPage(String ctx, HttpServletRequest request) {
    return loginPage(ctx, null, request);
  }

  /**
   * @param loginHint email to prefill (the forgot password and reset pages send
   *                  the user back with the address he typed); the login_hint
   *                  of the authorization request otherwise
   */
  public ResponseEntity<String> loginPage(@RequestParam(name = "ctx", required = false) String ctx,
      @RequestParam(name = "login_hint", required = false) String loginHint, HttpServletRequest request) {
    var context = readLoginContext(ctx);
    if (context == null) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    if (context.isStepUp()) {
      // the username is the one of the session, it cannot be changed on the page
      var session = stepUpSession(context, request);
      var user = session != null ? sessionUser(session) : null;
      if (user == null) {
        return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
      }
      return renderLogin(context, request, HttpStatus.OK, user.getLabel(), null);
    }
    var hint = emailValue(loginHint);
    return renderLogin(context, request, HttpStatus.OK, hint != null ? hint : context.params().get("login_hint"), null);
  }

  public ResponseEntity<String> login(@RequestParam MultiValueMap<String, String> form, HttpServletRequest request) {
    var context = readLoginContext(form.getFirst("ctx"));
    if (context == null) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    if (!isCsrfTokenValid(form, request)) {
      return errorPage(HttpStatus.FORBIDDEN, EXPIRED_REQUEST);
    }
    var username = form.getFirst("username") != null ? form.getFirst("username").trim() : "";
    OidcSession stepUpSession = null;
    if (context.isStepUp()) {
      stepUpSession = stepUpSession(context, request);
      var sessionUser = stepUpSession != null ? sessionUser(stepUpSession) : null;
      if (sessionUser == null || sessionUser.getLabel() == null) {
        return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
      }
      if (!username.equalsIgnoreCase(sessionUser.getLabel())) {
        log.warn("OIDC step-up refused: another username was posted for the session of identity {}",
            stepUpSession.getIdentityId());
        return errorPage(HttpStatus.FORBIDDEN, STEP_UP_OTHER_USER);
      }
      username = sessionUser.getLabel();
    }
    var attemptKey = "login|" + username.toLowerCase() + "|" + clientAddress(request);
    if (loginAttemptService.isLocked(attemptKey)) {
      return renderLogin(context, request, HttpStatus.TOO_MANY_REQUESTS, username,
          "Too many attempts. Try again in a few minutes.");
    }

    User user;
    try {
      user = openIdService.authenticateUser(username, form.getFirst("password"));
    } catch (BusinessException e) {
      loginAttemptService.recordFailure(attemptKey);
      return renderLogin(context, request, HttpStatus.UNAUTHORIZED, username, "Incorrect email or password.");
    }
    loginAttemptService.recordSuccess(attemptKey);
    if (stepUpSession != null && !stepUpSession.getSub().equals(user.getUid())) {
      return errorPage(HttpStatus.FORBIDDEN, STEP_UP_OTHER_USER);
    }

    if (Boolean.TRUE.equals(user.getMfa())) {
      // second factor: the MFA token proves the password step, the session is opened only after the code
      var mfaToken = mfaService.generateMfaTokenResponse(user).getMfaToken();
      var headers = new HttpHeaders();
      headers.add(HttpHeaders.SET_COOKIE, cookie(MFA_COOKIE, mfaToken, MFA_COOKIE_TTL_SEC, "Strict"));
      return redirect(pageUrl(OidcAuthorizationService.PATH_MFA, context.token()), headers);
    }
    if (context.client().isRequireMfa()) {
      return redirectError(context.params().get("redirect_uri"), "access_denied",
          "Multi-factor authentication is required for this application", context.params().get("state"));
    }
    if (stepUpSession != null) {
      return completeStepUp(context, stepUpSession, List.of(OidcSessionService.AMR_PASSWORD));
    }
    return completeLogin(context, request, user, List.of(OidcSessionService.AMR_PASSWORD));
  }

  // ------------------------------------------------------------------ mfa

  public ResponseEntity<String> mfaPage(@RequestParam(name = "ctx", required = false) String ctx,
      HttpServletRequest request) {
    var context = readLoginContext(ctx);
    if (context == null) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    var user = mfaUser(request);
    if (user == null) {
      return redirect(pageUrl(OidcAuthorizationService.PATH_LOGIN, context.token()), new HttpHeaders());
    }
    if (context.isStepUp()) {
      var refusal = checkStepUpUser(context, request, user);
      if (refusal != null) {
        return refusal;
      }
    }
    return renderMfa(context, request, HttpStatus.OK, user, null);
  }

  public ResponseEntity<String> mfa(@RequestParam MultiValueMap<String, String> form, HttpServletRequest request) {
    var context = readLoginContext(form.getFirst("ctx"));
    if (context == null) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    if (!isCsrfTokenValid(form, request)) {
      return errorPage(HttpStatus.FORBIDDEN, EXPIRED_REQUEST);
    }
    var user = mfaUser(request);
    if (user == null) {
      return redirect(pageUrl(OidcAuthorizationService.PATH_LOGIN, context.token()), new HttpHeaders());
    }
    OidcSession stepUpSession = null;
    if (context.isStepUp()) {
      var refusal = checkStepUpUser(context, request, user);
      if (refusal != null) {
        return refusal;
      }
      stepUpSession = stepUpSession(context, request);
    }
    var attemptKey = "mfa|" + user.getUid();
    if (loginAttemptService.isLocked(attemptKey)) {
      return renderMfa(context, request, HttpStatus.TOO_MANY_REQUESTS, user,
          "Too many attempts. Try again in a few minutes.");
    }
    var code = form.getFirst("totp") != null ? form.getFirst("totp").replaceAll("\\s", "") : "";
    var enrolling = !isEnrolled(user);
    try {
      mfaService.verifyCode(user, code);
    } catch (BadCredentialsException e) {
      loginAttemptService.recordFailure(attemptKey);
      return renderMfa(context, request, HttpStatus.UNAUTHORIZED, user, "Invalid verification code.");
    }
    loginAttemptService.recordSuccess(attemptKey);
    var amr = List.of(OidcSessionService.AMR_PASSWORD, OidcSessionService.AMR_OTP);
    if (stepUpSession != null) {
      if (sessionService.getAlive(stepUpSession.getId()) == null) {
        // the enrolment of the TOTP secret ended the sessions of the user (grant revocation). He
        // proved the password and the code in this flow (see startStepUp): a new session, as at
        // the login. Otherwise the session ended meanwhile: the authorization asks a login again.
        return enrolling ? completeLogin(context, request, user, amr)
            : redirect(authorizationService.url(OidcAuthorizationService.PATH_AUTHORIZE) + "?" + context.query(),
                new HttpHeaders());
      }
      return completeStepUp(context, stepUpSession, amr);
    }
    return completeLogin(context, request, user, amr);
  }

  /** User who passed the password step, read from the MFA cookie; null if there is none. */
  private User mfaUser(HttpServletRequest request) {
    var mfaToken = cookieValue(request, MFA_COOKIE);
    if (mfaToken == null) {
      return null;
    }
    try {
      return mfaService.getMfaUser(mfaToken);
    } catch (Exception e) {
      return null;
    }
  }

  /** Opens the SSO session and sends the browser back to the authorization endpoint. */
  private ResponseEntity<String> completeLogin(LoginContext context, HttpServletRequest request, User user,
      List<String> amr) {
    // a new session id at each login: the previous session of this browser, if any, ends
    sessionService.end(cookieValue(request, sessionCookieName()));
    var sessionCookie = sessionService.create(user, amr);

    var headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(sessionCookieName(), sessionCookie,
        mtacProperties.getOpenId().getServer().getSession().getMaxAgeSec(), "Lax"));
    headers.add(HttpHeaders.SET_COOKIE, cookie(MFA_COOKIE, "", 0, "Strict"));
    headers.add(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, "", 0, "Strict"));
    return redirect(authorizationService.url(OidcAuthorizationService.PATH_AUTHORIZE) + "?" + context.query(), headers);
  }

  // -------------------------------------------------------------- step-up

  /**
   * Sends the signed-in user to prove himself again before a new consent: the
   * MFA page when he has MFA (the session stands for the password step), the
   * login page with his username fixed otherwise. A user who still has to
   * enrol his TOTP secret starts with the password: the enrolment ends his
   * sessions (grant revocation), and the code alone must not open a new one.
   */
  private ResponseEntity<String> startStepUp(OidcAuthorizationRequest authorizationRequest, Map<String, String> params,
      OidcSession session) {
    var user = sessionUser(session);
    if (user == null || !session.getSub().equals(user.getUid())) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    var context = new HashMap<String, Object>();
    context.put("aud", LOGIN_CONTEXT_AUDIENCE);
    context.put("cid", authorizationRequest.getClientId());
    context.put("q", OidcAuthorizationService.appendQuery("", params).substring(1));
    context.put(STEP_UP_SESSION_CLAIM, session.getId());
    context.put(STEP_UP_SUBJECT_CLAIM, session.getSub());
    var loginContext = tokenService.newToken(context, LOGIN_CONTEXT_TTL_SEC);
    if (Boolean.TRUE.equals(user.getMfa()) && isEnrolled(user)) {
      var mfaToken = mfaService.generateMfaTokenResponse(user).getMfaToken();
      var headers = new HttpHeaders();
      headers.add(HttpHeaders.SET_COOKIE, cookie(MFA_COOKIE, mfaToken, MFA_COOKIE_TTL_SEC, "Strict"));
      return redirect(pageUrl(OidcAuthorizationService.PATH_MFA, loginContext), headers);
    }
    return redirect(pageUrl(OidcAuthorizationService.PATH_LOGIN, loginContext), new HttpHeaders());
  }

  /**
   * The SSO session a step-up context was issued for, when it is still the
   * session of this browser and of the same user; null otherwise (ended,
   * signed out, or another user signed in meanwhile).
   */
  private OidcSession stepUpSession(LoginContext context, HttpServletRequest request) {
    var session = sessionService.resolve(cookieValue(request, sessionCookieName()));
    if (session == null || !session.getId().equals(context.stepUpSessionId())
        || !context.stepUpSubject().equals(session.getSub())) {
      return null;
    }
    return session;
  }

  /** Refusal page when the user of the MFA step is not the user of the step-up session, else null. */
  private ResponseEntity<String> checkStepUpUser(LoginContext context, HttpServletRequest request, User user) {
    var session = stepUpSession(context, request);
    if (session == null) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    if (!session.getSub().equals(user.getUid())) {
      log.warn("OIDC step-up refused: the MFA step of another user was used for the session of identity {}",
          session.getIdentityId());
      return errorPage(HttpStatus.FORBIDDEN, STEP_UP_OTHER_USER);
    }
    return null;
  }

  /** True when the user validated a code of his TOTP secret already. */
  private static boolean isEnrolled(User user) {
    return Boolean.TRUE.equals(user.getMfaActive()) && user.getTotpSecret() != null;
  }

  /** User of a session, or null when it cannot be read. */
  private User sessionUser(OidcSession session) {
    try {
      return openIdService.getUserService().getByUidUnsecure(session.getSub());
    } catch (RuntimeException e) {
      log.warn("Cannot read the user of the OpenID session: {}", e.getMessage());
      return null;
    }
  }

  /**
   * The step-up succeeded: the same session gets a new authentication time
   * and methods (its cookie does not change), then the authorization request
   * resumes and shows the consent page.
   */
  private ResponseEntity<String> completeStepUp(LoginContext context, OidcSession session, List<String> amr) {
    sessionService.reauthenticate(session, amr);
    log.info("OIDC step-up of identity {} before a consent for client {} ({})", session.getIdentityId(),
        context.params().get("client_id"), String.join(" ", amr));
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(MFA_COOKIE, "", 0, "Strict"));
    headers.add(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, "", 0, "Strict"));
    return redirect(authorizationService.url(OidcAuthorizationService.PATH_AUTHORIZE) + "?" + context.query(), headers);
  }

  // -------------------------------------------------------------- consent

  /**
   * The user is signed in: issues the code, unless the client needs a consent
   * the user has not given yet, in which case the consent page is shown.
   */
  private ResponseEntity<String> grant(MtacOidcClient client, OidcAuthorizationRequest authorizationRequest,
      Map<String, String> params, OidcSession session, HttpServletRequest request) {
    var redirectUri = authorizationRequest.getRedirectUri();
    var state = authorizationRequest.getState();
    if (!OidcConsentService.isTrusted(client)) {
      if (isImpersonating(request)) {
        return redirectError(redirectUri, "access_denied",
            "An application cannot be authorized while impersonating a user", state);
      }
      if (consentService.isConsentRequired(client, authorizationRequest, session.getIdentityId())) {
        var promptNone = authorizationRequest.getPrompt().contains(OidcAuthorizationService.PROMPT_NONE);
        // a new access needs a recent authentication: an old SSO session alone is not enough
        if (consentService.isStepUpRequired(session)) {
          if (promptNone) {
            return redirectError(redirectUri, "login_required",
                "The user must confirm his identity to authorize this application", state);
          }
          return startStepUp(authorizationRequest, params, session);
        }
        if (promptNone) {
          return redirectError(redirectUri, "consent_required", "The user has not authorized this application",
              state);
        }
        return renderConsent(client, authorizationRequest, params, session, request);
      }
      consentService.recordUse(session.getIdentityId(), authorizationRequest.getClientId());
    }
    return codeRedirect(authorizationRequest, session, new HttpHeaders());
  }

  /** Answer of the consent page: allow records the consent and issues the code, deny returns access_denied. */
  public ResponseEntity<String> consent(@RequestParam MultiValueMap<String, String> form, HttpServletRequest request) {
    var context = readConsentContext(form.getFirst("ctx"));
    if (context == null) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    if (!isCsrfTokenValid(form, request)) {
      return errorPage(HttpStatus.FORBIDDEN, EXPIRED_REQUEST);
    }
    var params = context.params();
    var redirectUri = params.get("redirect_uri");
    var state = params.get("state");

    // the request is validated again: the client may have changed since the page was shown
    MtacOidcClient client;
    try {
      client = authorizationService.validateClient(params.get("client_id"), redirectUri);
    } catch (OidcException e) {
      return errorPage(HttpStatus.BAD_REQUEST, e.getMessage());
    }
    OidcAuthorizationRequest authorizationRequest;
    try {
      authorizationRequest = authorizationService.validateAuthorizationRequest(client, params);
    } catch (OidcException e) {
      return redirectError(redirectUri, e.getError(), e.getMessage(), state);
    }
    // same browser session, same scopes as the ones the user saw
    var session = sessionService.resolve(cookieValue(request, sessionCookieName()));
    if (session == null || !session.getId().equals(context.sessionId())
        || !OidcConsentRepository.joinScopes(authorizationRequest.getScopes()).equals(context.scopes())) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }

    var headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, "", 0, "Strict"));
    if (isImpersonating(request)) {
      return redirectError(redirectUri, "access_denied",
          "An application cannot be authorized while impersonating a user", state, headers);
    }
    var decision = form.getFirst("decision");
    if (DECISION_DENY.equals(decision)) {
      return redirectError(redirectUri, "access_denied", "The user denied the request", state, headers);
    }
    if (!DECISION_ALLOW.equals(decision)) {
      return errorPage(HttpStatus.BAD_REQUEST, EXPIRED_REQUEST);
    }
    consentService.recordApproval(session.getIdentityId(), authorizationRequest.getClientId(),
        OidcConsentService.consentScopes(authorizationRequest));
    return codeRedirect(authorizationRequest, session, headers);
  }

  /** Issues the authorization code and sends the browser back to the client. */
  private ResponseEntity<String> codeRedirect(OidcAuthorizationRequest authorizationRequest, OidcSession session,
      HttpHeaders headers) {
    var response = new LinkedHashMap<String, String>();
    response.put("code", authorizationService.issueCode(authorizationRequest, session));
    response.put("state", authorizationRequest.getState());
    response.put("iss", authorizationService.getIssuer());
    return redirect(OidcAuthorizationService.appendQuery(authorizationRequest.getRedirectUri(), response), headers);
  }

  private ResponseEntity<String> renderConsent(MtacOidcClient client, OidcAuthorizationRequest authorizationRequest,
      Map<String, String> params, OidcSession session, HttpServletRequest request) {
    var scopes = OidcConsentRepository.joinScopes(authorizationRequest.getScopes());
    var context = new HashMap<String, Object>();
    context.put("aud", CONSENT_CONTEXT_AUDIENCE);
    context.put("cid", authorizationRequest.getClientId());
    context.put("q", OidcAuthorizationService.appendQuery("", params).substring(1));
    context.put("sid", session.getId());
    context.put("scp", scopes);
    var consentContext = tokenService.newToken(context, LOGIN_CONTEXT_TTL_SEC);

    var clientId = authorizationRequest.getClientId();
    var model = new HashMap<String, String>();
    model.put("action", authorizationService.url(PATH_CONSENT));
    model.put("ctx", consentContext);
    model.put("clientName", client.getName() != null && !client.getName().isBlank() ? client.getName() : clientId);
    model.put("clientHost", OidcConsentService.clientHost(clientId, client, authorizationRequest.getRedirectUri()));
    model.put("redirectHost", OidcConsentService.host(authorizationRequest.getRedirectUri()));
    model.put("username", userLabel(session));
    model.put("loopback", OidcConsentService.isLoopbackOnly(client) ? "true" : null);
    model.put("resource", resourceLabel(authorizationRequest.getResource()));
    var items = new ArrayList<Map<String, String>>();
    for (var scope : OidcConsentRepository.splitScopes(scopes)) {
      var item = new HashMap<String, String>();
      item.put("name", scope);
      item.put("label", consentService.scopeLabel(scope));
      items.add(item);
    }
    return page("consent", model, Map.of("scopes", items), HttpStatus.OK, request, client, new HttpHeaders());
  }

  /** What the consent page shows of the user: the label (email) of the session's identity. */
  private String userLabel(OidcSession session) {
    try {
      var user = openIdService.getUserService().getByUidUnsecure(session.getSub());
      if (user != null && user.getLabel() != null) {
        return user.getLabel();
      }
    } catch (RuntimeException e) {
      log.warn("Cannot read the user of the OpenID session for the consent page: {}", e.getMessage());
    }
    return session.getSub();
  }

  /** URL of the audience asked through the resource parameter, or null. */
  private String resourceLabel(String resource) {
    if (resource == null || resource.isBlank()) {
      return null;
    }
    var audiences = mtacProperties.getJwt().getAudiences();
    var url = audiences != null ? audiences.get(resource) : null;
    return url != null ? url : resource;
  }

  /**
   * True while the browser holds an impersonation cookie of the Console. On a
   * separate authentication host the Console cookies are not sent (host-only
   * cookies): the check matters when the provider and the Console share a host.
   */
  private boolean isImpersonating(HttpServletRequest request) {
    var iam = mtacProperties.getIam();
    return isCookieSet(cookieValue(request, iam.getImpersonateTokenCookieName()))
        || isCookieSet(cookieValue(request, iam.getImpersonateActiveCookieName()));
  }

  private static boolean isCookieSet(String value) {
    return value != null && !value.isBlank() && !"deleted".equals(value);
  }

  private record ConsentContext(Map<String, String> params, String sessionId, String scopes) {
  }

  /** Context of the consent page, or null if it is missing, expired, tampered or of another kind. */
  private ConsentContext readConsentContext(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      var claims = tokenService.verify(token);
      if (!CONSENT_CONTEXT_AUDIENCE.equals(claims.getAudience())) {
        return null;
      }
      var clientId = claims.get("cid", String.class);
      var query = claims.get("q", String.class);
      var sessionId = claims.get("sid", String.class);
      var scopes = claims.get("scp", String.class);
      if (clientId == null || query == null || sessionId == null || scopes == null) {
        return null;
      }
      var params = parseQuery(query);
      if (!clientId.equals(params.get("client_id"))) {
        return null;
      }
      return new ConsentContext(params, sessionId, scopes);
    } catch (Exception e) {
      return null;
    }
  }

  // ---------------------------------------------------------------- token

  public ResponseEntity<?> token(@RequestParam MultiValueMap<String, String> form,
      @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
    var origin = request.getHeader("Origin");
    try {
      for (var name : List.of("client_id", "client_secret", "code", "redirect_uri", "code_verifier")) {
        if (hasSeveralValues(form, name)) {
          throw OidcException.invalidRequest("Duplicated parameter " + name);
        }
      }
      var client = clientCredentials(form.getFirst("client_id"), form.getFirst("client_secret"), authorization);
      return tokenResponse(authorizationService.exchangeCode(client[0], client[1], origin, form.getFirst("code"),
          form.getFirst("redirect_uri"), form.getFirst("code_verifier"),
          form.get(OidcAuthorizationService.PARAM_RESOURCE)), origin);
    } catch (OidcException e) {
      return jsonError(e, origin);
    }
  }

  /**
   * Refresh of the tokens of an OpenID client. Called by the token endpoint of
   * {@link OpenIdController} when the refresh token is one issued here.
   */
  public ResponseEntity<?> refresh(String clientId, String clientSecret, String refreshToken) {
    var attributes = RequestContextHolder.getRequestAttributes();
    var servletRequest = attributes instanceof ServletRequestAttributes servletAttributes
        ? servletAttributes.getRequest()
        : null;
    var origin = servletRequest != null ? servletRequest.getHeader("Origin") : null;
    var resources = servletRequest != null ? servletRequest.getParameterValues(OidcAuthorizationService.PARAM_RESOURCE)
        : null;
    try {
      return tokenResponse(authorizationService.refresh(clientId, clientSecret, origin, refreshToken,
          resources != null ? List.of(resources) : null), origin);
    } catch (OidcException e) {
      return jsonError(e, origin);
    }
  }

  public ResponseEntity<?> revoke(@RequestParam MultiValueMap<String, String> form,
      @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
    var origin = request.getHeader("Origin");
    try {
      var client = clientCredentials(form.getFirst("client_id"), form.getFirst("client_secret"), authorization);
      authorizationService.revoke(client[0], client[1], origin, form.getFirst("token"));
      return ResponseEntity.ok().headers(apiHeaders(origin)).build();
    } catch (OidcException e) {
      return jsonError(e, origin);
    }
  }

  public ResponseEntity<?> userinfo(@RequestHeader(value = "Authorization", required = false) String authorization,
      HttpServletRequest request) {
    var origin = request.getHeader("Origin");
    try {
      if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
        throw OidcException.invalidToken("Missing access token");
      }
      return ResponseEntity.ok().headers(apiHeaders(origin))
          .body(authorizationService.userinfo(authorization.substring(7).trim()));
    } catch (OidcException e) {
      var headers = apiHeaders(origin);
      headers.add(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + e.getError() + "\"");
      return ResponseEntity.status(e.getStatus()).headers(headers).body(errorBody(e));
    }
  }

  // --------------------------------------------------------------- logout

  /**
   * RP-initiated logout. With a valid id_token_hint the request comes from a
   * client of the user and the session ends at once: the session named by its
   * sid, or, for a hint without sid (ID token refreshed after its SSO session
   * ended), the session of this browser when it belongs to the same subject.
   * Otherwise anyone could have sent the user here, so a confirmation is asked.
   */
  public ResponseEntity<String> logoutRequest(@RequestParam MultiValueMap<String, String> query,
      HttpServletRequest request) {
    var params = query.toSingleValueMap();
    var hint = authorizationService.parseIdTokenHint(params.get("id_token_hint"));
    var sessionCookie = cookieValue(request, sessionCookieName());

    var hintSessionId = hint != null ? hint.get("sid", String.class) : null;
    if (hintSessionId != null) {
      sessionService.endById(hintSessionId);
      var headers = new HttpHeaders();
      if (sessionCookie != null && OidcCrypto.sha256Hex(sessionCookie).equals(hintSessionId)) {
        headers.add(HttpHeaders.SET_COOKIE, cookie(sessionCookieName(), "", 0, "Lax"));
      }
      return afterLogout(hint.getAudience(), params.get("post_logout_redirect_uri"), params.get("state"), headers);
    }
    // no hint, or a hint without sid (an ID token refreshed after its SSO session ended): the
    // session of this browser, if any, is the one to end
    var clientId = hint != null ? hint.getAudience() : params.get("client_id");
    var browserSession = sessionService.resolve(sessionCookie);
    if (browserSession == null) {
      return afterLogout(clientId, params.get("post_logout_redirect_uri"), params.get("state"), new HttpHeaders());
    }
    if (hint != null && hint.getSubject() != null && hint.getSubject().equals(browserSession.getSub())) {
      // a client of the user of this session asks it: no confirmation needed
      sessionService.endById(browserSession.getId());
      var headers = new HttpHeaders();
      headers.add(HttpHeaders.SET_COOKIE, cookie(sessionCookieName(), "", 0, "Lax"));
      return afterLogout(clientId, params.get("post_logout_redirect_uri"), params.get("state"), headers);
    }

    var model = new HashMap<String, String>();
    model.put("confirm", "true");
    model.put("action", authorizationService.url(OidcAuthorizationService.PATH_LOGOUT));
    model.put("clientId", clientId);
    model.put("postLogoutRedirectUri", params.get("post_logout_redirect_uri"));
    model.put("state", params.get("state"));
    return page("logout", model, HttpStatus.OK, request, clientRegistry.find(clientId), new HttpHeaders());
  }

  public ResponseEntity<String> logout(@RequestParam MultiValueMap<String, String> form, HttpServletRequest request) {
    if (!isCsrfTokenValid(form, request)) {
      return errorPage(HttpStatus.FORBIDDEN, "This request has expired. Try again.");
    }
    sessionService.end(cookieValue(request, sessionCookieName()));
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(sessionCookieName(), "", 0, "Lax"));
    headers.add(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, "", 0, "Strict"));
    return afterLogout(form.getFirst("client_id"), form.getFirst("post_logout_redirect_uri"), form.getFirst("state"),
        headers);
  }

  private ResponseEntity<String> afterLogout(String clientId, String postLogoutRedirectUri, String state,
      HttpHeaders headers) {
    var target = authorizationService.postLogoutRedirect(clientId, postLogoutRedirectUri, state);
    if (target != null) {
      return redirect(target, headers);
    }
    return page("logout", new HashMap<>(), HttpStatus.OK, null, null, headers);
  }

  // ------------------------------------------------------ forgot password

  /**
   * Form asking the email address the reset link is sent to.
   *
   * @param ctx       login context of the authorization request the user comes
   *                  from, optional (ignored during a step-up)
   * @param loginHint address to prefill, optional
   */
  public ResponseEntity<String> forgotPasswordPage(@RequestParam(name = "ctx", required = false) String ctx,
      @RequestParam(name = "login_hint", required = false) String loginHint, HttpServletRequest request) {
    return renderForgotPassword(signInContext(ctx), request, HttpStatus.OK, emailValue(loginHint), null);
  }

  /**
   * Sends the reset link. The answer is the same whether the address is known
   * or not, and also within the cooldown of the address: the page never tells
   * whether an account exists.
   */
  public ResponseEntity<String> forgotPassword(@RequestParam MultiValueMap<String, String> form,
      HttpServletRequest request) {
    var context = signInContext(form.getFirst("ctx"));
    var email = emailValue(form.getFirst("email"));
    if (!isCsrfTokenValid(form, request)) {
      return renderForgotPassword(context, request, HttpStatus.FORBIDDEN, email, PAGE_EXPIRED);
    }
    // every request counts: the page sends emails, it must not be used to spam a mailbox
    var attemptKey = "forgot|" + clientAddress(request);
    if (loginAttemptService.isLocked(attemptKey)) {
      return renderForgotPassword(context, request, HttpStatus.TOO_MANY_REQUESTS, email, TOO_MANY_ATTEMPTS);
    }
    loginAttemptService.recordFailure(attemptKey);
    if (email == null || !email.contains("@") || email.chars().anyMatch(Character::isWhitespace)) {
      return renderForgotPassword(context, request, HttpStatus.BAD_REQUEST, email, "Enter your email address.");
    }

    var linkParams = new LinkedHashMap<String, String>();
    if (context != null) {
      linkParams.put("ctx", newReturnContext(context.params().get("client_id"), context.query()));
    }
    var service = resetPasswordService;
    if (!resetRequestsInFlight.tryAcquire()) {
      // a burst of requests (several addresses, several IPs): no unbounded number of threads and emails
      log.warn("Reset password request dropped: {} requests in progress already", MAX_RESET_REQUESTS_IN_FLIGHT);
    } else {
      try {
        resetRequestExecutor.execute(() -> {
          try {
            service.triggerResetPassword(email, linkParams);
          } catch (BusinessException e) {
            // a new request within the cooldown of the address: no new link, same answer
            log.info("Reset password request ignored: {}", e.getErrorCode());
          } catch (RuntimeException e) {
            log.warn("Reset password request failed: {}", e.getMessage());
          } finally {
            resetRequestsInFlight.release();
          }
        });
      } catch (RuntimeException e) {
        resetRequestsInFlight.release();
        log.warn("Reset password request not started: {}", e.getMessage());
      }
    }

    var model = new HashMap<String, String>();
    model.put("email", email);
    model.put("validity", duration(mtacProperties.getJwt().getResetPasswordExpirationSec()));
    model.put("backUrl", context != null ? loginUrl(context.token(), email) : consoleUrl());
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, "", 0, "Strict"));
    return page("forgot-password-sent", model, HttpStatus.OK, null, null, headers);
  }

  private ResponseEntity<String> renderForgotPassword(LoginContext context, HttpServletRequest request,
      HttpStatus status, String email, String error) {
    var model = new HashMap<String, String>();
    model.put("action", authorizationService.url(PATH_FORGOT_PASSWORD));
    model.put("ctx", context != null ? context.token() : null);
    model.put("email", email);
    model.put("error", error);
    model.put("backUrl", context != null ? loginUrl(context.token(), email) : consoleUrl());
    return page("forgot-password", model, status, request, null, new HttpHeaders());
  }

  // ------------------------------------------------------- reset password

  /**
   * Form choosing the new password, opened by the link of the email (also the
   * "finish setting up your account" link of a new user, and the links sent
   * before the hosted pages existed: same token).
   *
   * @param ctx return context of the sign-in the request started from, optional
   */
  public ResponseEntity<String> resetPasswordPage(@RequestParam(name = "resetToken", required = false) String resetToken,
      @RequestParam(name = "ctx", required = false) String ctx, HttpServletRequest request) {
    var returnContext = readReturnContext(ctx);
    var user = resetPasswordService.resolveResetToken(resetToken);
    if (user == null) {
      return renderInvalidReset(resetToken, returnContext);
    }
    return renderResetPassword(user, resetToken, returnContext, request, HttpStatus.OK, null);
  }

  public ResponseEntity<String> resetPassword(@RequestParam MultiValueMap<String, String> form,
      HttpServletRequest request) {
    var resetToken = form.getFirst("resetToken");
    var returnContext = readReturnContext(form.getFirst("ctx"));
    var user = resetPasswordService.resolveResetToken(resetToken);
    if (user == null) {
      return renderInvalidReset(resetToken, returnContext);
    }
    if (!isCsrfTokenValid(form, request)) {
      return renderResetPassword(user, resetToken, returnContext, request, HttpStatus.FORBIDDEN, PAGE_EXPIRED);
    }
    var password = form.getFirst("password");
    var error = ResetPasswordService.passwordRuleError(password);
    if (error == null && !password.equals(form.getFirst("confirmation"))) {
      error = "The two passwords do not match.";
    }
    if (error != null) {
      return renderResetPassword(user, resetToken, returnContext, request, HttpStatus.BAD_REQUEST, error);
    }
    try {
      resetPasswordService.resetPassword(new ResetPasswordRequest(resetToken, password));
    } catch (RuntimeException e) {
      log.warn("Password reset of identity {} failed: {}", user.getId(),
          e instanceof BusinessException business ? business.getErrorCode() : e.getClass().getSimpleName());
      // the link may have been used meanwhile (another tab)
      if (resetPasswordService.resolveResetToken(resetToken) == null) {
        return renderInvalidReset(resetToken, returnContext);
      }
      return renderResetPassword(user, resetToken, returnContext, request, HttpStatus.INTERNAL_SERVER_ERROR,
          "Your password could not be changed. Try again.");
    }
    log.info("Password of identity {} changed with a reset link", user.getId());

    var model = new HashMap<String, String>();
    model.put("email", user.getLabel());
    model.put("signInUrl", signInUrl(returnContext, user.getLabel()));
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, "", 0, "Strict"));
    return page("reset-password-done", model, HttpStatus.OK, null, null, headers);
  }

  private ResponseEntity<String> renderResetPassword(User user, String resetToken, ReturnContext returnContext,
      HttpServletRequest request, HttpStatus status, String error) {
    var model = new HashMap<String, String>();
    model.put("action", authorizationService.url(PATH_RESET_PASSWORD));
    model.put("resetToken", resetToken);
    model.put("ctx", returnContext != null ? returnContext.token() : null);
    model.put("email", user.getLabel());
    model.put("minLength", String.valueOf(ResetPasswordService.PASSWORD_MIN_LENGTH));
    model.put("error", error);
    return page("reset-password", model, status, request, null, new HttpHeaders());
  }

  /** The link is expired, used, replaced by a newer one or tampered: the user asks a new one. */
  private ResponseEntity<String> renderInvalidReset(String resetToken, ReturnContext returnContext) {
    var email = expiredResetEmail(resetToken);
    var loginContext = returnContext != null && !isConsoleClient(returnContext.clientId())
        ? newLoginContext(returnContext.clientId(), returnContext.query())
        : null;
    var model = new HashMap<String, String>();
    model.put("forgotUrl", forgotPasswordUrl(loginContext, email));
    model.put("signInUrl", signInUrl(returnContext, email));
    return page("reset-password-invalid", model, HttpStatus.BAD_REQUEST, null, null, new HttpHeaders());
  }

  /** Address of a reset link whose signature is valid (to prefill a new request), else null. */
  private String expiredResetEmail(String resetToken) {
    if (resetToken == null || resetToken.isBlank()) {
      return null;
    }
    try {
      return emailValue(tokenService.verifyIgnoringExpiration(resetToken).get("email", String.class));
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Where the "Sign in" button of the reset pages goes: the login page of the
   * authorization request the reset started from, else the Console. The
   * Console client starts a new sign-in itself (its transaction cookie lives
   * a few minutes only, an old request would fail on its callback).
   */
  private String signInUrl(ReturnContext returnContext, String email) {
    if (returnContext != null && !isConsoleClient(returnContext.clientId())) {
      return loginUrl(newLoginContext(returnContext.clientId(), returnContext.query()), email);
    }
    return consoleUrl();
  }

  private boolean isConsoleClient(String clientId) {
    var browserSession = mtacProperties.getIam().getBrowserSession();
    return browserSession != null && browserSession.isEnabled() && clientId.equals(browserSession.getClientId());
  }

  private String consoleUrl() {
    var baseUrl = mtacProperties.getBaseUrl();
    return baseUrl != null && !baseUrl.isBlank() ? baseUrl : null;
  }

  private String forgotPasswordUrl(String loginContext, String email) {
    var url = new StringBuilder(authorizationService.url(PATH_FORGOT_PASSWORD));
    var separator = '?';
    if (loginContext != null) {
      url.append(separator).append("ctx=").append(URLEncoder.encode(loginContext, StandardCharsets.UTF_8));
      separator = '&';
    }
    var hint = emailValue(email);
    if (hint != null) {
      url.append(separator).append("login_hint=").append(URLEncoder.encode(hint, StandardCharsets.UTF_8));
    }
    return url.toString();
  }

  private String loginUrl(String loginContext, String email) {
    var url = pageUrl(OidcAuthorizationService.PATH_LOGIN, loginContext);
    var hint = emailValue(email);
    return hint != null ? url + "&login_hint=" + URLEncoder.encode(hint, StandardCharsets.UTF_8) : url;
  }

  /** The login context of a sign-in the password pages may return to: none during a step-up. */
  private LoginContext signInContext(String token) {
    var context = readLoginContext(token);
    return context != null && !context.isStepUp() ? context : null;
  }

  private String newLoginContext(String clientId, String query) {
    var context = new HashMap<String, Object>();
    context.put("aud", LOGIN_CONTEXT_AUDIENCE);
    context.put("cid", clientId);
    context.put("q", query);
    return tokenService.newToken(context, LOGIN_CONTEXT_TTL_SEC);
  }

  /** Context carried by the reset link to resume the sign-in; it lives as long as the link, one hour at most. */
  private String newReturnContext(String clientId, String query) {
    var context = new HashMap<String, Object>();
    context.put("aud", RESET_RETURN_AUDIENCE);
    context.put("cid", clientId);
    context.put("q", query);
    var ttl = Math.min(RESET_RETURN_TTL_SEC, mtacProperties.getJwt().getResetPasswordExpirationSec());
    return tokenService.newToken(context, ttl);
  }

  private record ReturnContext(String token, String clientId, String query) {
  }

  /** Return context of a reset link, or null if it is missing, expired, tampered or its client is gone. */
  private ReturnContext readReturnContext(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      var claims = tokenService.verifyIgnoringExpiration(token);
      var expiration = claims.getExpiration();
      if (!RESET_RETURN_AUDIENCE.equals(claims.getAudience()) || expiration == null
          || !expiration.after(new Date())) {
        return null;
      }
      var clientId = claims.get("cid", String.class);
      var query = claims.get("q", String.class);
      if (clientId == null || query == null || clientRegistry.find(clientId) == null
          || !clientId.equals(parseQuery(query).get("client_id"))) {
        return null;
      }
      return new ReturnContext(token, clientId, query);
    } catch (Exception e) {
      return null;
    }
  }

  /** A typed email address, trimmed; null when blank or too long. */
  private static String emailValue(String value) {
    if (value == null) {
      return null;
    }
    var trimmed = value.trim();
    return trimmed.isEmpty() || trimmed.length() > MAX_EMAIL_LENGTH ? null : trimmed;
  }

  /** "24 hours", "1 hour", "30 minutes". */
  static String duration(int seconds) {
    if (seconds >= 3600 && seconds % 3600 == 0) {
      var hours = seconds / 3600;
      return hours + (hours == 1 ? " hour" : " hours");
    }
    var minutes = Math.max(1, Math.round(seconds / 60f));
    return minutes + (minutes == 1 ? " minute" : " minutes");
  }

  // -------------------------------------------------------- login context

  /**
   * What the login pages need to resume the authorization request once the user
   * is signed in. It travels in a signed token: the pages never redirect to an
   * address taken from a plain parameter.
   */
  private record LoginContext(String token, String query, Map<String, String> params, MtacOidcClient client,
      String stepUpSessionId, String stepUpSubject) {
    /** True for the step-up of a signed-in user before a consent. */
    boolean isStepUp() {
      return stepUpSessionId != null;
    }
  }

  private LoginContext readLoginContext(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      var claims = tokenService.verify(token);
      if (!LOGIN_CONTEXT_AUDIENCE.equals(claims.getAudience())) {
        return null;
      }
      var client = clientRegistry.find(claims.get("cid", String.class));
      var query = claims.get("q", String.class);
      if (client == null || query == null) {
        return null;
      }
      var stepUpSessionId = claims.get(STEP_UP_SESSION_CLAIM, String.class);
      var stepUpSubject = claims.get(STEP_UP_SUBJECT_CLAIM, String.class);
      if ((stepUpSessionId == null) != (stepUpSubject == null)) {
        return null;
      }
      return new LoginContext(token, query, parseQuery(query), client, stepUpSessionId, stepUpSubject);
    } catch (Exception e) {
      return null;
    }
  }

  private static Map<String, String> parseQuery(String query) {
    var params = new LinkedHashMap<String, String>();
    for (var pair : query.split("&")) {
      var separator = pair.indexOf('=');
      if (separator > 0) {
        params.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
            URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
      }
    }
    return params;
  }

  private String pageUrl(String path, String loginContext) {
    return authorizationService.url(path) + "?ctx=" + URLEncoder.encode(loginContext, StandardCharsets.UTF_8);
  }

  // ---------------------------------------------------------------- pages

  private ResponseEntity<String> renderLogin(LoginContext context, HttpServletRequest request, HttpStatus status,
      String username, String error) {
    var model = new HashMap<String, String>();
    model.put("action", authorizationService.url(OidcAuthorizationService.PATH_LOGIN));
    model.put("ctx", context.token());
    if (context.isStepUp()) {
      // the page says why the user signs in again, and the username field is read-only
      model.put("stepUpClient", clientLabel(context));
    } else {
      model.put("clientName", context.client().getName());
      if (resetPasswordService != null) {
        // no forgot password link during a step-up: the user is signed in already
        model.put("forgotUrl", forgotPasswordUrl(context.token(), username));
      }
    }
    model.put("username", username);
    model.put("error", error);
    return page("login", model, status, request, context.client(), new HttpHeaders());
  }

  private ResponseEntity<String> renderMfa(LoginContext context, HttpServletRequest request, HttpStatus status,
      User user, String error) {
    var model = new HashMap<String, String>();
    model.put("action", authorizationService.url(OidcAuthorizationService.PATH_MFA));
    model.put("ctx", context.token());
    model.put("clientName", context.client().getName());
    if (context.isStepUp()) {
      model.put("stepUpClient", clientLabel(context));
    }
    model.put("username", user.getLabel());
    model.put("error", error);
    // a user who never validated a code has to enrol the secret first
    if (!Boolean.TRUE.equals(user.getMfaActive()) && user.getTotpSecret() != null) {
      var issuerName = mtacProperties.getOpenId().getServer().getTotpIssuerName();
      model.put("totpSecret", user.getTotpSecret());
      model.put("qrCode", TotpQrCode.dataUri(TotpQrCode.otpAuthUri(issuerName, user.getLabel(), user.getTotpSecret())));
    }
    return page("mfa", model, status, request, context.client(), new HttpHeaders());
  }

  /** Name of the client of a login context, its client_id when it has none. */
  private static String clientLabel(LoginContext context) {
    var name = context.client().getName();
    return name != null && !name.isBlank() ? name : context.params().get("client_id");
  }

  private ResponseEntity<String> errorPage(HttpStatus status, String message) {
    var model = new HashMap<String, String>();
    model.put("message", message);
    return page("error", model, status, null, null, new HttpHeaders());
  }

  /**
   * Renders a hosted page with its security headers.
   *
   * @param request when not null the page holds a form: a CSRF token is added to
   *                the model and to a cookie
   * @param client  client the form may end up redirecting to
   */
  private ResponseEntity<String> page(String template, Map<String, String> model, HttpStatus status,
      HttpServletRequest request, MtacOidcClient client, HttpHeaders headers) {
    return page(template, model, Map.of(), status, request, client, headers);
  }

  private ResponseEntity<String> page(String template, Map<String, String> model,
      Map<String, List<Map<String, String>>> lists, HttpStatus status, HttpServletRequest request,
      MtacOidcClient client, HttpHeaders headers) {
    var nonce = OidcCrypto.randomToken();
    model.put("nonce", nonce);
    if (request != null) {
      var csrf = cookieValue(request, CSRF_COOKIE);
      if (csrf == null || csrf.isBlank()) {
        csrf = OidcCrypto.randomToken();
      }
      model.put("csrf", csrf);
      headers.add(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, csrf, LOGIN_CONTEXT_TTL_SEC, "Strict"));
    }

    // Browsers apply form-action to the redirects that follow a form submission:
    // the origins of the client must be allowed or the return to the client is blocked.
    var formAction = new StringBuilder("'self'");
    if (client != null) {
      for (var origin : origins(client)) {
        formAction.append(' ').append(origin);
      }
    }
    headers.setContentType(new MediaType("text", "html", StandardCharsets.UTF_8));
    headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.add("Content-Security-Policy", "default-src 'none'; style-src 'nonce-" + nonce
        + "'; img-src 'self' data:; form-action " + formAction + "; frame-ancestors 'none'; base-uri 'none'");
    headers.add("X-Frame-Options", "DENY");
    headers.add("X-Content-Type-Options", "nosniff");
    headers.add("Referrer-Policy", "no-referrer");
    return ResponseEntity.status(status).headers(headers).body(pageRenderer.render(template, model, lists));
  }

  private static Set<String> origins(MtacOidcClient client) {
    var result = new LinkedHashSet<String>();
    var uris = new ArrayList<>(client.getRedirectUris());
    uris.addAll(client.getPostLogoutRedirectUris());
    for (var value : uris) {
      try {
        var uri = URI.create(value);
        if (client.isLoopback() && OidcClientRegistry.parseLoopback(value) != null) {
          // a loopback client is sent back on any port (RFC 8252)
          if (!"[::1]".equals(uri.getHost())) {
            result.add("http://" + uri.getHost() + ":*");
          }
          continue;
        }
        if (uri.getScheme() != null && uri.getHost() != null) {
          result.add(uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
        }
      } catch (IllegalArgumentException e) {
        log.warn("Invalid redirect URI in the OpenID client configuration: {}", value);
      }
    }
    return result;
  }

  // ------------------------------------------------------------ responses

  private ResponseEntity<String> redirect(String location, HttpHeaders headers) {
    headers.add(HttpHeaders.LOCATION, location);
    headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.add("Referrer-Policy", "no-referrer");
    // 303: the browser always follows with a GET, also after a form submission
    return ResponseEntity.status(HttpStatus.SEE_OTHER).headers(headers).build();
  }

  /** Error returned to the client through its redirect URI, which is already validated. */
  private ResponseEntity<String> redirectError(String redirectUri, String error, String description, String state) {
    return redirectError(redirectUri, error, description, state, new HttpHeaders());
  }

  private ResponseEntity<String> redirectError(String redirectUri, String error, String description, String state,
      HttpHeaders headers) {
    var params = new LinkedHashMap<String, String>();
    params.put("error", error);
    params.put("error_description", description);
    params.put("state", state);
    params.put("iss", authorizationService.getIssuer());
    return redirect(OidcAuthorizationService.appendQuery(redirectUri, params), headers);
  }

  private ResponseEntity<?> tokenResponse(Object body, String origin) {
    return ResponseEntity.ok().headers(apiHeaders(origin)).body(body);
  }

  private ResponseEntity<?> jsonError(OidcException e, String origin) {
    var headers = apiHeaders(origin);
    if ("invalid_client".equals(e.getError())) {
      headers.add(HttpHeaders.WWW_AUTHENTICATE, "Basic");
    }
    return ResponseEntity.status(e.getStatus()).headers(headers).body(errorBody(e));
  }

  private static Map<String, String> errorBody(OidcException e) {
    var body = new LinkedHashMap<String, String>();
    body.put("error", e.getError());
    body.put("error_description", e.getMessage());
    return body;
  }

  /** Headers of the endpoints called by the clients: never cached, CORS for the origins of the clients. */
  private HttpHeaders apiHeaders(String origin) {
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.add(HttpHeaders.PRAGMA, "no-cache");
    if (mtacProperties.getOpenId().getServer().isCorsEnabled() && clientRegistry.isKnownOrigin(origin)) {
      headers.add("Access-Control-Allow-Origin", origin);
      headers.add(HttpHeaders.VARY, "Origin");
    }
    return headers;
  }

  // -------------------------------------------------------------- helpers

  /**
   * Client id and secret, from the Basic authorization header (where both are
   * form-urlencoded, RFC 6749 section 2.3.1) or from the body.
   */
  private static String[] clientCredentials(String clientId, String clientSecret, String authorization) {
    if (authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
      try {
        var decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
        var separator = decoded.indexOf(':');
        if (separator > 0) {
          return new String[] { URLDecoder.decode(decoded.substring(0, separator), StandardCharsets.UTF_8),
              URLDecoder.decode(decoded.substring(separator + 1), StandardCharsets.UTF_8) };
        }
      } catch (IllegalArgumentException e) {
        // falls through to the error below
      }
      throw OidcException.invalidClient("Invalid Basic authorization header");
    }
    if (clientId == null || clientId.isBlank()) {
      throw OidcException.invalidClient("Missing client_id");
    }
    return new String[] { clientId, clientSecret };
  }

  private boolean isCsrfTokenValid(MultiValueMap<String, String> form, HttpServletRequest request) {
    return OidcCrypto.constantTimeEquals(cookieValue(request, CSRF_COOKIE), form.getFirst("csrf"));
  }

  private static boolean hasSeveralValues(MultiValueMap<String, String> params, String name) {
    var values = params.get(name);
    return values != null && values.size() > 1;
  }

  private static String cookieValue(HttpServletRequest request, String name) {
    var cookie = WebUtils.getCookie(request, name);
    return cookie != null ? cookie.getValue() : null;
  }

  private String sessionCookieName() {
    return mtacProperties.getOpenId().getServer().getSession().getCookieName();
  }

  /** A cookie of the provider: never readable by scripts, sent over HTTPS only unless configured otherwise. */
  private String cookie(String name, String value, int maxAgeSec, String sameSite) {
    var secure = mtacProperties.getOpenId().getServer().getSession().isSecure() ? "; Secure" : "";
    return name + "=" + value + "; Path=/; Max-Age=" + maxAgeSec + "; HttpOnly; SameSite=" + sameSite + secure;
  }

  /** Address of the browser: the reverse proxy sets X-Real-IP and overwrites whatever the client sent. */
  private static String clientAddress(HttpServletRequest request) {
    var forwarded = request.getHeader("X-Real-IP");
    return forwarded != null && !forwarded.isBlank() ? forwarded : request.getRemoteAddr();
  }
}
