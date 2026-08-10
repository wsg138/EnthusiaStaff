package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.common.security.HmacTokenService;
import net.enthusia.staff.common.security.NetworkIdentityProtector;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.migration.MigrationMode;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.migration.MigrationExecutionReport;
import net.enthusia.staff.persistence.migration.ShadowSummary;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LiteBansMigrationIntegrationTest {
    private static final String LEGACY_PREFIX = "legacy_";
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID BAN_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final Instant ISSUED_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    private void prepareLegacyTables() throws SQLException {
        try (Connection connection = sourceConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS legacy_history");
            statement.execute("DROP TABLE IF EXISTS legacy_mutes");
            statement.execute("DROP TABLE IF EXISTS legacy_bans");
            statement.execute("""
                    CREATE TABLE legacy_bans (
                        id BIGINT NOT NULL PRIMARY KEY,
                        uuid VARCHAR(36) NULL,
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
        insertLegacyRows();
    }

    @Test
    void reconcilesSourceChangesAndRerunsWithoutDuplicateCases() throws SQLException {
        DatabaseConfig config = databaseConfig();
        try (MariaDbRuntime runtime = MariaDb.initialize(config)) {
            prepareLegacyTables();
            var state = runtime.operationalStateStore().current();
            assertTrue(runtime.operationalStateStore().transition(
                    state.revision(),
                    OperationalMode.SHADOW_MIGRATION,
                    null,
                    "Integration-test shadow authority",
                    Instant.parse("2026-07-20T00:00:00Z")
            ));
            NetworkIdentityProtector protector = protector();

            MigrationExecutionReport initial = runtime.liteBansMigrationService(protector).execute(
                    config, LEGACY_PREFIX, 100, MigrationMode.SHADOW
            );
            assertInitialMigration(initial);

            endLegacyBan();

            MigrationExecutionReport reconciled = runtime.liteBansMigrationService(protector).execute(
                    config, LEGACY_PREFIX, 100, MigrationMode.SHADOW
            );
            assertReconciledMigration(reconciled);

            MigrationExecutionReport replay = runtime.liteBansMigrationService(protector).execute(
                    config, LEGACY_PREFIX, 100, MigrationMode.SHADOW
            );
            assertReplayMigration(replay);

            deleteLegacyBan();

            MigrationExecutionReport deletedSource = runtime.liteBansMigrationService(protector).execute(
                    config, LEGACY_PREFIX, 100, MigrationMode.SHADOW
            );
            assertDeletedSourceDetected(deletedSource);
        }
    }

    private static void assertInitialMigration(MigrationExecutionReport report) throws SQLException {
        assertEquals(3, report.importedRecords());
        assertEquals(0, report.reconciledRecords());
        assertEquals(1, report.protectedIdentityRecords());
        assertCleanShadowSummary(report);
        assertEquals(3, importedCaseCount());
        assertEquals(1, uuidBackedBanMappingCount());
    }

    private static void assertReconciledMigration(MigrationExecutionReport report) throws SQLException {
        assertEquals(0, report.importedRecords());
        assertEquals(1, report.reconciledRecords());
        assertEquals(2, report.replayedRecords());
        assertCleanShadowSummary(report);
        assertEquals("ENDED_EARLY", importedBanStatus());
        assertEquals(1, legacySyncEventCount());
    }

    private static void assertReplayMigration(MigrationExecutionReport report) throws SQLException {
        assertEquals(0, report.importedRecords());
        assertEquals(0, report.reconciledRecords());
        assertEquals(3, report.replayedRecords());
        assertCleanShadowSummary(report);
        assertEquals(3, importedCaseCount());
        assertEquals(1, uuidBackedBanMappingCount());
        assertEquals(1, legacySyncEventCount());
    }

    private static void assertDeletedSourceDetected(MigrationExecutionReport report) throws SQLException {
        ShadowSummary summary = report.shadowSummary().orElseThrow();
        assertFalse(summary.countsMatch());
        assertComparisonDimensionsMatch(summary);
        assertEquals(1, summary.mismatchCount());
        assertEquals(3, importedCaseCount());
    }

    private static void endLegacyBan() throws SQLException {
        Instant removedAt = Instant.parse("2026-07-25T12:00:00Z");
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE legacy_bans SET active = FALSE, removed_by_date = ? WHERE id = 1
                     """)) {
            statement.setTimestamp(1, Timestamp.from(removedAt));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void deleteLegacyBan() throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM legacy_bans WHERE id = 1")) {
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertCleanShadowSummary(MigrationExecutionReport report) {
        ShadowSummary summary = report.shadowSummary().orElseThrow();
        assertTrue(summary.countsMatch());
        assertComparisonDimensionsMatch(summary);
        assertEquals(0, summary.mismatchCount());
    }

    private static void assertComparisonDimensionsMatch(ShadowSummary summary) {
        assertTrue(summary.checksumsMatch());
        assertTrue(summary.activeSanctionsMatch());
        assertTrue(summary.uuidMappingsMatch());
        assertTrue(summary.expirationsMatch());
        assertTrue(summary.loginDecisions().matches());
        assertTrue(summary.muteDecisions().matches());
        assertTrue(summary.ipBanDecisions().matches());
    }

    private static void insertLegacyRows() throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement ipBan = connection.prepareStatement("""
                    INSERT INTO legacy_bans(
                        id, uuid, ip, reason, banned_by_name, removed_by_date,
                        time, until, active, ipban
                    ) VALUES (1, NULL, '203.0.113.25', 'Cheating', 'LegacyMod', NULL, ?, ?, TRUE, TRUE)
                    """);
             PreparedStatement uuidBan = connection.prepareStatement("""
                    INSERT INTO legacy_bans(
                        id, uuid, ip, reason, banned_by_name, removed_by_date,
                        time, until, active, ipban
                    ) VALUES (3, ?, NULL, 'UUID-only ban', 'LegacyMod', NULL, ?, ?, TRUE, FALSE)
                    """);
             PreparedStatement mute = connection.prepareStatement("""
                    INSERT INTO legacy_mutes(
                        id, uuid, reason, muted_by_name, removed_by_date,
                        time, until, active
                    ) VALUES (2, ?, 'Spam', 'LegacyMod', NULL, ?, ?, TRUE)
                    """);
             PreparedStatement history = connection.prepareStatement("""
                     INSERT INTO legacy_history(id, date, name, uuid, ip)
                     VALUES (1, ?, 'Example', ?, '203.0.113.25')
                     """)) {
            ipBan.setLong(1, ISSUED_AT.toEpochMilli());
            ipBan.setLong(2, EXPIRES_AT.toEpochMilli());
            ipBan.executeUpdate();

            uuidBan.setString(1, BAN_PLAYER_ID.toString());
            uuidBan.setLong(2, ISSUED_AT.toEpochMilli());
            uuidBan.setLong(3, EXPIRES_AT.toEpochMilli());
            uuidBan.executeUpdate();

            mute.setString(1, PLAYER_ID.toString());
            mute.setLong(2, ISSUED_AT.toEpochMilli());
            mute.setLong(3, EXPIRES_AT.toEpochMilli());
            mute.executeUpdate();

            history.setTimestamp(1, Timestamp.from(ISSUED_AT.minusSeconds(60)));
            history.setString(2, PLAYER_ID.toString());
            history.executeUpdate();
        }
    }

    private static String importedBanStatus() throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT s.status
                     FROM sanctions s
                     JOIN migration_mappings m ON m.case_id = s.case_id
                     WHERE m.source_table = 'legacy_bans' AND m.external_id = '1'
                     """);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new AssertionError("Imported ban was not found");
            }
            return result.getString(1);
        }
    }

    private static long importedCaseCount() throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM cases WHERE configuration_version = 'litebans-import-v1'
                     """)) {
            return count(statement);
        }
    }

    private static long uuidBackedBanMappingCount() throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM migration_mappings
                     WHERE source_table = 'legacy_bans' AND external_id = '3'
                     """)) {
            return count(statement);
        }
    }

    private static long legacySyncEventCount() throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM sanction_events WHERE event_type = 'LEGACY_SYNC'
                     """)) {
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

    private static NetworkIdentityProtector protector() {
        byte[] hmac = new byte[32];
        byte[] encryption = new byte[32];
        java.util.Arrays.fill(hmac, (byte) 0x31);
        java.util.Arrays.fill(encryption, (byte) 0x52);
        return new NetworkIdentityProtector(
                new HmacTokenService(1, new SecretKeySpec(hmac, "HmacSHA256")),
                1,
                new SecretKeySpec(encryption, "AES"),
                new SecureRandom()
        );
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
