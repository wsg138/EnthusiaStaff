package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.persistence.MariaDb;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V33 historical receipts are never given fabricated Discord message IDs. */
@Testcontainers
class StaffBotReceiptV34MigrationIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-19T14:00:00Z");
    private static final String RECEIPT = "4".repeat(18);
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("staffbot_v34_receipt_upgrade_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void oldDeliveredIntentIsQuarantinedWithOriginalTimestampAndStrictReceiptCheck()
            throws Exception {
        UUID reporter = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        UUID intent = UUID.randomUUID();
        try (HikariDataSource data = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            migrate(data, "33");
            insertLegacyDelivery(data, reporter, target, report, intent);
            migrate(data, null);
            assertLegacyQuarantined(data, intent);
            assertTrailingNewlineReceiptRejected(data, intent);
        }
    }

    private static void insertLegacyDelivery(
            HikariDataSource data, UUID reporter, UUID target, UUID report, UUID intent
    ) throws SQLException {
        MariaDbIntegrationSupport.insertPlayer(DATABASE, reporter, "UpgradeReporter", NOW);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, target, "UpgradeTarget", NOW);
        try (Connection connection = data.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO reports
                        (report_id, idempotency_key, reporter_id, target_id, reason_id,
                         description, state, server_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'chat.spam', 'Synthetic V33 report',
                            'OPEN', 'TEST', ?, ?)
                    """)) {
                insert.setBytes(1, MariaDbIntegrationSupport.uuidBytes(report));
                insert.setString(2, "receipt-v34-legacy-" + report);
                insert.setBytes(3, MariaDbIntegrationSupport.uuidBytes(reporter));
                insert.setBytes(4, MariaDbIntegrationSupport.uuidBytes(target));
                insert.setTimestamp(5, Timestamp.from(NOW));
                insert.setTimestamp(6, Timestamp.from(NOW));
                assertEquals(1, insert.executeUpdate());
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO staffbot_report_notification_intents
                        (intent_id, idempotency_key, report_id, event_type, state,
                         created_at, delivered_at)
                    VALUES (?, ?, ?, 'REPORT_CREATED', 'DELIVERED', ?, ?)
                    """)) {
                insert.setBytes(1, MariaDbIntegrationSupport.uuidBytes(intent));
                insert.setString(2, "v34:synthetic:legacy:" + intent);
                insert.setBytes(3, MariaDbIntegrationSupport.uuidBytes(report));
                insert.setTimestamp(4, Timestamp.from(NOW));
                insert.setTimestamp(5, Timestamp.from(NOW.plusSeconds(5)));
                assertEquals(1, insert.executeUpdate());
            }
        }
    }

    private static void assertLegacyQuarantined(HikariDataSource data, UUID intent)
            throws SQLException {
        try (Connection connection = data.getConnection();
             PreparedStatement select = connection.prepareStatement("""
                     SELECT state, delivered_at, legacy_delivered_at, discord_message_id
                     FROM staffbot_report_notification_intents WHERE intent_id = ?
                     """)) {
            select.setBytes(1, MariaDbIntegrationSupport.uuidBytes(intent));
            try (ResultSet row = select.executeQuery()) {
                assertTrue(row.next());
                assertEquals("REVIEW_REQUIRED", row.getString("state"));
                assertNull(row.getTimestamp("delivered_at"));
                assertEquals(NOW.plusSeconds(5),
                        row.getTimestamp("legacy_delivered_at").toInstant());
                assertNull(row.getString("discord_message_id"));
                assertFalse(row.next());
            }
        }
    }

    private static void assertTrailingNewlineReceiptRejected(
            HikariDataSource data, UUID intent
    ) throws SQLException {
        try (Connection connection = data.getConnection()) {
            try (PreparedStatement invalid = connection.prepareStatement("""
                    UPDATE staffbot_report_notification_intents
                    SET state = 'DELIVERED', delivered_at = ?, discord_message_id = ?
                    WHERE intent_id = ?
                    """)) {
                invalid.setTimestamp(1, Timestamp.from(NOW.plusSeconds(7)));
                invalid.setString(2, RECEIPT + "\n");
                invalid.setBytes(3, MariaDbIntegrationSupport.uuidBytes(intent));
                assertThrows(SQLException.class, invalid::executeUpdate,
                        "SQL receipt check must reject a trailing newline");
            }
        }
        assertLegacyQuarantined(data, intent);
    }

    private static void migrate(HikariDataSource data, String target) {
        var configuration = Flyway.configure()
                .dataSource(data)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .cleanDisabled(true);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }
}
