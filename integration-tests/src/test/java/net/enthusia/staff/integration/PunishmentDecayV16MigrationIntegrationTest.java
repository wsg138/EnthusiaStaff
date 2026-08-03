package net.enthusia.staff.integration;

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
class PunishmentDecayV16MigrationIntegrationTest {
    private static final String CASE_ID = "5600000000000001";
    private static final Instant ISSUED_AT = Instant.parse("2026-08-03T04:30:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_decay_v16_upgrade_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void upgradesV15RowsWithoutInventingHistoricalDecayEligibility() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            migrate(dataSource, "15");
            insertPlayer(dataSource, targetId, "DecayTarget");
            insertPlayer(dataSource, actorId, "DecayActor");
            insertV15CaseAndStep(dataSource, targetId, actorId);

            migrate(dataSource, null);

            assertTrue(columnExists(dataSource, "punishment_steps", "decay_eligible"));
            assertTrue(columnExists(dataSource, "punishment_requests", "decay_eligible"));
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT selected_ordinal, recommended_sanctions_json, decay_eligible
                         FROM punishment_steps WHERE case_id = ?
                         """)) {
                statement.setString(1, CASE_ID);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(0, result.getInt("selected_ordinal"));
                    assertEquals("[]", result.getString("recommended_sanctions_json"));
                    assertNull(result.getObject("decay_eligible"));
                }
            }
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
            statement.setTimestamp(4, Timestamp.from(ISSUED_AT));
            statement.setTimestamp(5, Timestamp.from(ISSUED_AT));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertV15CaseAndStep(
            HikariDataSource dataSource,
            UUID targetId,
            UUID actorId
    ) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement caseStatement = connection.prepareStatement("""
                    INSERT INTO cases(
                        case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                        public_reason, exact_reason_id, sanction_family, internal_explanation,
                        configuration_version, visibility, state, issued_at, revision)
                    VALUES (?, 'v16-legacy-case', ?, ?, 'DecayActor', 'FOUNDER',
                        'V15 decay reason', 'decay.legacy', 'decay-test',
                        'V15 case predates decay eligibility snapshots', 'legacy-v15',
                        'PRIVATE', 'OPEN', ?, 0)
                    """)) {
                caseStatement.setString(1, CASE_ID);
                caseStatement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(targetId));
                caseStatement.setBytes(3, MariaDbIntegrationSupport.uuidBytes(actorId));
                caseStatement.setTimestamp(4, Timestamp.from(ISSUED_AT));
                assertEquals(1, caseStatement.executeUpdate());
            }
            try (PreparedStatement stepStatement = connection.prepareStatement("""
                    INSERT INTO punishment_steps(
                        case_id, raw_ordinal, effective_ordinal, selected_ordinal,
                        recency_bonus, step_label, contribution_json,
                        recommended_sanctions_json, escalation_contributes)
                    VALUES (?, 1, 1, 0, 0, 'V15 recommendation', JSON_ARRAY(), JSON_ARRAY(), TRUE)
                    """)) {
                stepStatement.setString(1, CASE_ID);
                assertEquals(1, stepStatement.executeUpdate());
            }
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
}
