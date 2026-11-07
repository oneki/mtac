package net.oneki.mtac.framework.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;

import org.springframework.stereotype.Repository;

/**
 * Replay protection of the client assertions ({@code private_key_jwt}, RFC 7523):
 * the {@code jti} of every accepted assertion is kept until the assertion
 * itself can no longer be accepted, so that presenting it again is refused.
 * Table {@code oauth_client_assertion_jti}, primary key (client_id, jti) so that
 * a client cannot burn the identifiers of another one.
 */
@Repository
public class ClientAssertionJtiRepository extends AbstractRepository {

  private static final String SQL_INSERT = "INSERT INTO oauth_client_assertion_jti (client_id, jti, expires_at) "
      + "VALUES (:client_id, :jti, :expires_at) ON CONFLICT DO NOTHING";

  /**
   * Stores a jti atomically.
   *
   * @param expiresAt when the row may be purged: the assertion expiration plus
   *                  the accepted clock skew
   * @return true when the jti was stored, false when it was already there (replay)
   */
  public boolean tryStore(String jti, String clientId, Instant expiresAt) {
    var params = new HashMap<String, Object>();
    params.put("client_id", clientId);
    params.put("jti", jti);
    params.put("expires_at", Timestamp.from(expiresAt));
    return jdbcTemplate.update(SQL_INSERT, params) == 1;
  }

  private static final String SQL_PURGE = "DELETE FROM oauth_client_assertion_jti WHERE expires_at < :now";

  /** @return the number of rows deleted */
  public int purgeExpired(Instant now) {
    var params = new HashMap<String, Object>();
    params.put("now", Timestamp.from(now));
    return jdbcTemplate.update(SQL_PURGE, params);
  }
}
