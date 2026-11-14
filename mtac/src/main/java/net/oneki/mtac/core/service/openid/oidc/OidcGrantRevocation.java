package net.oneki.mtac.core.service.openid.oidc;

import net.oneki.mtac.core.service.openid.ConsoleSessionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.OidcConsentRepository;
import net.oneki.mtac.framework.repository.OidcRepository;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.DefaultResourceService;

/**
 * Ends every access a user delegated, when his credentials change (password
 * changed or reset, MFA changed or enrolled again) or when his account is
 * disabled.
 *
 * <p>
 * {@link #revokeAll(User, String, boolean)}:
 * <ul>
 * <li>revokes every OIDC refresh token of the user ({@code oidc_refresh_token},
 * by {@code identity_id}), whether its SSO session is still alive or not, so no
 * connector can get a new access token any more;</li>
 * <li>deletes the SSO sessions of the user ({@code oidc_session}); their
 * authorization codes go with them (ON DELETE CASCADE). Refresh tokens do not
 * (ON DELETE SET NULL): they outlive a browser logout on purpose, hence the
 * explicit revocation above;</li>
 * <li>deletes the consents of the user ({@code oidc_consent}): the next
 * authorization of any client shows the consent page again;</li>
 * <li>unless {@code keepConsoleSession}, gives the user a new random
 * {@code refreshToken}, which invalidates the Console refresh token (the Console
 * asks for the password once its access token expires);</li>
 * <li>drops the cached userinfo claims of the user ({@link TokenRegistry} and the
 * {@code token} table). These claims carry no credential: this only makes the
 * next request rebuild them from the user;</li>
 * <li>puts the refresh token families and the SSO sessions it ended in the
 * revocation list ({@link TokenRevocationService}): their OIDC access tokens
 * (claims {@code fam}, {@code sid}), however long-lived, are refused within
 * about 30 seconds. The Console access tokens carry neither: the one of the
 * browser kept by {@code keepConsoleSession} stays valid.</li>
 * </ul>
 *
 * <p>
 * The other Console access tokens already issued stay valid until they expire
 * ({@code mtac.jwt.expiration-sec}, 15 min): the revocation stops their
 * renewal. Without a revocation list, the OIDC access tokens in flight live
 * until they expire too.
 *
 * <p>
 * Callers run it inside the transaction of the credential change: if the
 * revocation fails, the exception rolls the credential change back, so a
 * password is never changed while the old sessions survive. The
 * {@link #onRevoked(User, Revocation)} hook runs after the commit (or at once
 * without a transaction) and never fails the operation.
 */
@Service
@Slf4j
public class OidcGrantRevocation {
  /** The user changed his own password. */
  public static final String REASON_PASSWORD_CHANGED = "password-changed";
  /** Somebody else (an administrator) set the password of the user. */
  public static final String REASON_PASSWORD_SET_BY_ADMIN = "password-set-by-admin";
  /** The password was reset with the link sent by mail. */
  public static final String REASON_PASSWORD_RESET = "password-reset";
  /** The MFA requirement of the user was turned on or off. */
  public static final String REASON_MFA_CHANGED = "mfa-changed";
  /** An administrator reset the MFA of the user (lost authenticator): a new secret is to enrol. */
  public static final String REASON_MFA_RESET = "mfa-reset";
  /** A TOTP secret was enrolled (first enrolment or after an MFA reset). */
  public static final String REASON_MFA_ENROLLED = "mfa-enrolled";

  /**
   * What a revocation removed.
   *
   * @param sessions                  SSO sessions deleted, with their codes (the
   *                                  refresh tokens of the user are revoked as
   *                                  well, counted in the log only)
   * @param consents                  consents deleted
   * @param consoleSessionInvalidated true when the Console refresh token was
   *                                  invalidated
   */
  public record Revocation(Integer identityId, String reason, int sessions, int consents,
      boolean consoleSessionInvalidated) {
  }

  protected OidcRepository oidcRepository;
  protected OidcConsentRepository consentRepository;
  protected DefaultResourceService resourceService;
  protected TokenRegistry tokenRegistry;
  protected TokenRepository tokenRepository;

  /**
   * Revokes every delegated access of an identity, loaded by id. See
   * {@link #revokeAll(User, String, boolean)}.
   *
   * @throws IllegalArgumentException if the identity is not a user
   */
  public Revocation revokeAll(Integer identityId, String reason, boolean keepConsoleSession) {
    var identity = resourceService.getByIdUnsecure(identityId);
    if (!(identity instanceof User user)) {
      throw new IllegalArgumentException("Identity " + identityId + " is not a user");
    }
    return revokeAll(user, reason, keepConsoleSession);
  }

