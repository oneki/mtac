package net.oneki.mtac.framework.repository;

import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class TotpRepository extends AbstractRepository {

  public static final String SQL_IS_REVOKED_TOTP = "SELECT COUNT(*) FROM totp WHERE user_id = :userId AND totp = :totp AND interval >= :interval";

  public boolean isTotpRevoked(int userId, String totp, Long currentInterval) {
    Map<String, Object> args = Map.of("userId", userId, "totp", totp, "interval", currentInterval - 2);
    Integer count = jdbcTemplate.queryForObject(SQL_IS_REVOKED_TOTP, args, Integer.class);
    if (count > 0)
      return true;
    return false;
  }

  public static final String SQL_REVOKE_TOTP = "INSERT INTO totp (user_id,totp,\"interval\") VALUES(:userId,:totp,:interval)";

  public void revokeTotp(int userId, String totp, Long currentInterval) {
    Map<String, Object> params = Map.of("userId", userId, "totp", totp, "interval", currentInterval);
    SqlParameterSource sqlParameterSource = new MapSqlParameterSource(params);
    try {
      jdbcTemplate.update(SQL_REVOKE_TOTP, sqlParameterSource);
    } catch (DuplicateKeyException e) {
    }
  }

  public static final String SQL_DELETE_TOTP = "DELETE FROM totp WHERE \"interval\" < :interval";

  public void deleteExpiredTotp(Long interval) {
    Map<String, Object> params = Map.of("interval", interval);
    SqlParameterSource args = new MapSqlParameterSource(params);
    jdbcTemplate.update(SQL_DELETE_TOTP, args);
  }

}
