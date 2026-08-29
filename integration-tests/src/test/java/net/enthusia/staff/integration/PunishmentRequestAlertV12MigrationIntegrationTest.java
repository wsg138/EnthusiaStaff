package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import net.enthusia.staff.persistence.MariaDb;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRequestAlertV12MigrationIntegrationTest {
    private static final Instant CREATED = Instant.now()
            .minus(Duration.ofDays(1))
            .truncatedTo(ChronoUnit.SECONDS);

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_alert_v12_upgrade_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void upgradesV11RowsWithoutConsumingSharedAudienceIntent() throws Exception {
        UUID directAlert = UUID.randomUUID();
        UUID directRecipient = UUID.randomUUID();
        UUID audienceAlert = UUID.randomUUID();
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .target("11")
                    .validateMigrationNaming(true)
                    .cleanDisabled(true)
                    .load()
                    .migrate();

            insertV11Alert(dataSource, directAlert, directRecipient,
                    "migration:direct", "DIRECT_RECIPIENT", "DELIVERED");
            insertV11Alert(dataSource, audienceAlert, null,
                    "migration:audience", "ELIGIBLE_REVIEWERS", "DELIVERED");

            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .validateMigrationNaming(true)
                    .cleanDisabled(true)
                    .load()
                    .migrate();

            assertEquals("CLOSED", intentState(dataSource, directAlert));
            assertEquals("ACTIVE", intentState(dataSource, audienceAlert));
            assertEquals("DELIVERED", deliveryState(dataSource, directAlert, directRecipient));
            assertEquals(0, deliveryCount(dataSource, audienceAlert));
            assertEquals(CREATED.plusSeconds(30L * 24 * 60 * 60), expiresAt(dataSource, directAlert));
            assertEquals(CREATED.plusSeconds(30L * 24 * 60 * 60), expiresAt(dataSource, audienceAlert));
            assertTrue(columnIsNotNullable(dataSource, "staff_alerts", "expires_at"));
            assertTrue(columnHasDefault(dataSource, "staff_alerts", "expires_at"));
            assertEquals("alert_id,recipient_id", primaryKeyColumns(dataSource, "staff_alert_deliveries"));
            assertFalse("CASCADE".equals(deliveryForeignKeyDeleteRule(dataSource)));
            assertFalse(hasCascadeDelete(dataSource, "staff_alert_deliveries"));
        }
    }

    private static void insertV11Alert(
            HikariDataSource dataSource,
            UUID alertId,
            UUID recipientId,
            String intentKey,
            String audience,
            String state
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO staff_alerts(
                         alert_id, intent_key, request_id, request_revision, lifecycle_event,
                         audience, recipient_id, minimum_rank, excluded_recipient_id, visibility,
                         schema_version, alert_type, payload_json, state, attempt_count,
                         available_at, created_at, delivered_at, expires_at)
                     VALUES (?, ?, ?, 0, 'REQUEST_SUBMITTED', ?, ?, ?, ?, 'PRIVATE', 1,
                         'REQUEST_SUBMITTED', JSON_OBJECT('schemaVersion', 1), ?, 1,
                         ?, ?, ?, NULL)
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            statement.setString(2, intentKey);
            statement.setBytes(3, MariaDbIntegrationSupport.uuidBytes(UUID.randomUUID()));
            statement.setString(4, audience);
            if (recipientId == null) {
                statement.setNull(5, java.sql.Types.BINARY);
            } else {
                statement.setBytes(5, MariaDbIntegrationSupport.uuidBytes(recipientId));
            }
            if ("ELIGIBLE_REVIEWERS".equals(audience)) {
                statement.setString(6, "MOD");
                statement.setBytes(7, MariaDbIntegrationSupport.uuidBytes(UUID.randomUUID()));
            } else {
                statement.setNull(6, java.sql.Types.VARCHAR);
                statement.setNull(7, java.sql.Types.BINARY);
            }
            statement.setString(8, state);
            Calendar utc = utcCalendar();
            statement.setTimestamp(9, Timestamp.from(CREATED), utc);
            statement.setTimestamp(10, Timestamp.from(CREATED), utc);
            statement.setTimestamp(11, "DELIVERED".equals(state)
                    ? Timestamp.from(CREATED.plusSeconds(10)) : null, utc);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static String intentState(HikariDataSource dataSource, UUID alertId) throws Exception {
        return stringValue(dataSource,
                "SELECT intent_state FROM staff_alerts WHERE alert_id = ?", alertId);
    }

    private static String deliveryState(
            HikariDataSource dataSource,
            UUID alertId,
            UUID recipientId
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state FROM staff_alert_deliveries
                     WHERE alert_id = ? AND recipient_id = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(recipientId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static int deliveryCount(HikariDataSource dataSource, UUID alertId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM staff_alert_deliveries WHERE alert_id = ?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static Instant expiresAt(HikariDataSource dataSource, UUID alertId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT expires_at FROM staff_alerts WHERE alert_id = ?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getTimestamp(1, utcCalendar()).toInstant();
            }
        }
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    private static boolean columnIsNotNullable(
            HikariDataSource dataSource,
            String table,
            String column
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT IS_NULLABLE FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                     """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return "NO".equals(result.getString(1));
            }
        }
    }

    private static boolean columnHasDefault(
            HikariDataSource dataSource,
            String table,
            String column
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                     """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1) != null;
            }
        }
    }

    private static String primaryKeyColumns(HikariDataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
                     FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = 'PRIMARY'
                     """)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static String deliveryForeignKeyDeleteRule(HikariDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS
                     WHERE CONSTRAINT_SCHEMA = DATABASE()
                       AND TABLE_NAME = 'staff_alert_deliveries'
                       AND CONSTRAINT_NAME = 'fk_staff_alert_delivery_alert'
                     """)) {
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static boolean hasCascadeDelete(HikariDataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
                     WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND DELETE_RULE = 'CASCADE'
                     """)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1) > 0;
            }
        }
    }

    private static String stringValue(
            HikariDataSource dataSource,
            String sql,
            UUID alertId
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) { // nosemgrep
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }
}
