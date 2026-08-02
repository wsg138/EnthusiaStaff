package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.migration.FencedModerationStore;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AuthoritativeWriteFenceSmallPoolIntegrationTest {
    private static final CaseId CASE_ID = new CaseId("01HZX3K8M2N4P6QR");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_small_pool_fence_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void concurrentWritesCompleteWithMinimumSupportedPool() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig());
             HikariDataSource dataSource = dataSource();
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            setMode(OperationalMode.ACTIVE);
            assertEquals(OperationalMode.ACTIVE, runtime.operationalStateStore().current().mode());
            AtomicInteger calls = new AtomicInteger();
            ModerationStore store = new FencedModerationStore(dataSource, new ModerationStore() {
                @Override
                public List<PriorOffense> relatedHistory(UUID targetId, String family) {
                    return List.of();
                }

                @Override
                public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
                    calls.incrementAndGet();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("fenced delegate was interrupted", exception);
                    }
                    return new PunishmentResult.Accepted(CASE_ID, false);
                }
            });

            Future<PunishmentResult.Accepted> first = executor.submit(
                    () -> store.createPunishment(null)
            );
            Future<PunishmentResult.Accepted> second = executor.submit(
                    () -> store.createPunishment(null)
            );

            assertEquals(CASE_ID, first.get(5, TimeUnit.SECONDS).caseId());
            assertEquals(CASE_ID, second.get(5, TimeUnit.SECONDS).caseId());
            assertEquals(2, calls.get());
        }
    }

    private static void setMode(OperationalMode mode) throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE operational_state
                     SET mode = ?, revision = revision + 1,
                         reason = 'Minimum-pool fence integration test',
                         updated_at = CURRENT_TIMESTAMP(6)
                     WHERE singleton_id = 1
                     """)) {
            statement.setString(1, mode.name());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl());
        config.setUsername(DATABASE.getUsername());
        config.setPassword(DATABASE.getPassword());
        config.setMaximumPoolSize(2);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }

    private static DatabaseConfig databaseConfig() {
        return new DatabaseConfig(
                jdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword(),
                2,
                1_000
        );
    }

    private static Connection sourceConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                jdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword()
        );
    }

    private static String jdbcUrl() {
        return DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:");
    }
}
