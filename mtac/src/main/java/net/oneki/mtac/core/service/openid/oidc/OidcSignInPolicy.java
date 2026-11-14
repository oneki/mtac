package net.oneki.mtac.core.service.openid.oidc;

import java.util.List;
import java.util.Optional;

import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;

/**
 * Hook of the application: may refuse that a user signs in to a client, for
 * example a client that requires a second factor from some users only (the
 * client attribute {@code require-mfa} applies to every user). Optional beans
 * (any of them may refuse): without one every sign-in the provider accepts
 * goes on.
 *
 * <p>
 * The hosted pages ask it before any authorization code is issued:
 * <ul>
 * <li>after the password step, and after the second factor, before the SSO
 * session is opened: a refusal is shown on the sign-in page, with the
 * returned message;</li>
 * <li>when an existing SSO session would answer the authorization request: a
 * refused session is not enough, the user signs in again (with the second
 * factor if they have one); with {@code prompt=none} the client gets
 * {@code login_required};</li>
 * <li>right before the code is issued (the consent page included): a refusal
 * is shown on an error page of the provider.</li>
 * </ul>
 * A policy that throws refuses the sign-in (fail closed).
 *
 * <p>
 * Every policy is asked for every client: an implementation must first check
 * the client of the request ({@link Request#clientId()} or
 * {@link Request#client()}) and answer empty for the clients it does not
 * govern, before any other work. Since a failure refuses the sign-in, a policy
 * that loads data (or throws) for clients it does not concern would block the
 * sign-in to every client, the Console included, when that data is
 * unavailable.
 */
@FunctionalInterface
public interface OidcSignInPolicy {

  /** Message shown when a policy fails: it is a security check, the sign-in is refused. */
  String FAILURE_MESSAGE = "The sign-in cannot be completed right now. Try again later.";

  /**
   * @return empty to let the sign-in go on, or the message shown to the user
   *         when it is refused (plain text, the page escapes it)
   */
  Optional<String> refusal(Request request);

  /**
   * What the decision may depend on.
   *
   * @param clientId   the client the user signs in to
   * @param client     its configuration
   * @param sub        uid of the user
   * @param identityId id of the user, null when unknown
   * @param amr        authentication methods of this sign-in (RFC 8176:
   *                   {@code pwd}, {@code otp} once the TOTP code is checked),
   *                   never null
   */
  record Request(String clientId, MtacOidcClient client, String sub, Integer identityId, List<String> amr) {
    public Request {
      amr = amr == null ? List.of() : List.copyOf(amr);
    }
  }
}
