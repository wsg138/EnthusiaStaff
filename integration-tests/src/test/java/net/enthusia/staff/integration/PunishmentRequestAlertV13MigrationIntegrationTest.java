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
    private static final Instant AVAILABLE = CREATED.plusSeconds(15);
    private static final Instant UPDATED = CREATED.plusSeconds(45);
    private static final String DEAD_LETTER_ERROR = "PERMANENT_PRESENTATION_FAILURE";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_alert_v13_upgrade_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void upgradesV12RowsWithoutChangingExistingTerminalDeliveryMetadata() throws Exception {
        UUID pendingAlertId = UUID.randomUUID();
        UUID pendingRequestId = UUID.randomUUID();
        UUID pendingRecipientId = UUID.randomUUID();
        UUID deadLetterAlertId = UUID.randomUUID();
        UUID deadLetterRecipientId = UUID.randomUUID();
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            migrate(dataSource, "12");

            insertV12Direct(
                    dataSource,
                    pendingAlertId,
                    pendingRequestId,
                    pendingRecipientId,
                    new DeliveryMetadata("PENDING", 0, null, CREATED, CREATED, CREATED)
            );
            DeliveryMetadata deadLetterBefore = new DeliveryMetadata(
                    "DEAD_LETTER",
                    4,
                    DEAD_LETTER_ERROR,
                    AVAILABLE,
                    CREATED,
                    UPDATED
            );
            insertV12Direct(
                    dataSource,
                    deadLetterAlertId,
                    UUID.randomUUID(),
                    deadLetterRecipientId,
                    deadLetterBefore
            );

            migrate(dataSource, null);

            assertEquals("legacy-request-revision:7", stringValue(
                    dataSource, "SELECT occurrence_key FROM staff_alerts WHERE alert_id=?", pendingAlertId));
            assertEquals("PENDING", deliveryMetadata(
                    dataSource, pendingAlertId, pendingRecipientId).state());
            assertEquals(deadLetterBefore, deliveryMetadata(
                    dataSource, deadLetterAlertId, deadLetterRecipientId));
            assertTrue(enumContains(dataSource, "staff_alert_deliveries", "state", "CANCELLED"));
            assertTrue(columnExists(dataSource, "staff_alert_deliveries", "cancelled_at"));
            assertTrue(columnExists(dataSource, "staff_alert_deliveries", "cancel_reason"));
        }
    }

    private static void migrate(HikariDataSource dataSource, String target) {
        org.flywaydb.core.api.configuration.FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .cleanDisabled(true);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static void insertV12Direct(
            HikariDataSource dataSource,
            UUID alertId,
            UUID requestId,
            UUID recipientId,
            DeliveryMetadata metadata
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
                        alert_id, recipient_id, state, attempt_count, available_at,
                        lease_owner, lease_until, last_error_code, delivered_at,
                        created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, NULL, ?, ?)
                    """)) {
                statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
                statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(recipientId));
                statement.setString(3, metadata.state());
                statement.setInt(4, metadata.attemptCount());
                statement.setTimestamp(5, Timestamp.from(metadata.availableAt()));
                statement.setString(6, metadata.lastErrorCode());
                statement.setTimestamp(7, Timestamp.from(metadata.createdAt()));
                statement.setTimestamp(8, Timestamp.from(metadata.updatedAt()));
                assertEquals(1, statement.executeUpdate());
            }
        }
    }

    private static DeliveryMetadata deliveryMetadata(
            HikariDataSource dataSource,
            UUID alertId,
            UUID recipientId
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state, attempt_count, last_error_code, available_at, created_at, updated_at
                     FROM staff_alert_deliveries WHERE alert_id=? AND recipient_id=?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(recipientId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "delivery row must survive the V13 migration");
                return new DeliveryMetadata(
                        result.getString("state"),
                        result.getInt("attempt_count"),
                        result.getString("last_error_code"),
                        result.getTimestamp("available_at").toInstant(),
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("updated_at").toInstant()
                );
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

    private record DeliveryMetadata(
            String state,
            int attemptCount,
            String lastErrorCode,
            Instant availableAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
