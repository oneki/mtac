package net.oneki.mtac.core.service.openid.oidc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import net.oneki.mtac.core.service.openid.ResetPasswordService;
import net.oneki.mtac.model.core.config.MtacProperties;

/**
 * Language and texts of the pages hosted by the OpenID provider (login, mfa, consent, logout, error, forgot and
 * reset password).
 *
 * <p><b>Language</b> ({@link #choose}): the first supported language ({@code mtac.openid.server.locales}) of, in
 * order, the {@code ui_locales} parameter (OpenID Connect, sent by the client on the authorization request), the
 * {@code lang} parameter (links sent by mail), the {@code ui_locales} of the authorization request the page belongs
 * to, the {@value #COOKIE} cookie, the {@code Accept-Language} of the browser, else the first supported language.
 * A language given by a parameter is remembered in the cookie, so that the next pages of the flow (form posts,
 * forgot password, reset) keep it.
 *
 * <p><b>Texts</b>: from the Spring {@link MessageSource} of the application ({@code {{@key}}} in the templates,
 * see {@link OidcPageRenderer}). The messages MTAC writes itself on the pages (errors) are known by their English
 * text ({@link #translateKnown}) and translated with the {@code mtac.oidc.error.*} keys when the application has
 * them; an application without messages keeps the English texts.
 */
@Service
public class OidcPageMessages {
  public static final String COOKIE = "mtac_locale";
  static final int COOKIE_MAX_AGE_SEC = 365 * 24 * 3600;

  public static final String EXPIRED_REQUEST = "This sign-in request has expired. Go back to the application and try again.";
  public static final String STEP_UP_OTHER_USER = "Only the account that is signed in can confirm this request. "
      + "Go back to the application and try again.";
  public static final String TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes.";
  public static final String PAGE_EXPIRED = "This page has expired. Try again.";
  public static final String REQUEST_EXPIRED = "This request has expired. Try again.";
  public static final String INCORRECT_CREDENTIALS = "Incorrect email or password.";
  public static final String INVALID_CODE = "Invalid verification code.";
  public static final String ENTER_EMAIL = "Enter your email address.";
  public static final String PASSWORDS_DIFFER = "The two passwords do not match.";
  public static final String PASSWORD_NOT_CHANGED = "Your password could not be changed. Try again.";

  private record Known(String key, Object... args) {
  }

  /** English text of a message MTAC shows on a page → its key and arguments. */
  private static final Map<String, Known> KNOWN = known();

  private final MtacProperties mtacProperties;
  private final MessageSource messageSource;

  public OidcPageMessages(MtacProperties mtacProperties, MessageSource messageSource) {
    this.mtacProperties = mtacProperties;
    this.messageSource = messageSource;
  }

  private static Map<String, Known> known() {
    var map = new LinkedHashMap<String, Known>();
    map.put(EXPIRED_REQUEST, new Known("mtac.oidc.error.expiredRequest"));
    map.put(STEP_UP_OTHER_USER, new Known("mtac.oidc.error.stepUpOtherUser"));
    map.put(TOO_MANY_ATTEMPTS, new Known("mtac.oidc.error.tooManyAttempts"));
    map.put(PAGE_EXPIRED, new Known("mtac.oidc.error.pageExpired"));
    map.put(REQUEST_EXPIRED, new Known("mtac.oidc.error.requestExpired"));
    map.put(INCORRECT_CREDENTIALS, new Known("mtac.oidc.error.incorrectCredentials"));
    map.put(INVALID_CODE, new Known("mtac.oidc.error.invalidCode"));
    map.put(ENTER_EMAIL, new Known("mtac.oidc.error.enterEmail"));
    map.put(PASSWORDS_DIFFER, new Known("mtac.oidc.error.passwordsDiffer"));
    map.put(PASSWORD_NOT_CHANGED, new Known("mtac.oidc.error.passwordNotChanged"));
    map.put(OidcSignInPolicy.FAILURE_MESSAGE, new Known("mtac.oidc.error.signInFailure"));
    map.put(ResetPasswordService.PASSWORD_EMPTY, new Known("mtac.oidc.error.passwordEmpty"));
    map.put(ResetPasswordService.passwordTooShort(), new Known("mtac.oidc.error.passwordTooShort",
        ResetPasswordService.PASSWORD_MIN_LENGTH));
    map.put(ResetPasswordService.passwordTooLong(), new Known("mtac.oidc.error.passwordTooLong",
        ResetPasswordService.PASSWORD_MAX_BYTES));
    return Collections.unmodifiableMap(map);
  }

