package net.oneki.mtac.framework.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Repository;

import net.oneki.mtac.model.core.openid.oidc.OidcAuthorizationCode;
import net.oneki.mtac.model.core.openid.oidc.OidcRefreshToken;
import net.oneki.mtac.model.core.openid.oidc.OidcSession;

/**
 * Storage of the OpenID provider: SSO sessions, authorization codes and refresh
 * tokens. Secrets are stored hashed. Codes are deleted with their session (ON
 * DELETE CASCADE). Refresh tokens are not: they carry the identity they were
 * issued for, and ending their session only clears their {@code session_id}
 * (ON DELETE SET NULL); they are deleted with their identity (ON DELETE
 * CASCADE).
 */
@Repository
public class OidcRepository extends AbstractRepository {

  // ---------------------------------------------------------------- sessions

  private static final String SQL_INSERT_SESSION = "INSERT INTO oidc_session (id, identity_id, sub, auth_time, amr, last_seen_at, expires_at) "
      + "VALUES (:id, :identity_id, :sub, :auth_time, :amr, :last_seen_at, :expires_at)";

  public void insertSession(OidcSession session) {
    var params = new HashMap<String, Object>();
    params.put("id", session.getId());
    params.put("identity_id", session.getIdentityId());
    params.put("sub", session.getSub());
    params.put("auth_time", Timestamp.from(session.getAuthTime()));
    params.put("amr", String.join(" ", session.getAmr()));
    params.put("last_seen_at", Timestamp.from(session.getLastSeenAt()));
    params.put("expires_at", Timestamp.from(session.getExpiresAt()));
    jdbcTemplate.update(SQL_INSERT_SESSION, params);
  }

  private static final String SQL_GET_SESSION = "SELECT * FROM oidc_session WHERE id = :id";

  public OidcSession getSession(String id) {
    var params = new HashMap<String, Object>();
    params.put("id", id);
    return first(jdbcTemplate.query(SQL_GET_SESSION, params, (rs, rowNum) -> OidcSession.builder()
        .id(rs.getString("id"))
        .identityId(rs.getInt("identity_id"))
        .sub(rs.getString("sub"))
        .authTime(instant(rs, "auth_time"))
        .amr(Arrays.asList(rs.getString("amr").split(" ")))
        .lastSeenAt(instant(rs, "last_seen_at"))
        .expiresAt(instant(rs, "expires_at"))
        .build()));
  }

  private static final String SQL_TOUCH_SESSION = "UPDATE oidc_session SET last_seen_at = :now WHERE id = :id";

  public void touchSession(String id, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("id", id);
    params.put("now", Timestamp.from(now));
    jdbcTemplate.update(SQL_TOUCH_SESSION, params);
  }

  private static final String SQL_DELETE_SESSION = "DELETE FROM oidc_session WHERE id = :id";

  public void deleteSession(String id) {
    var params = new HashMap<String, Object>();
    params.put("id", id);
    jdbcTemplate.update(SQL_DELETE_SESSION, params);
  }

  private static final String SQL_UPDATE_SESSION_AUTHENTICATION = "UPDATE oidc_session SET auth_time = :auth_time, amr = :amr, last_seen_at = :auth_time WHERE id = :id";

  /** The user proved himself again in this session (step-up): new authentication time and methods. */
  public void updateSessionAuthentication(String id, Instant authTime, Collection<String> amr) {
    var params = new HashMap<String, Object>();
    params.put("id", id);
    params.put("auth_time", Timestamp.from(authTime));
    params.put("amr", String.join(" ", amr));
    jdbcTemplate.update(SQL_UPDATE_SESSION_AUTHENTICATION, params);
  }

  private static final String SQL_DELETE_SESSIONS_OF_IDENTITY = "DELETE FROM oidc_session WHERE identity_id = :identity_id";

