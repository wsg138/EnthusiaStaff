package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class PunishmentHistoryV14MigrationIntegrationTest {
    private static final String CASE_ID = "V14HSTRY00000001";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T18:00:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_history_v14_upgrade_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void upgradesV13EventsAndAddsBoundedHistoryIndexesWithoutLosingAuditData() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID sanctionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            migrate(dataSource, "13");
            insertPlayer(dataSource, subjectId, "HistorySubject");
            insertPlayer(dataSource, actorId, "HistoryActor");
            insertCase(dataSource, subjectId, actorId);
            insertSanction(dataSource, sanctionId, subjectId);
            insertLegacyEvent(dataSource, eventId, sanctionId, actorId);

            migrate(dataSource, null);

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT case_id, subject_id, event_type, actor_id, occurred_at,
                             previous_status, resulting_status, linked_appeal_id,
                             linked_punishment_request_id, origin_runtime, reason, idempotency_key
                         FROM sanction_events WHERE event_id = ?
                         """)) {
                statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(eventId));
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next(), "the V13 event must survive the V14 migration");
                    assertEquals(CASE_ID, result.getString("case_id"));
                    assertArrayEquals(
                            MariaDbIntegrationSupport.uuidBytes(subjectId),
                            result.getBytes("subject_id")
                    );
                    assertEquals("CREATED", result.getString("event_type"));
                    assertArrayEquals(
                            MariaDbIntegrationSupport.uuidBytes(actorId),
                            result.getBytes("actor_id")
                    );
                    assertEquals(OCCURRED_AT, result.getTimestamp("occurred_at").toInstant());
                    assertNull(result.getString("previous_status"));
                    assertNull(result.getString("resulting_status"));
                    assertNull(result.getBytes("linked_appeal_id"));
                    assertNull(result.getBytes("linked_punishment_request_id"));
                    assertNull(result.getString("origin_runtime"));
                    assertEquals("Original audit reason", result.getString("reason"));
                    assertEquals("legacy-v13-event", result.getString("idempotency_key"));
                }
            }

            assertTrue(columnExists(dataSource, "sanction_events", "previous_expiration"));
            assertTrue(columnExists(dataSource, "sanction_events", "resulting_expiration"));
            assertTrue(indexExists(dataSource, "sanction_events", "idx_sanction_events_subject_time"));
            assertTrue(indexExists(dataSource, "sanction_events", "idx_sanction_events_case_time"));
            assertTrue(indexExists(dataSource, "cases", "idx_cases_history"));
            assertTrue(indexExists(dataSource, "sanctions", "idx_sanctions_history"));
            assertTrue(indexExists(dataSource, "punishment_requests", "idx_punishment_requests_history"));
            assertTrue(indexExists(dataSource, "website_appeal_requests", "idx_website_appeal_case_history"));
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

    private static void insertPlayer(
            HikariDataSource dataSource,
            UUID playerId,
            String username
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO players(
                         player_id, current_username, lowercase_username, platform,
                         first_seen_at, last_seen_at)
                     VALUES (?, ?, LOWER(?), 'JAVA', ?, ?)
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(playerId));
            statement.setString(2, username);
            statement.setString(3, username);
            statement.setTimestamp(4, Timestamp.from(OCCURRED_AT));
            statement.setTimestamp(5, Timestamp.from(OCCURRED_AT));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertCase(
            HikariDataSource dataSource,
            UUID subjectId,
            UUID actorId
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO cases(
                         case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                         public_reason, exact_reason_id, sanction_family, internal_explanation,
                         configuration_version, visibility, state, issued_at, revision)
                     VALUES (?, 'v14-case', ?, ?, 'HistoryActor', 'FOUNDER',
                         'Migration history reason', 'migration.history', 'BAN',
                         'Migration private detail', 'v14-test', 'PRIVATE', 'OPEN', ?, 0)
                     """)) {
            statement.setString(1, CASE_ID);
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(subjectId));
            statement.setBytes(3, MariaDbIntegrationSupport.uuidBytes(actorId));
            statement.setTimestamp(4, Timestamp.from(OCCURRED_AT));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSanction(
            HikariDataSource dataSource,
            UUID sanctionId,
            UUID subjectId
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO sanctions(
                         sanction_id, case_id, target_id, sanction_type, status,
                         issued_at, activated_at, expiration_at, ended_at, revision)
                     VALUES (?, ?, ?, 'BAN', 'ACTIVE', ?, ?, NULL, NULL, 0)
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(sanctionId));
            statement.setString(2, CASE_ID);
            statement.setBytes(3, MariaDbIntegrationSupport.uuidBytes(subjectId));
            statement.setTimestamp(4, Timestamp.from(OCCURRED_AT));
            statement.setTimestamp(5, Timestamp.from(OCCURRED_AT));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertLegacyEvent(
            HikariDataSource dataSource,
            UUID eventId,
            UUID sanctionId,
            UUID actorId
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO sanction_events(
                         event_id, sanction_id, event_type, actor_id, occurred_at,
                         reason, event_json, idempotency_key)
                     VALUES (?, ?, 'CREATED', ?, ?, 'Original audit reason',
                         JSON_OBJECT('source', 'V13'), 'legacy-v13-event')
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(eventId));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(sanctionId));
            statement.setBytes(3, MariaDbIntegrationSupport.uuidBytes(actorId));
            statement.setTimestamp(4, Timestamp.from(OCCURRED_AT));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static boolean columnExists(
            HikariDataSource dataSource,
            String table,
            String column
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?
                     """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean indexExists(
            HikariDataSource dataSource,
            String table,
            String index
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?
                     """)) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
