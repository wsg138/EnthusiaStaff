package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.persistence.MariaDb;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRequestAlertV13MigrationIntegrationTest {
    private static final Instant CREATED = Instant.parse("2026-07-31T17:00:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_alert_v13_upgrade_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void upgradesV12RowsWithStableOccurrenceAndCancellationSchema() throws Exception {
        UUID alertId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID deadLetterAlertId = UUID.randomUUID();
        UUID deadLetterRecipientId = UUID.randomUUID();
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .target("12")
                    .validateMigrationNaming(true)
                    .cleanDisabled(true)
                    .load()
                    .migrate();

            insertV12Direct(dataSource, alertId, requestId, recipientId, "PENDING");
            insertV12Direct(
                    dataSource,
                    deadLetterAlertId,
                    UUID.randomUUID(),
                    deadLetterRecipientId,
                    "DEAD_LETTER"
            );

            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .validateMigrationNaming(true)
                    .cleanDisabled(true)
                    .load()
                    .migrate();

            assertEquals("legacy-request-revision:7", stringValue(
                    dataSource, "SELECT occurrence_key FROM staff_alerts WHERE alert_id=?", alertId));
            assertEquals("PENDING", stringValue(dataSource, """
                    SELECT state FROM staff_alert_deliveries
                    WHERE alert_id=? AND recipient_id=?
                    """, alertId, recipientId));
            assertEquals("DEAD_LETTER", stringValue(dataSource, """
                    SELECT state FROM staff_alert_deliveries
                    WHERE alert_id=? AND recipient_id=?
                    """, deadLetterAlertId, deadLetterRecipientId));
            assertTrue(enumContains(dataSource, "staff_alert_deliveries", "state", "CANCELLED"));
            assertTrue(columnExists(dataSource, "staff_alert_deliveries", "cancelled_at"));
            assertTrue(columnExists(dataSource, "staff_alert_deliveries", "cancel_reason"));
        }
    }

    private static void insertV12Direct(
            HikariDataSource dataSource,
            UUID alertId,
            UUID requestId,
            UUID recipientId,
            String deliveryState
    ) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO staff_alerts(
                        alert_id, intent_key, request_id, request_revision, lifecycle_event,
                        audience, recipient_id, minimum_rank, excluded_recipient_id, visibility,
                        schema_version, alert_type, payload_json, state, attempt_count,
                        available_at, created_at, expires_at, intent_state)
                    VALUES (?, ?, ?, 7, 'REQUEST_SUBMITTED',
                        'DIRECT_RECIPIENT', ?, NULL, NULL, 'PRIVATE', 1,
                        'REQUEST_SUBMITTED', JSON_OBJECT('schemaVersion', 1), 'PENDING', 0,
                        ?, ?, ?, 'ACTIVE')
                    """)) {
                statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
                statement.setString(2, "migration:v13:" + alertId);
                statement.setBytes(3, MariaDbIntegrationSupport.uuidBytes(requestId));
                statement.setBytes(4, MariaDbIntegrationSupport.uuidBytes(recipientId));
                statement.setTimestamp(5, Timestamp.from(CREATED));
                statement.setTimestamp(6, Timestamp.from(CREATED));
                statement.setTimestamp(7, Timestamp.from(CREATED.plusSeconds(3600)));
                assertEquals(1, statement.executeUpdate());
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO staff_alert_deliveries(
                        alert_id, recipient_id, state, attempt_count,
                        available_at, created_at, updated_at)
                    VALUES (?, ?, ?, 0, ?, ?, ?)
                    """)) {
                statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
                statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(recipientId));
                statement.setString(3, deliveryState);
                statement.setTimestamp(4, Timestamp.from(CREATED));
                statement.setTimestamp(5, Timestamp.from(CREATED));
                statement.setTimestamp(6, Timestamp.from(CREATED));
                assertEquals(1, statement.executeUpdate());
            }
        }
    }

    private static String stringValue(
            HikariDataSource dataSource,
            String sql,
            UUID... identifiers
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) { // nosemgrep
            for (int index = 0; index < identifiers.length; index++) {
                statement.setBytes(index + 1, MariaDbIntegrationSupport.uuidBytes(identifiers[index]));
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static boolean columnExists(
            HikariDataSource dataSource,
            String table,
            String column
    ) throws Exception {
        return metadataValue(dataSource, table, column, "COLUMN_NAME") != null;
    }

    private static boolean enumContains(
            HikariDataSource dataSource,
            String table,
            String column,
            String value
    ) throws Exception {
        String type = metadataValue(dataSource, table, column, "COLUMN_TYPE");
        return type != null && type.contains("'" + value + "'");
    }

    private static String metadataValue(
            HikariDataSource dataSource,
            String table,
            String column,
            String selectedColumn
    ) throws Exception {
        String sql = switch (selectedColumn) {
            case "COLUMN_NAME" -> """
                    SELECT COLUMN_NAME FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?
                    """;
            case "COLUMN_TYPE" -> """
                    SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?
                    """;
            default -> throw new IllegalArgumentException("unsupported metadata column");
        };
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) { // nosemgrep
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }
}