  /**
   * Revokes every delegated access of a user: OIDC refresh tokens, SSO sessions
   * (with their codes), consents and, unless {@code keepConsoleSession}, the
   * Console refresh token. The OIDC access tokens of these families and
   * sessions are refused within about 30 seconds (revocation list); the
   * Console access tokens already issued stay valid until they expire (15 min).
   *
   * <p>
   * When the Console refresh token is invalidated, the new value is set on the
   * given object and saved: a caller that saves the same object again afterwards
   * keeps it (or replaces it on purpose, like a login that issues its own).
   *
   * @param reason             one of the {@code REASON_*} constants, logged and
   *                           audited
   * @param keepConsoleSession true to leave the Console refresh token untouched
   *                           (the user changed his own password and stays
   *                           signed in to the Console)
   * @throws RuntimeException any storage error, so that the caller's transaction
   *                          rolls back
   */
  public Revocation revokeAll(User user, String reason, boolean keepConsoleSession) {
    if (user == null || user.getId() == null) {
      throw new IllegalArgumentException("A persisted user is required");
    }
    var identityId = user.getId();
    // read before revoking and deleting them: their access tokens go into the revocation list
    List<String> familyIds = tokenRevocations != null ? oidcRepository.listLiveFamilyIdsOfIdentity(identityId)
        : List.of();
    List<String> sessionIds = tokenRevocations != null ? oidcRepository.listSessionIdsOfIdentity(identityId)
        : List.of();
    // before the sessions: a token stored before identity_id existed is still found through its session
    var refreshTokens = oidcRepository.revokeRefreshTokensOfIdentity(identityId, Instant.now());
    var sessions = oidcRepository.deleteSessionsOfIdentity(identityId);
    var consents = consentRepository.deleteByIdentity(identityId);
    if (!keepConsoleSession) {
      // former single refresh token of the user
      user.setRefreshToken(UUID.randomUUID().toString());
      resourceService.updateUnsecure(user);
    }
    var consoleSessionsClosed = 0;
    if (consoleSessions != null) {
      // every Console session ends; when the user changed his own password, the one of this browser stays
      consoleSessionsClosed = consoleSessions.closeAll(identityId, keepConsoleSession ? currentConsoleSession() : null);
    }
    tokenRegistry.remove(user.getUid());
    tokenRepository.deleteToken(identityId);
    if (tokenRevocations != null) {
      // same transaction: a failure rolls the credential change back, like the rest
      tokenRevocations.revokeFamilies(familyIds, reason);
      tokenRevocations.revokeSessions(sessionIds, reason);
    }

    var revocation = new Revocation(identityId, reason, sessions, consents,
        !keepConsoleSession || consoleSessionsClosed > 0);
    log.info(
        "Delegated access revoked for user {} (id {}), reason {}: {} OIDC refresh token(s) revoked ({} live famil(ies)), {} SSO session(s) with their codes, {} consent(s), {} Console session(s) closed{}",
        user.getLabel(), identityId, reason, refreshTokens, familyIds.size(), sessions, consents, consoleSessionsClosed,
        keepConsoleSession ? " (the current browser stays signed in)" : "");
    afterCommit(() -> {
      try {
        onRevoked(user, revocation);
      } catch (RuntimeException e) {
        log.error("Revocation hook failed for user {} (id {}), reason {}", user.getLabel(), identityId, reason, e);
      }
    });
    return revocation;
  }

  /**
   * Called once the revocation is committed (at once when there is no
   * transaction), e.g. to write an audit entry. Exceptions are logged and
   * ignored: the revocation is already done.
   */
  protected void onRevoked(User user, Revocation revocation) {
  }

  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          action.run();
        }
      });
    } else {
      action.run();
    }
  }

  /** Console session of the request (csid claim of its access token), null outside a Console request. */
  protected String currentConsoleSession() {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
        .getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof
        org.springframework.security.oauth2.core.user.OAuth2User principal) {
      var value = principal.getAttributes().get(ConsoleSessionService.CLAIM);
      return value != null ? value.toString() : null;
    }
    return null;
  }

  protected ConsoleSessionService consoleSessions;
  protected TokenRevocationService tokenRevocations; // null: the OIDC access tokens in flight live until they expire

  @Autowired(required = false)
  public void setTokenRevocationService(TokenRevocationService tokenRevocations) {
    this.tokenRevocations = tokenRevocations;
  }

  @Autowired
  public void setConsoleSessions(ConsoleSessionService consoleSessions) {
    this.consoleSessions = consoleSessions;
  }

  @Autowired
  public void setOidcRepository(OidcRepository oidcRepository) {
    this.oidcRepository = oidcRepository;
  }

  @Autowired
  public void setConsentRepository(OidcConsentRepository consentRepository) {
    this.consentRepository = consentRepository;
  }

  @Autowired
  public void setResourceService(DefaultResourceService resourceService) {
    this.resourceService = resourceService;
  }

  @Autowired
  public void setTokenRegistry(TokenRegistry tokenRegistry) {
    this.tokenRegistry = tokenRegistry;
  }

  @Autowired
  public void setTokenRepository(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }
}
