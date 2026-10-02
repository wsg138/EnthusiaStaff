package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.migration.MigrationMode;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LiteBansHistoricalActionsIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("historical_migration_test")
            .withUsername("migration_test")
            .withPassword("migration_test_password");

    @Test
    void preservesWarningRemovalAndCompletedKickWithoutReplayingExternalActions() throws Exception {
        DatabaseConfig config = new DatabaseConfig(DATABASE.getJdbcUrl(), DATABASE.getUsername(),
                DATABASE.getPassword(), 4, 5_000);
        try (var runtime = MariaDb.initialize(config)) {
            prepareSource();
            var state = runtime.operationalStateStore().current();
            assertTrue(runtime.operationalStateStore().transition(state.revision(),
                    OperationalMode.SHADOW_MIGRATION, null, "Historical migration test", Instant.now()));
            var service = runtime.liteBansMigrationService();
            var first = service.execute(config, "historical_", 100, MigrationMode.SHADOW);
            assertEquals(2, first.importedRecords());
            assertTrue(first.rejectedRows().isEmpty());
            assertEquals(0, first.shadowSummary().orElseThrow().mismatchCount());
            var replay = service.execute(config, "historical_", 100, MigrationMode.SHADOW);
            assertEquals(0, replay.importedRecords());
            assertEquals(2, replay.replayedRecords());
            assertEquals(0, replay.shadowSummary().orElseThrow().mismatchCount());
            verifyStoredHistory();
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
    }

    private static void prepareSource() throws Exception {
        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            for (String table : new String[]{"bans", "mutes", "warnings", "kicks"}) {
                sql.execute("CREATE TABLE historical_" + table + """
                        (id BIGINT PRIMARY KEY, uuid VARCHAR(36), ip VARCHAR(45), reason VARCHAR(255),
                         banned_by_name VARCHAR(64), time BIGINT, until BIGINT, active BOOLEAN,
                         removed_by_date TIMESTAMP(6) NULL)
                        """);
            }
            sql.execute("CREATE TABLE historical_history (id BIGINT PRIMARY KEY, uuid VARCHAR(36), "
                    + "name VARCHAR(32), ip VARCHAR(45), date TIMESTAMP(6))");
            long issued = Instant.parse("2026-07-01T00:00:00Z").toEpochMilli();
            long expiration = Instant.parse("2030-08-01T00:00:00Z").toEpochMilli();
            sql.execute("INSERT INTO historical_warnings VALUES (1, '00000000-0000-0000-0000-000000000101', "
                    + "NULL, 'Original warning', 'Original staff', " + issued + ", " + expiration
                    + ", FALSE, '2026-07-02 00:00:00')");
            sql.execute("INSERT INTO historical_kicks VALUES (1, '00000000-0000-0000-0000-000000000101', "
                    + "NULL, 'Original kick', 'Original staff', " + issued + ", 0, TRUE, NULL)");
        }
    }

    private static void verifyStoredHistory() throws Exception {
        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("""
                    SELECT s.sanction_type, s.status, s.ended_at, c.public_reason, c.actor_name
                    FROM sanctions s JOIN cases c ON c.case_id=s.case_id ORDER BY s.sanction_type
                    """)) {
                assertTrue(rows.next());
                assertEquals("KICK", rows.getString(1));
                assertEquals("APPLIED", rows.getString(2));
                assertEquals(Instant.parse("2026-07-01T00:00:00Z"), rows.getTimestamp(3).toInstant());
                assertEquals("Original kick", rows.getString(4));
                assertEquals("Original staff", rows.getString(5));
                assertTrue(rows.next());
                assertEquals("WARNING", rows.getString(1));
                assertEquals("ENDED_EARLY", rows.getString(2));
                assertEquals(Instant.parse("2026-07-02T00:00:00Z"), rows.getTimestamp(3).toInstant());
                assertEquals("Original warning", rows.getString(4));
                assertEquals("Original staff", rows.getString(5));
            }
            for (String table : new String[]{"network_outbox", "discord_outbox"}) {
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getLong(1));
                }
            }
        }
    }
}
