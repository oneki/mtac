package net.oneki.mtac.framework.cache;

import java.sql.Connection;
import java.util.function.Consumer;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.model.resource.Resource;

@Slf4j
@RequiredArgsConstructor
public class PgNotifierService {
    public static final String RESOURCE_CHANNEL = "resource_channel";
    private final JdbcTemplate tpl;

    @Transactional
    public void notifyCreateResource(Resource resource) {
        tpl.execute("NOTIFY " + RESOURCE_CHANNEL + ", 'create," + resource.getId() + "," + resource.getTenantId() + ","
                + resource.getSchemaId() + "," + resource.getLabel() + "'");
    }

    @Transactional
    public void notifyUpdateResource(Resource resource) {
        tpl.execute("NOTIFY " + RESOURCE_CHANNEL + ", 'update," + resource.getId() + "," + resource.getTenantId() + ","
                + resource.getSchemaId() + "," + resource.getLabel() + "'");
    }

    @Transactional
    public void notifyDeleteResource(Integer resourceId) {
        tpl.execute("NOTIFY " + RESOURCE_CHANNEL + ", 'delete," + resourceId + "'");
    }

    public Runnable createNotificationHandler(Consumer<PGNotification> consumer) {

        return () -> {
            try {
                tpl.execute((Connection c) -> {
                    log.info("notificationHandler: sending LISTEN RESOURCE_CHANNEL command...");
                    c.createStatement().execute("LISTEN " + RESOURCE_CHANNEL);

                    PGConnection pgconn = c.unwrap(PGConnection.class);

                    while (!Thread.currentThread().isInterrupted()) {
                        PGNotification[] nts = pgconn.getNotifications(2000);
                        if (nts == null || nts.length == 0) {
                            continue;
                        }

                        for (PGNotification nt : nts) {
                            consumer.accept(nt);
                        }
                    }

                    return 0;
                });
            } catch (DataAccessResourceFailureException e) {
            }
        };
    }
}
