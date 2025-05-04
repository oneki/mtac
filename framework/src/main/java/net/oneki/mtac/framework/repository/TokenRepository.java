package net.oneki.mtac.framework.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import net.oneki.mtac.model.core.util.json.JsonUtil;
import net.oneki.mtac.model.core.util.security.Claims;
import net.oneki.mtac.model.resource.Resource;

@Repository
public class TokenRepository extends AbstractRepository {

  private static final String SQL_DELETE_TOKEN = "DELETE FROM token WHERE identity_id = :identity_id";

  public void deleteToken(String sub) {
    deleteToken(Resource.fromUid(sub));
  }

  public void deleteToken(Integer identityId) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    jdbcTemplate.update(SQL_DELETE_TOKEN, params);
  }

  // get token
  public static final String SQL_GET_TOKEN = "SELECT * FROM token WHERE identity_id = :identity_id";

  public Claims getToken(String sub) {
    return getToken(Resource.fromUid(sub));
  }

  public Claims getToken(Integer identityId) {
    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
    try {
      return jdbcTemplate.queryForObject(SQL_GET_TOKEN, params, (rs, rowNum) -> {
        try {
          return JsonUtil.json2Object(rs.getString("token"), Claims.class);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    } catch (EmptyResultDataAccessException emptyException) {
      return null;
    }

  }

  // insert token
  public static final String SQL_INSERT_TOKEN = "INSERT INTO token (identity_id, sub, token, expire_at) VALUES (:identity_id, :sub, to_json(:token::JSON), :expire_at)";

  public void upsertToken(Integer identityId, String sub, Object token, Instant expireAt) {
    var currentToken = getToken(identityId);
    if (currentToken != null) {
      deleteToken(identityId);
    }

    var params = new HashMap<String, Object>();
    params.put("identity_id", identityId);
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
