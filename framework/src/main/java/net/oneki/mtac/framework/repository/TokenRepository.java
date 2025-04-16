package net.oneki.mtac.framework.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Repository;

import net.oneki.mtac.model.core.util.json.JsonUtil;
import net.oneki.mtac.model.core.util.security.Claims;

@Repository
public class TokenRepository extends AbstractRepository {

  private static final String SQL_DELETE_TOKEN = "DELETE FROM token WHERE jti = :jti";

  public void deleteToken(String jti) {
    var params = new HashMap<String, Object>();
    params.put("jti", jti);
    jdbcTemplate.update(SQL_DELETE_TOKEN, params);
  }

  // get token
  public static final String SQL_GET_TOKEN = "SELECT * FROM token WHERE jti = :jti";

  public Claims getToken(String jti) {
    var params = new HashMap<String, Object>();
    params.put("jti", jti);
    return jdbcTemplate.queryForObject(SQL_GET_TOKEN, params, (rs, rowNum) -> {
      try {
        return JsonUtil.json2Object(rs.getString("token"), Claims.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  // insert token
  public static final String SQL_INSERT_TOKEN = "INSERT INTO token (jti, sub, token, expire_at) VALUES (:jti, :sub, to_json(:token::JSON), :expire_at)";

  public void insertToken(String jti, String sub, Object token, Instant expireAt) {
    var params = new HashMap<String, Object>();
    params.put("jti", UUID.fromString(jti));
    params.put("sub", sub);
    params.put("token", JsonUtil.object2Json(token));
    params.put("expire_at", Timestamp.from(expireAt));

    jdbcTemplate.update(SQL_INSERT_TOKEN, params);
  }

  public Date now() {
    final DateTime now = DateTime.now(DateTimeZone.UTC);
    return now.toDate();
  }
}
