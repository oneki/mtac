package net.oneki.mtac.core.util.cache;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.cache.PgNotificationConsumer;
import net.oneki.mtac.framework.cache.PgNotifierService;

@Configuration
@Slf4j
public class PgListenerConfiguration {

    @Bean
    CommandLineRunner startListener(PgNotifierService notifier, PgNotificationConsumer handler) {
        return (args) -> {
            log.info("Starting postgres listener thread...");
            Runnable listener = notifier.createNotificationHandler(handler);
            Thread t = new Thread(listener, "postgres-listener");
            t.start();
        };
    }
}
