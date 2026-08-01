package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import net.enthusia.staff.domain.migration.CutoverEvidence;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.UuidBytes;
import net.enthusia.staff.persistence.migration.CutoverCoordinator;
import net.enthusia.staff.persistence.migration.CutoverOutcome;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CutoverCoordinatorIntegrationTest {
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
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
    void commitsAndAuditsOperationalSafetyTransitions() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            resetCutoverState();
            enterShadowMode(runtime, Instant.now().minus(Duration.ofDays(8)));
            CutoverCoordinator coordinator = runtime.cutoverCoordinator();

            assertTrue(coordinator.enterMaintenance(ACTOR_ID, "Prepare final LiteBans import"));
            assertFalse(coordinator.enterMaintenance(ACTOR_ID, "Duplicate maintenance request"));
            assertEquals(OperationalMode.MAINTENANCE, runtime.operationalStateStore().current().mode());
            assertTrue(coordinator.abortMaintenance(ACTOR_ID, "Final import validation failed"));
            assertEquals(OperationalMode.SHADOW_MIGRATION, runtime.operationalStateStore().current().mode());

            var shadow = runtime.operationalStateStore().current();
            assertTrue(runtime.operationalStateStore().transition(
                    shadow.revision(),
                    OperationalMode.ACTIVE,
                    ACTOR_ID,
                    "Integration-test active authority",
                    Instant.now()
            ));
            assertTrue(coordinator.freezeActiveAuthority(ACTOR_ID, "Emergency integrity freeze"));
            assertEquals(OperationalMode.READ_ONLY_FAILURE, runtime.operationalStateStore().current().mode());

            assertEquals(1, auditCount("LITEBANS_CUTOVER_MAINTENANCE_ENTERED"));
            assertEquals(1, auditCount("LITEBANS_CUTOVER_MAINTENANCE_ABORTED"));
            assertEquals(1, auditCount("LITEBANS_CUTOVER_EMERGENCY_FREEZE"));
        }
    }

    @Test
    void requiresFinalEvidenceAndActivatesExactlyOnce() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            resetCutoverState();
            enterShadowMode(runtime, Instant.now().minus(Duration.ofDays(8)));
            CutoverCoordinator coordinator = runtime.cutoverCoordinator();
            assertTrue(coordinator.enterMaintenance(ACTOR_ID, "Run final cutover gate"));

            Instant maintenanceStarted = runtime.operationalStateStore().current().updatedAt();
            insertCompleteShadowWindow(maintenanceStarted);
            assertFinalImportBlocksActivation(coordinator);

            UUID finalRunId = insertCompletedRun("CUTOVER", maintenanceStarted, maintenanceStarted);
            insertMatchingComparisons(finalRunId, maintenanceStarted);
            CutoverEvidence evidence = coordinator.latestEvidence().orElseThrow();
            assertTrue(evidence.finalIncrementalImportComplete());
            assertTrue(coordinator.assess(Optional.empty()).allowed());

            CutoverOutcome activated = coordinator.activate(ACTOR_ID, Optional.empty());
            assertTrue(activated.activated());
            assertTrue(activated.cutoverId().isPresent());
            assertEquals(OperationalMode.ACTIVE, runtime.operationalStateStore().current().mode());
            assertEquals(finalRunId, recordedFinalRunId());
            assertEquals(1, tableCount("cutover_records"));
            assertEquals(1, auditCount("LITEBANS_CUTOVER_ACTIVATED"));

            CutoverOutcome repeated = coordinator.activate(ACTOR_ID, Optional.empty());
            assertFalse(repeated.activated());
            assertEquals(List.of("MAINTENANCE_REQUIRED"), repeated.assessment().blockers());
            assertEquals(1, tableCount("cutover_records"));
        }
    }

    private static void assertFinalImportBlocksActivation(CutoverCoordinator coordinator) {
        CutoverEvidence evidence = coordinator.latestEvidence().orElseThrow();
        assertFalse(evidence.finalIncrementalImportComplete());
        assertEquals(
                List.of("FINAL_IMPORT_INCOMPLETE"),
                coordinator.assess(Optional.empty()).blockers()
        );
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

    private static UUID insertCompletedRun(String mode, Instant startedAt, Instant completedAt) throws SQLException {
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

    private static UUID recordedFinalRunId() throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT migration_run_id FROM cutover_records"
             );
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new AssertionError("Cutover record was not persisted");
            }
            return UuidBytes.fromBytes(result.getBytes(1));
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

    private static long tableCount(String table) throws SQLException {
        if (!"cutover_records".equals(table)) {
            throw new IllegalArgumentException("Unsupported test table: " + table);
        }
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM cutover_records")) {
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

    private static void resetCutoverState() throws SQLException {
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
