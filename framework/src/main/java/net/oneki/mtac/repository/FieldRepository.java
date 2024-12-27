package net.oneki.mtac.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import net.oneki.mtac.repository.framework.AbstractRepository;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.model.entity.Field;
import net.oneki.mtac.util.json.JsonUtil;
import net.oneki.mtac.util.sql.SqlUtils;

@Repository
@RequiredArgsConstructor
public class FieldRepository extends AbstractRepository {

    public List<Field> listAllFieldsUnsecure() {
        var sql = SqlUtils.getSQL("field/field_list_all_unsecure.sql");
        var fields = jdbcTemplate.query(sql, new HashMap<String, Object>(), new RowMapper<Field>() {

            @Override
            public Field mapRow(ResultSet rs, int rowNum) throws SQLException {
                return Field.builder()
                        .id(rs.getInt("id") == 0 ? null : rs.getInt("id"))
                        .label(rs.getString("label"))
                        .type(rs.getString("type"))
                        .peerId(rs.getInt("peer_id") == 0 ? null : rs.getInt("peer_id"))
                        .multiple(rs.getBoolean("multiple"))
                        .required(rs.getBoolean("required"))
                        .validators(JsonUtil.json2Object(rs.getString("validators"), Object.class))
                        .defaultValue(JsonUtil.json2Object(rs.getString("default_value"), Object.class))
                        .example(JsonUtil.json2Object(rs.getString("example"), Object.class))
                        .priv(rs.getBoolean("priv"))
                        .description(rs.getString("description"))
                        .editable(rs.getBoolean("editable"))
                        .schemas(JsonUtil.json2List(rs.getString("schemas"), String.class))
                        .build();
            }

        });

        return fields;
    }

    public Field create(Field fieldEntity) {
        Map<String, Object> parameters = SqlUtils.getPropertySqlParameters(fieldEntity);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        var sql = SqlUtils.getSQL("field/field_insert.sql");
        var parameterSource = new MapSqlParameterSource(parameters);
        jdbcTemplate.update(sql, parameterSource, keyHolder);
        if (keyHolder.getKeyList() != null && keyHolder.getKeyList().size() > 0) {
            fieldEntity.setId((Integer) keyHolder.getKeyList().get(0).get("id"));
            return fieldEntity;
        }
        return null;
    }

    public void update(Field fieldEntity) {
        Map<String, Object> parameters = SqlUtils.getPropertySqlParameters(fieldEntity);

        SqlParameterSource parameterSource = new MapSqlParameterSource(parameters);
        var sql = SqlUtils.getSQL("field/field_update.sql");
        jdbcTemplate.update(sql, parameterSource);
    }

    public void updatePeer(Field fieldEntity) {
        Map<String, Object> parameters = SqlUtils.getPropertySqlParameters(fieldEntity);

        SqlParameterSource parameterSource = new MapSqlParameterSource(parameters);
        var sql = SqlUtils.getSQL("field/field_update_peer.sql");
        jdbcTemplate.update(sql, parameterSource);
    }

    public void delete(Integer id) {
        Map<String, Object> parameters = Map.of("id", id);
        SqlParameterSource parameterSource = new MapSqlParameterSource(parameters);
        var sql = "DELETE FROM field WHERE id = :id";
        jdbcTemplate.update(sql, parameterSource);
    }

    public void deleteOrphans() {
        SqlParameterSource parameterSource = new MapSqlParameterSource();
        var sql = SqlUtils.getSQL("field/field_delete_orphans.sql");
        jdbcTemplate.update(sql, parameterSource);
    }

}
