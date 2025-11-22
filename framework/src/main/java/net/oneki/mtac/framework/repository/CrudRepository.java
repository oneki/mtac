package net.oneki.mtac.framework.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import net.oneki.mtac.framework.json.EntityMapper;
import net.oneki.mtac.framework.util.sql.SqlUtils;
import net.oneki.mtac.model.core.resource.HasId;

public abstract class CrudRepository<T extends HasId> extends AbstractRepository { 
  protected EntityMapper entityMapper;
  public T create(T entity, String sqlResourcePath) {
    return create(entity, sqlResourcePath, null);
  }

  public T create(T entity, String sqlResourcePath, Function<Map<String,Object>, Map<String,Object>> parameterSourceFunction) {
    var parameters = SqlUtils.getPropertySqlParameters(entity);
    var sql = SqlUtils.getSQL(sqlResourcePath);
    var keyHolder = new GeneratedKeyHolder();
    if (parameterSourceFunction != null) {
      parameters = parameterSourceFunction.apply(parameters);
    }
    var parameterSource = new MapSqlParameterSource(parameters);
    jdbcTemplate.update(sql, parameterSource, keyHolder);
    if (keyHolder.getKeyList() != null && keyHolder.getKeyList().size() > 0) {
      entity.setId((Integer) keyHolder.getKeyList().get(0).get("id"));
      return entity;
    }
    return null;
  }

  public T update(T entity, String sqlResourcePath) {
    return update(entity, sqlResourcePath, null);
  }

  public T update(T entity, String sqlResourcePath, Function<Map<String,Object>, Map<String,Object>> parameterSourceFunction) {
    var parameters = SqlUtils.getPropertySqlParameters(entity);
    var sql = SqlUtils.getSQL(sqlResourcePath);
    var parameterSource = new MapSqlParameterSource(parameters);
    jdbcTemplate.update(sql, parameterSource);
    
    return entity;
  }

  public void delete(Integer id, String sqlResourcePath) {
    delete(Set.of(id), sqlResourcePath);
  }

  public void delete(Collection<Integer> ids, String sqlResourcePath) {
    var sql = SqlUtils.getSQL(sqlResourcePath);
    Map<String, Object> args = new HashMap<>();
    args.put("ids", ids);
    jdbcTemplate.update(sql, args);
  }

  @Autowired
  public void setEntityMapper(EntityMapper entityMapper) {
    this.entityMapper = entityMapper;
  }
}