  /** The English texts {@link #translateKnown} translates (for the tests of the applications' messages). */
  public static Map<String, String> knownKeys() {
    var keys = new LinkedHashMap<String, String>();
    KNOWN.forEach((english, known) -> keys.put(english, known.key()));
    return keys;
  }

  /** The language of a page and whether to remember it in the cookie. */
  public record Choice(Locale locale, boolean remember) {
  }

  public List<Locale> supported() {
    var result = new ArrayList<Locale>();
    for (var tag : mtacProperties.getOpenId().getServer().getLocales()) {
      if (tag != null && !tag.isBlank()) {
        result.add(Locale.forLanguageTag(tag.trim()));
      }
    }
    return result.isEmpty() ? List.of(Locale.ENGLISH) : result;
  }

  public Locale defaultLocale() {
    return supported().get(0);
  }

  /**
   * @param request         the request of the page, null for the current one
   * @param authorizeLocales {@code ui_locales} of the authorization request the page belongs to, may be null
   */
  public Choice choose(HttpServletRequest request, String authorizeLocales) {
    var current = request != null ? request : currentRequest();
    var cookie = current != null ? supportedOf(cookieValue(current)) : null;
    if (current != null) {
      for (var parameter : List.of("ui_locales", "lang")) {
        var given = supportedOf(current.getParameter(parameter));
        if (given != null) {
          return new Choice(given, !given.equals(cookie));
        }
      }
    }
    var fromAuthorize = supportedOf(authorizeLocales);
    if (fromAuthorize != null) {
      return new Choice(fromAuthorize, !fromAuthorize.equals(cookie));
    }
    if (cookie != null) {
      return new Choice(cookie, false);
    }
    if (current != null) {
      var accepted = current.getLocales();
      while (accepted != null && accepted.hasMoreElements()) {
        var match = supportedOf(accepted.nextElement().toLanguageTag());
        if (match != null) {
          return new Choice(match, false);
        }
      }
    }
    return new Choice(defaultLocale(), false);
  }

  /**
   * The first supported language of a list of tags ({@code ui_locales} is space separated, BCP-47), compared on the
   * language only ({@code fr-BE} gives {@code fr}); null when none is supported.
   */
  Locale supportedOf(String tags) {
    if (tags == null || tags.isBlank()) {
      return null;
    }
    for (var tag : tags.trim().split("[\\s,]+")) {
      var language = Locale.forLanguageTag(tag.replace('_', '-')).getLanguage();
      for (var locale : supported()) {
        if (!language.isEmpty() && locale.getLanguage().equals(language)) {
          return locale;
        }
      }
    }
    return null;
  }

  /** The {@code Set-Cookie} value remembering the language of the pages. */
  public String cookie(Locale locale) {
    var secure = mtacProperties.getOpenId().getServer().getSession().isSecure() ? "; Secure" : "";
    return COOKIE + "=" + locale.toLanguageTag() + "; Path=/; Max-Age=" + COOKIE_MAX_AGE_SEC + "; HttpOnly; SameSite=Lax"
        + secure;
  }

  /** A message of the application, or {@code english} when it has none (formatted with the arguments). */
  public String text(String key, String english, Locale locale, Object... args) {
    return messageSource.getMessage(key, args.length == 0 ? null : args, english, locale);
  }

  /** A message MTAC wrote in English on a page, translated when it is a known one; any other text as is. */
  public String translateKnown(String english, Locale locale) {
    if (english == null) {
      return null;
    }
    var known = KNOWN.get(english);
    return known == null ? english : text(known.key(), english, locale, known.args());
  }

  /** A duration as the pages show it ("31 days", "24 hours", "15 minutes"), in the language of the page. */
  public String duration(int seconds, Locale locale) {
    if (seconds >= 2 * 86400 && seconds % 86400 == 0) { // "24 hours", not "1 day"
      var days = seconds / 86400;
      return text("mtac.oidc.duration.days", days + (days == 1 ? " day" : " days"), locale, days);
    }
    if (seconds >= 3600 && seconds % 3600 == 0) {
      var hours = seconds / 3600;
      return text("mtac.oidc.duration.hours", hours + (hours == 1 ? " hour" : " hours"), locale, hours);
    }
    var minutes = Math.max(1, Math.round(seconds / 60f));
    return text("mtac.oidc.duration.minutes", minutes + (minutes == 1 ? " minute" : " minutes"), locale, minutes);
  }

  private static String cookieValue(HttpServletRequest request) {
    var cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (var cookie : cookies) {
      if (COOKIE.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private static HttpServletRequest currentRequest() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
        ? attributes.getRequest()
        : null;
  }
}