  /**
   * Ends every SSO session of an identity. The authorization codes of these
   * sessions go with them (ON DELETE CASCADE); their refresh tokens do NOT (ON
   * DELETE SET NULL): see {@link #revokeRefreshTokensOfIdentity(Integer, Instant)}.
   *
   * @return the number of sessions deleted
   */
  public int deleteSessionsOfIdentity(Integer identityId) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    return jdbcTemplate.update(SQL_DELETE_SESSIONS_OF_IDENTITY, params);
  }

  // ---------------------------------------------------- authorization codes

  private static final String SQL_INSERT_CODE = "INSERT INTO oidc_authorization_code (code_hash, client_id, session_id, redirect_uri, scope, nonce, code_challenge, expires_at, resource) "
      + "VALUES (:code_hash, :client_id, :session_id, :redirect_uri, :scope, :nonce, :code_challenge, :expires_at, :resource)";

  public void insertAuthorizationCode(OidcAuthorizationCode code) {
    var params = new HashMap<String, Object>();
    params.put("code_hash", code.getCodeHash());
    params.put("client_id", code.getClientId());
    params.put("session_id", code.getSessionId());
    params.put("redirect_uri", code.getRedirectUri());
    params.put("scope", code.getScope());
    params.put("nonce", code.getNonce());
    params.put("code_challenge", code.getCodeChallenge());
    params.put("expires_at", Timestamp.from(code.getExpiresAt()));
    params.put("resource", code.getResource());
    jdbcTemplate.update(SQL_INSERT_CODE, params);
  }

  private static final String SQL_GET_CODE = "SELECT * FROM oidc_authorization_code WHERE code_hash = :code_hash";

  public OidcAuthorizationCode getAuthorizationCode(String codeHash) {
    var params = new HashMap<String, Object>();
    params.put("code_hash", codeHash);
    return first(jdbcTemplate.query(SQL_GET_CODE, params, (rs, rowNum) -> OidcAuthorizationCode.builder()
        .codeHash(rs.getString("code_hash"))
        .clientId(rs.getString("client_id"))
        .sessionId(rs.getString("session_id"))
        .redirectUri(rs.getString("redirect_uri"))
        .scope(rs.getString("scope"))
        .nonce(rs.getString("nonce"))
        .codeChallenge(rs.getString("code_challenge"))
        .expiresAt(instant(rs, "expires_at"))
        .consumedAt(instant(rs, "consumed_at"))
        .resource(rs.getString("resource"))
        .build()));
  }

  private static final String SQL_CONSUME_CODE = "UPDATE oidc_authorization_code SET consumed_at = :now WHERE code_hash = :code_hash AND consumed_at IS NULL";

  /**
   * Marks the code as used.
   *
   * @return false if it was already used: a code is valid only once, even when
   *         two requests present it at the same time.
   */
  public boolean consumeAuthorizationCode(String codeHash, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("code_hash", codeHash);
    params.put("now", Timestamp.from(now));
    return jdbcTemplate.update(SQL_CONSUME_CODE, params) == 1;
  }

  // --------------------------------------------------------- refresh tokens

  private static final String SQL_INSERT_REFRESH_TOKEN = "INSERT INTO oidc_refresh_token (token_hash, family_id, client_id, session_id, scope, expires_at, resource, family_started_at, identity_id, sub, auth_time, amr) "
      + "VALUES (:token_hash, :family_id, :client_id, :session_id, :scope, :expires_at, :resource, :family_started_at, :identity_id, :sub, :auth_time, :amr)";

  /**
   * Stores a refresh token. {@code sessionId} may be null (the family outlived
   * its SSO session); the identity, sub, auth_time and amr of the family are
   * stored with each token so that a refresh does not need the session.
   */
  public void insertRefreshToken(OidcRefreshToken token) {
    var params = new HashMap<String, Object>();
    params.put("token_hash", token.getTokenHash());
    params.put("family_id", token.getFamilyId());
    params.put("client_id", token.getClientId());
    params.put("session_id", token.getSessionId());
    params.put("scope", token.getScope());
    params.put("expires_at", Timestamp.from(token.getExpiresAt()));
    params.put("resource", token.getResource());
    params.put("family_started_at", token.getFamilyStartedAt() != null ? Timestamp.from(token.getFamilyStartedAt()) : null);
    params.put("identity_id", token.getIdentityId());
    params.put("sub", token.getSub());
    params.put("auth_time", token.getAuthTime() != null ? Timestamp.from(token.getAuthTime()) : null);
    // space separated, like oidc_session.amr
    params.put("amr", token.getAmr() != null ? String.join(" ", token.getAmr()) : null);
    jdbcTemplate.update(SQL_INSERT_REFRESH_TOKEN, params);
  }

  private static final String SQL_GET_REFRESH_TOKEN = "SELECT * FROM oidc_refresh_token WHERE token_hash = :token_hash";

  public OidcRefreshToken getRefreshToken(String tokenHash) {
    var params = new HashMap<String, Object>();
    params.put("token_hash", tokenHash);
    return first(jdbcTemplate.query(SQL_GET_REFRESH_TOKEN, params, (rs, rowNum) -> OidcRefreshToken.builder()
        .tokenHash(rs.getString("token_hash"))
        .familyId(rs.getString("family_id"))
        .clientId(rs.getString("client_id"))
        .sessionId(rs.getString("session_id"))
        .scope(rs.getString("scope"))
        .expiresAt(instant(rs, "expires_at"))
        .rotatedAt(instant(rs, "rotated_at"))
        .revokedAt(instant(rs, "revoked_at"))
        .resource(rs.getString("resource"))
        .familyStartedAt(instant(rs, "family_started_at"))
        .identityId(integer(rs, "identity_id"))
        .sub(rs.getString("sub"))
        .authTime(instant(rs, "auth_time"))
        .amr(amr(rs.getString("amr")))
        .build()));
  }

  private static final String SQL_ROTATE_REFRESH_TOKEN = "UPDATE oidc_refresh_token SET rotated_at = :now "
      + "WHERE token_hash = :token_hash AND rotated_at IS NULL AND revoked_at IS NULL";

  /**
   * Marks the refresh token as exchanged for its successor.
   *
   * @return false if it was already exchanged or revoked.
   */
  public boolean rotateRefreshToken(String tokenHash, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("token_hash", tokenHash);
    params.put("now", Timestamp.from(now));
    return jdbcTemplate.update(SQL_ROTATE_REFRESH_TOKEN, params) == 1;
  }

  private static final String SQL_REVOKE_REFRESH_TOKEN_FAMILY = "UPDATE oidc_refresh_token SET revoked_at = :now "
      + "WHERE family_id = :family_id AND revoked_at IS NULL";

  public void revokeRefreshTokenFamily(String familyId, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("family_id", familyId);
    params.put("now", Timestamp.from(now));
    jdbcTemplate.update(SQL_REVOKE_REFRESH_TOKEN_FAMILY, params);
  }

  // a token stored before identity_id existed is still found through its session
  private static final String SQL_REVOKE_REFRESH_TOKENS_OF_IDENTITY = "UPDATE oidc_refresh_token SET revoked_at = :now "
      + "WHERE revoked_at IS NULL AND (identity_id = :identity_id "
      + "OR session_id IN (SELECT id FROM oidc_session WHERE identity_id = :identity_id))";

  /**
   * Revokes every refresh token of an identity, of every client, with or
   * without a live SSO session (credentials changed, account disabled). Revoked
   * tokens are kept until they expire, like the other revoked ones.
   *
   * @return the number of tokens revoked
   */
  public int revokeRefreshTokensOfIdentity(Integer identityId, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    params.put("now", Timestamp.from(now));
    return jdbcTemplate.update(SQL_REVOKE_REFRESH_TOKENS_OF_IDENTITY, params);
  }

  // a token stored before identity_id existed is still found through its session
  private static final String SQL_LIST_LIVE_FAMILIES_OF_IDENTITY = "SELECT DISTINCT family_id FROM oidc_refresh_token "
      + "WHERE revoked_at IS NULL AND (identity_id = :identity_id "
      + "OR session_id IN (SELECT id FROM oidc_session WHERE identity_id = :identity_id))";

  /**
   * The refresh token families of an identity that are not revoked yet, of
   * every client: read before {@link #revokeRefreshTokensOfIdentity} so that
   * their access tokens can be revoked as well.
   */
  public List<String> listLiveFamilyIdsOfIdentity(Integer identityId) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    return jdbcTemplate.queryForList(SQL_LIST_LIVE_FAMILIES_OF_IDENTITY, params, String.class);
  }

  private static final String SQL_LIST_SESSIONS_OF_IDENTITY = "SELECT id FROM oidc_session WHERE identity_id = :identity_id";

  /** Ids of the SSO sessions of an identity: read before {@link #deleteSessionsOfIdentity}. */
  public List<String> listSessionIdsOfIdentity(Integer identityId) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    return jdbcTemplate.queryForList(SQL_LIST_SESSIONS_OF_IDENTITY, params, String.class);
  }

  // ------------------------------------------------------ client revocation

  private static final String SQL_REVOKE_REFRESH_TOKENS_OF_CLIENT = "UPDATE oidc_refresh_token SET revoked_at = :now "
      + "WHERE client_id = :client_id AND revoked_at IS NULL";

  /**
   * Revokes every refresh token of a client (client deleted, or its redirect URIs
   * changed).
   *
   * @return the number of tokens revoked
   */
  public int revokeRefreshTokensOfClient(String clientId, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("client_id", clientId);
    params.put("now", Timestamp.from(now));
    return jdbcTemplate.update(SQL_REVOKE_REFRESH_TOKENS_OF_CLIENT, params);
  }

  private static final String SQL_DELETE_UNUSED_CODES_OF_CLIENT = "DELETE FROM oidc_authorization_code "
      + "WHERE client_id = :client_id AND consumed_at IS NULL";

  /**
   * Deletes the authorization codes of a client that were not exchanged yet.
   * Used codes are kept: presenting one again must still be detected as a
   * replay.
   */
  public int deleteUnusedAuthorizationCodesOfClient(String clientId) {
    var params = new HashMap<String, Object>();
    params.put("client_id", clientId);
    return jdbcTemplate.update(SQL_DELETE_UNUSED_CODES_OF_CLIENT, params);
  }

  // ---------------------------------------------------------------- cleanup

  private static final String SQL_DELETE_EXPIRED_SESSIONS = "DELETE FROM oidc_session WHERE expires_at < :now OR last_seen_at < :idle_limit";
  private static final String SQL_DELETE_EXPIRED_CODES = "DELETE FROM oidc_authorization_code WHERE expires_at < :code_limit";
  private static final String SQL_DELETE_EXPIRED_REFRESH_TOKENS = "DELETE FROM oidc_refresh_token WHERE expires_at < :now";

  /**
   * Deletes what can no longer be used. Used codes are kept for a while after
   * their expiration so that presenting one again is still detected as a replay.
   * Refresh tokens are deleted on their own expiration only: an expired session
   * leaves them with a null {@code session_id}.
   */
  public void deleteExpired(Instant now, Instant idleLimit, Instant codeLimit) {
    var params = new HashMap<String, Object>();
    params.put("now", Timestamp.from(now));
    params.put("idle_limit", Timestamp.from(idleLimit));
    params.put("code_limit", Timestamp.from(codeLimit));
    jdbcTemplate.update(SQL_DELETE_EXPIRED_REFRESH_TOKENS, params);
    jdbcTemplate.update(SQL_DELETE_EXPIRED_CODES, params);
    jdbcTemplate.update(SQL_DELETE_EXPIRED_SESSIONS, params);
  }

  private static Integer integer(ResultSet rs, String column) throws SQLException {
    var value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  /** Space separated methods, null or empty list when the column is null or blank. */
  private static List<String> amr(String value) {
    if (value == null) {
      return null;
    }
    return value.isBlank() ? List.of() : Arrays.asList(value.trim().split(" +"));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    var timestamp = rs.getTimestamp(column);
    return timestamp != null ? timestamp.toInstant() : null;
  }

  private static <T> T first(List<T> rows) {
    return rows.isEmpty() ? null : rows.get(0);
  }
}
