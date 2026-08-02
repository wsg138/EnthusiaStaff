package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.migration.MigrationMode;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.UuidBytes;
import net.enthusia.staff.persistence.migration.CutoverOutcome;
import net.enthusia.staff.persistence.migration.MigrationExecutionReport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LiteBansCutoverRestartRecoveryIntegrationTest {
    private static final String LEGACY_PREFIX = "legacy_";
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000351");
    private static final List<String> COMPARISON_TYPES = List.of(
            "COUNTS",
            "CHECKSUMS",
            "ACTIVE_SANCTIONS",
            "UUID_MAPPINGS",
            "EXPIRATIONS",
            "LOGIN_DECISIONS",
            "MUTE_DECISIONS",
            "IP_BAN_DECISIONS"
    );

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void restartFailsAbandonedRunClosedBeforeStartingReplacement() throws SQLException {
        UUID abandonedRunId;
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            resetMigrationState();
            prepareEmptyLegacyTables();
            enterShadowMode(runtime, Instant.now().minus(Duration.ofDays(2)));
            abandonedRunId = insertRunningRun("SHADOW", Instant.now().minus(Duration.ofMinutes(15)));
        }

        MigrationExecutionReport replacement;
        try (MariaDbRuntime restarted = MariaDb.initialize(databaseConfig())) {
            replacement = restarted.liteBansMigrationService().execute(
                    databaseConfig(),
                    LEGACY_PREFIX,
                    100,
                    MigrationMode.SHADOW
            );
        }

        assertNotEquals(abandonedRunId, replacement.runId());
        assertEquals("FAILED", migrationRunState(abandonedRunId));
        assertNotNull(migrationRunCompletedAt(abandonedRunId));
        assertEquals(1, migrationRunMismatchCount(abandonedRunId));
        assertEquals(
                "{\"reason\":\"ABANDONED_AFTER_PROCESS_FAILURE\"}",
                migrationRunReport(abandonedRunId)
        );
        assertEquals("COMPLETED", migrationRunState(replacement.runId()));
    }

    @Test
    void committedActivationCanBeRetriedAfterRestartWithoutDuplicateCutover() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            resetMigrationState();
            enterShadowMode(runtime, Instant.now().minus(Duration.ofDays(8)));
            assertTrue(runtime.cutoverCoordinator().enterMaintenance(ACTOR_ID, "Restart recovery rehearsal"));
            Instant maintenanceStarted = runtime.operationalStateStore().current().updatedAt();
            insertCompleteShadowWindow(maintenanceStarted);
            UUID finalRunId = insertCompletedRun("CUTOVER", maintenanceStarted, maintenanceStarted);
            insertMatchingComparisons(finalRunId, maintenanceStarted);

            CutoverOutcome committed = runtime.cutoverCoordinator().activate(ACTOR_ID, Optional.empty());
            assertTrue(committed.activated());
        }

        try (MariaDbRuntime restarted = MariaDb.initialize(databaseConfig())) {
            assertEquals(OperationalMode.ACTIVE, restarted.operationalStateStore().current().mode());
            CutoverOutcome replay = restarted.cutoverCoordinator().activate(ACTOR_ID, Optional.empty());
            assertFalse(replay.activated());
            assertEquals(List.of("MAINTENANCE_REQUIRED"), replay.assessment().blockers());
            assertEquals(1, cutoverRecordCount());
            assertEquals(1, auditCount("LITEBANS_CUTOVER_ACTIVATED"));
            assertTrue(restarted.cutoverCoordinator().freezeActiveAuthority(
                    ACTOR_ID,
                    "Post-cutover restart rehearsal freeze"
            ));
        }

        try (MariaDbRuntime restartedAgain = MariaDb.initialize(databaseConfig())) {
            assertEquals(
                    OperationalMode.READ_ONLY_FAILURE,
                    restartedAgain.operationalStateStore().current().mode()
            );
            assertFalse(restartedAgain.cutoverCoordinator().freezeActiveAuthority(
                    ACTOR_ID,
                    "Duplicate freeze after restart"
            ));
            assertEquals(1, cutoverRecordCount());
            assertEquals(1, auditCount("LITEBANS_CUTOVER_EMERGENCY_FREEZE"));
        }
    }

    private static void prepareEmptyLegacyTables() throws SQLException {
        try (Connection connection = sourceConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS legacy_history");
            statement.execute("DROP TABLE IF EXISTS legacy_mutes");
            statement.execute("DROP TABLE IF EXISTS legacy_bans");
            statement.execute("""
                    CREATE TABLE legacy_bans (
                        id BIGINT NOT NULL PRIMARY KEY,
                        uuid VARCHAR(36) NULL,
                        name VARCHAR(32) NULL,
                        ip VARCHAR(64) NULL,
                        reason VARCHAR(255) NOT NULL,
                        banned_by_name VARCHAR(64) NOT NULL,
                        removed_by_date TIMESTAMP(6) NULL,
                        time BIGINT NOT NULL,
                        until BIGINT NOT NULL,
                        active BOOLEAN NOT NULL,
                        ipban BOOLEAN NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE legacy_mutes (
                        id BIGINT NOT NULL PRIMARY KEY,
                        uuid VARCHAR(36) NULL,
                        name VARCHAR(32) NULL,
                        reason VARCHAR(255) NOT NULL,
                        muted_by_name VARCHAR(64) NOT NULL,
                        removed_by_date TIMESTAMP(6) NULL,
                        time BIGINT NOT NULL,
                        until BIGINT NOT NULL,
                        active BOOLEAN NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE legacy_history (
                        id BIGINT NOT NULL PRIMARY KEY,
                        date TIMESTAMP(6) NOT NULL,
                        name VARCHAR(32) NULL,
                        uuid VARCHAR(36) NOT NULL,
                        ip VARCHAR(64) NOT NULL
                    )
                    """);
        }
    }

    private static void enterShadowMode(MariaDbRuntime runtime, Instant changedAt) {
        var initial = runtime.operationalStateStore().current();
        assertTrue(runtime.operationalStateStore().transition(
                initial.revision(),
                OperationalMode.SHADOW_MIGRATION,
                ACTOR_ID,
                "Integration-test shadow authority",
                changedAt
        ));
    }

    private static void insertCompleteShadowWindow(Instant shadowEndedAt) throws SQLException {
        Instant windowStartedAt = shadowEndedAt.minus(Duration.ofHours(168));
        for (int day = 0; day < 7; day++) {
            Instant startedAt = windowStartedAt.plus(Duration.ofHours(24L * day));
            Instant completedAt = startedAt.plus(Duration.ofHours(24));
            UUID runId = insertCompletedRun("SHADOW", startedAt, completedAt);
            insertMatchingComparisons(runId, completedAt);
        }
    }

    private static UUID insertCompletedRun(String mode, Instant startedAt, Instant completedAt)
            throws SQLException {
        UUID runId = UUID.randomUUID();
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO migration_runs(
                         run_id, mode, state, source_schema_name, started_at, completed_at,
                         source_high_watermark, counts_json, checksums_json, mismatch_count, report_json
                     ) VALUES (?, ?, 'COMPLETED', 'legacy_', ?, ?, NULL, '{}', '{}', 0, '{}')
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(runId));
            statement.setString(2, mode);
            statement.setTimestamp(3, Timestamp.from(startedAt));
            statement.setTimestamp(4, Timestamp.from(completedAt));
            assertEquals(1, statement.executeUpdate());
        }
        return runId;
    }

    private static UUID insertRunningRun(String mode, Instant startedAt) throws SQLException {
        UUID runId = UUID.randomUUID();
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO migration_runs(
                         run_id, mode, state, source_schema_name, started_at, completed_at,
                         source_high_watermark, counts_json, checksums_json, mismatch_count, report_json
                     ) VALUES (?, ?, 'RUNNING', 'legacy_', ?, NULL, NULL, '{}', '{}', 0, '{}')
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(runId));
            statement.setString(2, mode);
            statement.setTimestamp(3, Timestamp.from(startedAt));
            assertEquals(1, statement.executeUpdate());
        }
        return runId;
    }

    private static void insertMatchingComparisons(UUID runId, Instant comparedAt) throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO shadow_comparisons(
                         comparison_id, run_id, comparison_type, legacy_decision,
                         expected_decision, matched, detail_json, compared_at
                     ) VALUES (?, ?, ?, 'LITEBANS', 'ENTHUSIASTAFF', TRUE,
                         '{"compared":1,"mismatched":0}', ?)
                     """)) {
            for (String type : COMPARISON_TYPES) {
                statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
                statement.setBytes(2, UuidBytes.toBytes(runId));
                statement.setString(3, type);
                statement.setTimestamp(4, Timestamp.from(comparedAt));
                statement.addBatch();
            }
            int[] results = statement.executeBatch();
            assertEquals(COMPARISON_TYPES.size(), results.length);
        }
    }

    private static String migrationRunState(UUID runId) throws SQLException {
        return migrationRunString(runId, "state");
    }

    private static String migrationRunReport(UUID runId) throws SQLException {
        return migrationRunString(runId, "report_json");
    }

    private static String migrationRunString(UUID runId, String column) throws SQLException {
        String query = "SELECT " + column + " FROM migration_runs WHERE run_id = ?";
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setBytes(1, UuidBytes.toBytes(runId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AssertionError("Migration run was not found");
                }
                return result.getString(1);
            }
        }
    }

    private static Timestamp migrationRunCompletedAt(UUID runId) throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT completed_at FROM migration_runs WHERE run_id = ?"
             )) {
            statement.setBytes(1, UuidBytes.toBytes(runId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AssertionError("Migration run was not found");
                }
                return result.getTimestamp(1);
            }
        }
    }

    private static long migrationRunMismatchCount(UUID runId) throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT mismatch_count FROM migration_runs WHERE run_id = ?"
             )) {
            statement.setBytes(1, UuidBytes.toBytes(runId));
            return count(statement);
        }
    }

    private static long cutoverRecordCount() throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM cutover_records"
             )) {
            return count(statement);
        }
    }

    private static long auditCount(String eventType) throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM audit_events WHERE event_type = ?"
             )) {
            statement.setString(1, eventType);
            return count(statement);
        }
    }

    private static long count(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new AssertionError("Count query did not return a row");
            }
            return result.getLong(1);
        }
    }

    private static void resetMigrationState() throws SQLException {
        try (Connection connection = sourceConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM cutover_records");
            statement.executeUpdate("DELETE FROM shadow_comparisons");
            statement.executeUpdate("DELETE FROM migration_mappings");
            statement.executeUpdate("DELETE FROM migration_runs");
            statement.executeUpdate("DELETE FROM audit_events");
            statement.executeUpdate("""
                    UPDATE operational_state
                    SET mode = 'BOOTSTRAP', revision = 0, reason = 'Integration-test reset',
                        updated_by = NULL, updated_at = CURRENT_TIMESTAMP(6)
                    WHERE singleton_id = 1
                    """);
        }
    }

    private static DatabaseConfig databaseConfig() {
        return new DatabaseConfig(
                DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:"),
                DATABASE.getUsername(),
                DATABASE.getPassword(),
                4,
                5_000
        );
    }

    private static Connection sourceConnection() throws SQLException {
        return DriverManager.getConnection(
                DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:"),
                DATABASE.getUsername(),
                DATABASE.getPassword()
        );
    }
}
