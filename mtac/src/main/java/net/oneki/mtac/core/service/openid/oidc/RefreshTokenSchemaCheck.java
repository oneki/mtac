package net.oneki.mtac.core.service.openid.oidc;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.repository.OidcRepository;

/**
 * Startup check: the column {@code oidc_refresh_token.previous_hash} (grace
 * window of a reused refresh token, {@link OidcAuthorizationService#graceSuccessor})
 * must exist. Every code exchange and every refresh stores a refresh token with
 * it: without the column, no client could sign in nor stay signed in. The
 * application does not start until the migration is applied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenSchemaCheck {
  private final OidcRepository repository;

  @PostConstruct
  public void check() {
    boolean exists;
    try {
      exists = repository.refreshTokenPreviousHashColumnExists();
    } catch (RuntimeException e) {
      throw new IllegalStateException("Unable to check that the column oidc_refresh_token.previous_hash exists: "
          + e.getMessage(), e);
    }
    if (!exists) {
      throw new IllegalStateException("The column oidc_refresh_token.previous_hash (grace window of a reused refresh "
          + "token) is missing: " + TokenRevocationTableCheck.MIGRATION_HINT);
    }
    log.debug("The column oidc_refresh_token.previous_hash exists");
  }
}
