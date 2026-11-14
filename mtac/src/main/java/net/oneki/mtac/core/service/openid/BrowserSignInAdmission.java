package net.oneki.mtac.core.service.openid;

import java.util.Optional;

import net.oneki.mtac.model.resource.iam.identity.user.User;

/**
 * Hook of the application: may refuse the Console session of a user who has
 * just signed in through the OpenID provider (backend for frontend,
 * {@code mtac.iam.browser-session}), for example an account meant for another
 * application only. Optional bean: without it, every user who signs in gets a
 * Console session.
 *
 * <p>
 * It is called by {@code GET <api>/auth/callback} once the code is exchanged
 * and the ID token checked, before any Console session is opened. On a
 * refusal the callback opens no Console session, ends the SSO session the
 * sign-in comes from (so that the next sign-in asks for credentials again and
 * the user may choose another account), closes the Console session this
 * browser still had, clears the session cookies, and redirects the browser to
 * {@link Refusal#consolePath()} on the Console origin.
 *
 * <p>
 * The hook decides who may use the Console, not what a token may do: it is not
 * a security boundary (the API checks every request). A hook that throws is
 * logged and the Console session is opened as without it, so that a fault of
 * the application never locks every user out of the Console.
 */
@FunctionalInterface
public interface BrowserSignInAdmission {

  /**
   * @param user the user who signed in
   * @return empty to open the Console session, else the refusal
   */
  Optional<Refusal> check(User user);

  /**
   * A refused Console sign-in.
   *
   * @param reason      short reason, written to the log only
   * @param consolePath public Console route the browser is redirected to: an
   *                    absolute path ({@code /…}), on the Console origin of the
   *                    sign-in, without scheme, host or query
   */
  record Refusal(String reason, String consolePath) {
    public Refusal {
      if (consolePath == null || !consolePath.startsWith("/") || consolePath.startsWith("//")
          || consolePath.contains("\\") || consolePath.contains("?") || consolePath.contains("#")
          || consolePath.chars().anyMatch(Character::isISOControl)) {
        throw new IllegalArgumentException("The Console path of a refused sign-in must be an absolute path");
      }
    }
  }
}
