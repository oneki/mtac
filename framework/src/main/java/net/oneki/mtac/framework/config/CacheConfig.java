package net.oneki.mtac.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.oneki.mtac.framework.cache.PgNotificationConsumer;

@Configuration
public class CacheConfig {
    @Bean
    public PgNotificationConsumer PgNotificationConsumer() {
        return new PgNotificationConsumer();
    }

}
