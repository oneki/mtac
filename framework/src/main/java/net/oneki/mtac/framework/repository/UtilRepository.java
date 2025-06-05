package net.oneki.mtac.framework.repository;

import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UtilRepository extends AbstractRepository{
  public Integer getNextSequence(String sequenceName) {
    String sql = "SELECT nextval('" + sequenceName + "')";
    return (Integer) jdbcTemplate.queryForObject(sql, new EmptySqlParameterSource(), Integer.class);
  }
}
