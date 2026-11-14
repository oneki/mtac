package net.oneki.mtac.core.service.openid.oidc;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.repository.TokenRevocationRepository;

/**
 * Startup check: the table {@code token_revocation} of the revocation list
 * ({@link TokenRevocationService}) must exist. Without it, every credential
 * change (password change or reset, MFA enrolment, account disabled), sign-out
 * and refresh token revocation would fail when it writes its row, and the
 * revocations of the other instances would never be read. The application does
 * not start until the migration is applied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenRevocationTableCheck {
  /** Where the migration is, in the message of the failure. */
  public static final String MIGRATION_HINT = "apply the idempotent OpenID schema migration that creates it (Fuzz api: "
      + "api/src/main/resources/sql/oidc/schema.sql; MTAC: framework/src/main/resources/sql/framework/init.sql)";

  private final TokenRevocationRepository repository;

  @PostConstruct
  public void check() {
    boolean exists;
    try {
      exists = repository.tableExists();
    } catch (RuntimeException e) {
      throw new IllegalStateException("Unable to check that the table token_revocation exists: " + e.getMessage(), e);
    }
    if (!exists) {
      throw new IllegalStateException("The table token_revocation (revocation list of the access tokens) is missing: "
          + MIGRATION_HINT);
    }
    log.debug("The table token_revocation exists");
  }
}
