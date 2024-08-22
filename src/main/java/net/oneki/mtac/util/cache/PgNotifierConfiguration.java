package net.oneki.mtac.util.cache;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import net.oneki.mtac.service.cache.PgNotifierService;


@Configuration
public class PgNotifierConfiguration {

    @Bean
    PgNotifierService notifier(DataSource dataSource) {
        JdbcTemplate tpl = new JdbcTemplate(dataSource);
        return new PgNotifierService(tpl);
    }
}
