package net.oneki.mtac.core.service.openid.oidc;

import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;

/**
 * Extension point of {@link OidcClientRegistry} for the clients that are
 * neither declared in yml nor stored as iam.oidc.client resources, e.g. Client
 * ID Metadata Documents (step D4: the client_id is an HTTPS URL whose document
 * describes the client).
 *
 * <p>
 * Every Spring bean implementing this interface is consulted, in
 * {@link org.springframework.core.annotation.Order} order, for the client_ids
 * it {@link #supports(String) supports}; the resource lookup is then skipped
 * for those ids. A resolver caches what it fetches itself: the registry does
 * not cache its answers.
 */
public interface OidcClientResolver {

  /** True when this resolver is responsible for the client_id (e.g. it starts with https://). */
  boolean supports(String clientId);

  /** The client, or null when it cannot be resolved (unknown, invalid, not allowed). */
  MtacOidcClient resolve(String clientId);

  /**
   * The client when this resolver already holds it, else null; never does any
   * I/O (network fetch, database). For display only, e.g. the list of a user's
   * consents, where a lookup per row must not trigger a fetch. The default
   * knows nothing.
   */
  default MtacOidcClient peek(String clientId) {
    return null;
  }

  /** True when the origin belongs to one of the clients this resolver knows (CORS). */
  default boolean isKnownOrigin(String origin) {
    return false;
  }
}
