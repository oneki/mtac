package net.oneki.mtac.framework.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import net.oneki.mtac.model.core.openid.oidc.OidcConsent;

/**
 * Storage of the consents of the OpenID provider (table {@code oidc_consent}):
 * the scopes a user approved for a client. A consent is deleted with its
 * identity (ON DELETE CASCADE), with its client and when the user revokes it.
 *
 * <p>
 * Scopes are stored space separated, sorted, without duplicates.
 */
@Repository
public class OidcConsentRepository extends AbstractRepository {

  private static final String SQL_FIND = "SELECT * FROM oidc_consent WHERE identity_id = :identity_id AND client_id = :client_id";

  /** The consent of the identity for the client, or null. */
  public OidcConsent find(Integer identityId, String clientId) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    params.put("client_id", clientId);
    var rows = jdbcTemplate.query(SQL_FIND, params, (rs, rowNum) -> map(rs, false));
    return rows.isEmpty() ? null : rows.get(0);
  }

  // the stored scopes and the new ones are merged in SQL: two approvals at the same time lose nothing
  private static final String SQL_UPSERT = "INSERT INTO oidc_consent (identity_id, client_id, scope, created_at, updated_at, last_used_at) "
      + "VALUES (:identity_id, :client_id, :scope, :now, :now, :now) "
      + "ON CONFLICT (identity_id, client_id) DO UPDATE SET "
      + "scope = (SELECT string_agg(DISTINCT s, ' ' ORDER BY s) "
      + "FROM unnest(string_to_array(oidc_consent.scope || ' ' || EXCLUDED.scope, ' ')) AS s WHERE s <> ''), "
      + "updated_at = EXCLUDED.updated_at, last_used_at = EXCLUDED.last_used_at";

  /**
   * Records an approval: creates the consent, or adds the scopes to the ones
   * already approved. The consent counts as used at once.
   */
  public void upsert(Integer identityId, String clientId, Collection<String> scopes, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    params.put("client_id", clientId);
    params.put("scope", joinScopes(scopes));
    params.put("now", Timestamp.from(now));
    jdbcTemplate.update(SQL_UPSERT, params);
  }

  private static final String SQL_TOUCH = "UPDATE oidc_consent SET last_used_at = :now WHERE identity_id = :identity_id AND client_id = :client_id";

  /** Records that an authorization code was issued with this consent. */
  public void touch(Integer identityId, String clientId, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    params.put("client_id", clientId);
    params.put("now", Timestamp.from(now));
    jdbcTemplate.update(SQL_TOUCH, params);
  }

  private static final String SQL_DELETE = "DELETE FROM oidc_consent WHERE identity_id = :identity_id AND client_id = :client_id";

  /** @return true if there was a consent to delete */
  public boolean delete(Integer identityId, String clientId) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    params.put("client_id", clientId);
    return jdbcTemplate.update(SQL_DELETE, params) > 0;
  }

  private static final String SQL_DELETE_BY_CLIENT = "DELETE FROM oidc_consent WHERE client_id = :client_id";

  /** Deletes the consents of every user for a client (client deleted or its redirect URIs changed). */
  public int deleteByClient(String clientId) {
    var params = new HashMap<String, Object>();
    params.put("client_id", clientId);
    return jdbcTemplate.update(SQL_DELETE_BY_CLIENT, params);
  }

  private static final String SQL_LIST_BY_IDENTITY = "SELECT * FROM oidc_consent WHERE identity_id = :identity_id "
      + "ORDER BY COALESCE(last_used_at, updated_at) DESC, client_id";

  private static final String SQL_DELETE_BY_IDENTITY = "DELETE FROM oidc_consent WHERE identity_id = :identity_id";

  /** Deletes every consent of an identity (credentials changed, account disabled). */
  public int deleteByIdentity(Integer identityId) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    return jdbcTemplate.update(SQL_DELETE_BY_IDENTITY, params);
  }

  public List<OidcConsent> listByIdentity(Integer identityId) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    return jdbcTemplate.query(SQL_LIST_BY_IDENTITY, params, (rs, rowNum) -> map(rs, false));
  }

  private static final String SQL_LIST_BY_CLIENT = "SELECT c.*, r.label AS identity_label FROM oidc_consent c "
      + "LEFT JOIN resource r ON r.id = c.identity_id WHERE c.client_id = :client_id ORDER BY r.label, c.identity_id";

  /** The consents given to a client, with the label of each identity. */
  public List<OidcConsent> listByClient(String clientId) {
    var params = new HashMap<String, Object>();
    params.put("client_id", clientId);
    return jdbcTemplate.query(SQL_LIST_BY_CLIENT, params, (rs, rowNum) -> map(rs, true));
  }

  // ------------------------------------------------------- grants of a user

  // by identity_id: the SSO session of a refresh token may have ended (session_id null) while the token lives on;
  // a token stored before identity_id existed is still found through its session
  private static final String SQL_REVOKE_REFRESH_TOKENS = "UPDATE oidc_refresh_token SET revoked_at = :now "
      + "WHERE client_id = :client_id AND revoked_at IS NULL "
      + "AND (identity_id = :identity_id OR session_id IN (SELECT id FROM oidc_session WHERE identity_id = :identity_id))";
  private static final String SQL_DELETE_UNUSED_CODES = "DELETE FROM oidc_authorization_code "
      + "WHERE client_id = :client_id AND consumed_at IS NULL "
      + "AND session_id IN (SELECT id FROM oidc_session WHERE identity_id = :identity_id)";

  /**
   * Revokes what a client holds for one user: the refresh tokens issued to the
   * user (whether their SSO session is still alive or not), and the
   * authorization codes not exchanged yet (codes always belong to a session). Used codes are
   * kept so that a replay is still detected. Access tokens are JWT: they stay
   * valid until they expire.
   *
   * @return the number of refresh tokens revoked
   */
  public int revokeGrants(Integer identityId, String clientId, Instant now) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    params.put("client_id", clientId);
    params.put("now", Timestamp.from(now));
    var revoked = jdbcTemplate.update(SQL_REVOKE_REFRESH_TOKENS, params);
    jdbcTemplate.update(SQL_DELETE_UNUSED_CODES, params);
    return revoked;
  }

  // -------------------------------------------------------------- helpers

  /** Space separated, sorted, without duplicates or blanks. */
  public static String joinScopes(Collection<String> scopes) {
    return String.join(" ", normalize(scopes));
  }

  public static Set<String> splitScopes(String value) {
    if (value == null || value.isBlank()) {
      return new TreeSet<>();
    }
    return normalize(Arrays.asList(value.trim().split("\\s+")));
  }

  private static Set<String> normalize(Collection<String> scopes) {
    if (scopes == null) {
      return new TreeSet<>();
    }
    return scopes.stream()
        .filter(scope -> scope != null && !scope.isBlank())
        .map(String::trim)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static OidcConsent map(ResultSet rs, boolean withLabel) throws SQLException {
    return OidcConsent.builder()
        .identityId(rs.getInt("identity_id"))
        .clientId(rs.getString("client_id"))
        .scopes(splitScopes(rs.getString("scope")))
        .createdAt(instant(rs, "created_at"))
        .updatedAt(instant(rs, "updated_at"))
        .lastUsedAt(instant(rs, "last_used_at"))
        .identityLabel(withLabel ? rs.getString("identity_label") : null)
        .build();
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    var timestamp = rs.getTimestamp(column);
    return timestamp != null ? timestamp.toInstant() : null;
  }
}
