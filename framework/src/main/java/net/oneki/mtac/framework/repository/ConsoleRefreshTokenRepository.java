package net.oneki.mtac.framework.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;

import org.springframework.stereotype.Repository;

import net.oneki.mtac.model.core.openid.ConsoleRefreshToken;

/** Table console_refresh_token: the refresh tokens of the Console logins. */
@Repository
public class ConsoleRefreshTokenRepository extends AbstractRepository {

  private static final String SQL_INSERT = "INSERT INTO console_refresh_token (id, token_hash, identity_id, created_at, last_used_at, expires_at, user_agent, sso_session_id) "
      + "VALUES (:id, :token_hash, :identity_id, :created_at, :last_used_at, :expires_at, :user_agent, :sso_session_id)";

  public void insert(ConsoleRefreshToken token) {
    var params = new HashMap<String, Object>();
    params.put("id", token.getId());
    params.put("token_hash", token.getTokenHash());
    params.put("identity_id", token.getIdentityId());
    params.put("created_at", Timestamp.from(token.getCreatedAt()));
    params.put("last_used_at", Timestamp.from(token.getLastUsedAt()));
    params.put("expires_at", Timestamp.from(token.getExpiresAt()));
    params.put("user_agent", token.getUserAgent());
    params.put("sso_session_id", token.getSsoSessionId());
    jdbcTemplate.update(SQL_INSERT, params);
  }

  private static final String SQL_GET_BY_HASH = "SELECT * FROM console_refresh_token WHERE token_hash = :token_hash";

  public ConsoleRefreshToken getByHash(String tokenHash) {
    var params = new HashMap<String, Object>();
    params.put("token_hash", tokenHash);
    var rows = jdbcTemplate.query(SQL_GET_BY_HASH, params, (rs, rowNum) -> map(rs));
    return rows.isEmpty() ? null : rows.get(0);
  }

  private static final String SQL_SLIDE = "UPDATE console_refresh_token SET last_used_at = :now, expires_at = :expires_at "
      + "WHERE id = :id AND revoked_at IS NULL";

  /** A refresh used the token: the inactivity window starts again (expires_at already capped by the caller). */
  public void slide(String id, Instant now, Instant expiresAt) {
    var params = new HashMap<String, Object>();
    params.put("id", id);
    params.put("now", Timestamp.from(now));
    params.put("expires_at", Timestamp.from(expiresAt));
    jdbcTemplate.update(SQL_SLIDE, params);
  }

  private static final String SQL_REVOKE = "UPDATE console_refresh_token SET revoked_at = :now WHERE id = :id AND revoked_at IS NULL";

  /** Ends one Console session (logout). */
  public int revoke(String id, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("id", id);
    params.put("now", Timestamp.from(now));
    return jdbcTemplate.update(SQL_REVOKE, params);
  }

  private static final String SQL_START_IMPERSONATION = "UPDATE console_refresh_token "
      + "SET impersonated_identity_id = :identity_id, impersonation_expires_at = :expires_at "
      + "WHERE id = :id AND revoked_at IS NULL";

  /**
   * The user of a live Console session starts impersonating another identity
   * until {@code expiresAt}: the renewals of the session mint impersonation
   * tokens meanwhile.
   *
   * @return 1 when the session was updated, 0 when it is unknown or revoked
   */
  public int startImpersonation(String id, Integer identityId, Instant expiresAt) {
    var params = new HashMap<String, Object>();
    params.put("id", id);
    params.put("identity_id", identityId);
    params.put("expires_at", Timestamp.from(expiresAt));
    return jdbcTemplate.update(SQL_START_IMPERSONATION, params);
  }

  private static final String SQL_STOP_IMPERSONATION = "UPDATE console_refresh_token "
      + "SET impersonated_identity_id = NULL, impersonation_expires_at = NULL WHERE id = :id";

  /** Ends the impersonation of a Console session (exit, expiry). */
  public int stopImpersonation(String id) {
    var params = new HashMap<String, Object>();
    params.put("id", id);
    return jdbcTemplate.update(SQL_STOP_IMPERSONATION, params);
  }

  private static final String SQL_REVOKE_OF_IDENTITY ="UPDATE console_refresh_token SET revoked_at = :now "
      + "WHERE identity_id = :identity_id AND revoked_at IS NULL AND (CAST(:keep_id AS char(32)) IS NULL OR id <> :keep_id)";

  /**
   * Ends every Console session of an identity, except the one kept (the
   * browser in which the user changed his own password), null for none.
   *
   * @return the number of sessions ended
   */
  public int revokeOfIdentity(Integer identityId, String keepId, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    params.put("keep_id", keepId);
    params.put("now", Timestamp.from(now));
    return jdbcTemplate.update(SQL_REVOKE_OF_IDENTITY, params);
  }

  private static final String SQL_REVOKE_BY_SSO_SESSION = "UPDATE console_refresh_token SET revoked_at = :now "
      + "WHERE sso_session_id = :sso_session_id AND revoked_at IS NULL";

  /**
   * Ends the Console sessions opened from an SSO session of the OpenID
   * provider, which just ended (logout, expiry).
   *
   * @return the number of sessions ended
   */
  public int revokeBySsoSession(String ssoSessionId, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("sso_session_id", ssoSessionId);
    params.put("now", Timestamp.from(now));
    return jdbcTemplate.update(SQL_REVOKE_BY_SSO_SESSION, params);
  }

  private static final String SQL_REVOKE_ORPHANED_SSO_SESSIONS = "UPDATE console_refresh_token SET revoked_at = :now "
      + "WHERE sso_session_id IS NOT NULL AND revoked_at IS NULL "
      + "AND NOT EXISTS (SELECT 1 FROM oidc_session s WHERE s.id = console_refresh_token.sso_session_id)";

  /**
   * Ends the Console sessions whose SSO session no longer exists, whatever
   * deleted it (expiry purge, revocation of every session of a user).
   *
   * @return the number of sessions ended
   */
  public int revokeOrphanedSsoSessions(Instant now) {
    var params = new HashMap<String, Object>();
    params.put("now", Timestamp.from(now));
    return jdbcTemplate.update(SQL_REVOKE_ORPHANED_SSO_SESSIONS, params);
  }

  private static final String SQL_PURGE ="DELETE FROM console_refresh_token WHERE expires_at < :now OR revoked_at < :revoked_before";

  /** Deletes the expired tokens and those revoked before the given instant. */
  public int purge(Instant now, Instant revokedBefore) {
    var params = new HashMap<String, Object>();
    params.put("now", Timestamp.from(now));
    params.put("revoked_before", Timestamp.from(revokedBefore));
    return jdbcTemplate.update(SQL_PURGE, params);
  }

  private static ConsoleRefreshToken map(ResultSet rs) throws SQLException {
    return ConsoleRefreshToken.builder()
        .id(rs.getString("id"))
        .tokenHash(rs.getString("token_hash"))
        .identityId(rs.getInt("identity_id"))
        .createdAt(instant(rs, "created_at"))
        .lastUsedAt(instant(rs, "last_used_at"))
        .expiresAt(instant(rs, "expires_at"))
        .revokedAt(instant(rs, "revoked_at"))
        .userAgent(rs.getString("user_agent"))
        .ssoSessionId(rs.getString("sso_session_id"))
        .impersonatedIdentityId((Integer) rs.getObject("impersonated_identity_id"))
        .impersonationExpiresAt(instant(rs, "impersonation_expires_at"))
        .build();
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    var timestamp = rs.getTimestamp(column);
    return timestamp != null ? timestamp.toInstant() : null;
  }
}
