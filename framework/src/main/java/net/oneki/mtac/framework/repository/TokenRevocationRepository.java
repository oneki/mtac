package net.oneki.mtac.framework.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import net.oneki.mtac.model.core.openid.oidc.TokenRevocation;
import net.oneki.mtac.model.core.openid.oidc.TokenRevocation.Kind;

/**
 * Revocation list of the access tokens (table {@code token_revocation}): a few
 * rows, each one living as long as the longest access token. Every instance
 * reads the new rows every 30 seconds through the index on
 * {@code revoked_at} ({@code TokenRevocationService}).
 */
@Repository
public class TokenRevocationRepository extends AbstractRepository {

  private static final String SQL_INSERT = "INSERT INTO token_revocation (kind, value, revoked_at, expires_at) "
      + "VALUES (:kind, :value, :revoked_at, :expires_at)";

  /** Stores revocations, in one batch. */
  public void insertAll(List<TokenRevocation> revocations) {
    if (revocations == null || revocations.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(SQL_INSERT,
        SqlParameterSourceUtils.createBatch(revocations.stream().map(TokenRevocationRepository::params).toArray()));
  }

  private static final String SQL_LIST_SINCE = "SELECT kind, value, revoked_at, expires_at FROM token_revocation "
      + "WHERE revoked_at > :since AND expires_at > :now";

  /**
   * Revocations written after {@code since} that are still in force at
   * {@code now}: a range scan of the index on {@code revoked_at}.
   */
  public List<TokenRevocation> listSince(Instant since, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("since", Timestamp.from(since));
    params.put("now", Timestamp.from(now));
    return jdbcTemplate.query(SQL_LIST_SINCE, params, (rs, rowNum) -> new TokenRevocation(
        Kind.ofCode(rs.getString("kind")),
        rs.getString("value"),
        rs.getTimestamp("revoked_at").toInstant(),
        rs.getTimestamp("expires_at").toInstant()))
        .stream().filter(revocation -> revocation.kind() != null).toList();
  }

  private static final String SQL_PURGE = "DELETE FROM token_revocation WHERE expires_at <= :now";

  /**
   * Deletes the revocations whose tokens have all expired. The table holds the
   * revocations of the last hour or so: a handful of rows.
   *
   * @return the number of rows deleted
   */
  public int purgeExpired(Instant now) {
    var params = new HashMap<String, Object>();
    params.put("now", Timestamp.from(now));
    return jdbcTemplate.update(SQL_PURGE, params);
  }

  private static final String SQL_TABLE_EXISTS = "SELECT to_regclass('token_revocation') IS NOT NULL";

  /** True when the table {@code token_revocation} exists in the schema of the connection (startup check). */
  public boolean tableExists() {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject(SQL_TABLE_EXISTS, Map.of(), Boolean.class));
  }

  private static Map<String, Object> params(TokenRevocation revocation) {
    var params = new HashMap<String, Object>();
    params.put("kind", revocation.kind().code());
    params.put("value", revocation.value());
    params.put("revoked_at", Timestamp.from(revocation.revokedAt()));
    params.put("expires_at", Timestamp.from(revocation.expiresAt()));
    return params;
  }
}
