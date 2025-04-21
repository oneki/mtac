package net.oneki.mtac.framework.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Repository;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.util.sql.SqlUtils;

@Repository
@Slf4j
public class InitRepository extends AbstractRepository {
    @Value("${mtac.postgres.owner:postgres}")
    String owner;

    @Value("${mtac.postgres.seeding:}")
    String seedingPath;

    public boolean initSchema() {
        var schema = getSchemaName();
        String sql = "select count(*) from pg_class c join pg_namespace s on s.oid = c.relnamespace where s.nspname not in ('pg_catalog', 'information_schema') and s.nspname = '"
                + schema + "'";
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long count = jdbcTemplate.queryForObject(sql, new SingleColumnRowMapper<Long>());

        if (count == 0) {
            try {
                // Create tables / functions / triggers
                log.info("Initializing database");
                sql = SqlUtils.getSQL("framework/init.sql");
                sql = replace(sql, "\\$\\$", "'", "''").replaceAll("public\\.", schema + ".")
                        .replaceAll("OWNER TO postgres", "OWNER TO " + owner);
                ScriptUtils.executeSqlScript(dataSource.getConnection(), new ByteArrayResource(sql.getBytes()));

                // Seed tables
                sql = SqlUtils.getSQL("framework/seeding.sql");
                ScriptUtils.executeSqlScript(dataSource.getConnection(), new ByteArrayResource(sql.getBytes()));

                if (seedingPath != null && seedingPath.length() > 0) {
                    sql = SqlUtils.getSQL(seedingPath);
                    ScriptUtils.executeSqlScript(dataSource.getConnection(), new ByteArrayResource(sql.getBytes()));
                }
                log.info("Database initialized");
            } catch (Exception e) {
                throw new RuntimeException(e);
            } 
            return true;
        }

        return false;
    }

    private String getSchemaName() {
        // MultiValueMap<String, String> defaultDatasourceParams = UriComponentsBuilder
        //         .fromUriString(((HikariDataSource) dataSource).getJdbcUrl().substring(5)).build().getQueryParams();
        // List<String> currentDefaultSchemas = defaultDatasourceParams.get("currentSchema");
        // if (currentDefaultSchemas.size() != 1) {
        //     return "public";
        // }
        // return currentDefaultSchemas.get(0);

        return "public"; // TODO handle correctly schema
    }

    private String replace(String string, String delimiter, String toBeReplaced, String toReplace) {
        String[] tokens = string.split(delimiter);
        String result = "";
        for (int i = 0; i < tokens.length; i++) {
            if (i % 2 == 1) {
                result += "'" + tokens[i].replaceAll("'", "''") + "'";
            } else {
                result += tokens[i];
            }
        }
        return result;
    }
}
