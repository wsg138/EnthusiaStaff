package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.market.MarketComplianceKind;
import net.enthusia.staff.domain.market.MarketComplianceRequest;
import net.enthusia.staff.domain.market.MarketComplianceResult;
import net.enthusia.staff.domain.ports.MarketComplianceStore;
import net.enthusia.staff.persistence.JdbcMarketComplianceStore;
import net.enthusia.staff.persistence.MariaDb;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MarketComplianceV19MigrationIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");
    private static final String CASE_ID = "01JMARKETV190001";
    private static final UUID LEGACY_OPERATION = UUID.fromString(
            "b8cc4b64-730b-4428-9d72-eb5bc091eb17"
    );
    private static final UUID NEW_OPERATION = UUID.fromString(
            "b76a3d84-fcb7-4550-8507-8da19ef5268c"
    );
    private static final UUID TARGET = UUID.fromString("3ad7cd71-e80a-4917-8930-bbc674da05cc");
    private static final UUID ACTOR = UUID.fromString("5579a33e-bda4-43fa-b14d-cbb29b054fed");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_market_v19_upgrade_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void upgradesLegacyRowsWithoutTreatingThemAsRecoverableProviderIntents() throws Exception {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            migrate(dataSource, "18");
            insertFixtures();
            insertLegacyComplianceRow(dataSource);

            migrate(dataSource, null);

            assertLegacyRowPreserved(dataSource);
            assertTrue(columnExists(dataSource, "idempotency_key"));
            assertTrue(columnExists(dataSource, "recovery_until"));
            assertTrue(columnExists(dataSource, "revision"));
            assertTrue(columnExists(dataSource, "created_at"));
            assertTrue(columnExists(dataSource, "review_alerted_at"));

            MarketComplianceStore store = new JdbcMarketComplianceStore(
                    dataSource, new ObjectMapper()
            );
            assertTrue(store.recoverable(10).isEmpty());
            assertFalse(store.find(LEGACY_OPERATION).isPresent());

            MarketComplianceResult started = store.start(newRequest());
            assertEquals(MarketComplianceResult.Status.CREATED, started.status());
            assertEquals(NEW_OPERATION, started.operation().orElseThrow().operationId());
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

    private static void insertFixtures() throws Exception {
        MariaDbIntegrationSupport.insertPlayer(DATABASE, TARGET, "MarketTarget", NOW);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, ACTOR, "MarketActor", NOW);
        MariaDbIntegrationSupport.insertCase(DATABASE, CASE_ID, TARGET, ACTOR, NOW);
    }

    private static void insertLegacyComplianceRow(HikariDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO market_compliance_cases(
                         compliance_id, case_id, target_id, stall_id, state,
                         review_due_at, snapshot_json, updated_at
                     ) VALUES (?, ?, ?, 'legacy-stall', 'PREPARED', ?, JSON_OBJECT('legacy', TRUE), ?)
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(LEGACY_OPERATION));
            statement.setString(2, CASE_ID);
            statement.setBytes(3, MariaDbIntegrationSupport.uuidBytes(TARGET));
            statement.setTimestamp(4, Timestamp.from(NOW.plus(Duration.ofDays(7))));
            statement.setTimestamp(5, Timestamp.from(NOW));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertLegacyRowPreserved(HikariDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT case_id, target_id, stall_id, state, snapshot_json,
                            idempotency_key, recovery_until, revision,
                            created_at, review_alerted_at
                     FROM market_compliance_cases WHERE compliance_id = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(LEGACY_OPERATION));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(CASE_ID, result.getString("case_id"));
                assertEquals("legacy-stall", result.getString("stall_id"));
                assertEquals("PREPARED", result.getString("state"));
                assertTrue(result.getString("snapshot_json").contains("legacy"));
                assertNull(result.getString("idempotency_key"));
                assertNull(result.getTimestamp("recovery_until"));
                assertEquals(0L, result.getLong("revision"));
                assertNull(result.getTimestamp("created_at"));
                assertNull(result.getTimestamp("review_alerted_at"));
            }
        }
    }

    private static boolean columnExists(HikariDataSource dataSource, String column) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA=DATABASE()
                       AND TABLE_NAME='market_compliance_cases' AND COLUMN_NAME=?
                     """)) {
            statement.setString(1, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static MarketComplianceRequest newRequest() {
        return new MarketComplianceRequest(
                NEW_OPERATION,
                new IdempotencyKey("market:stall:v19-upgrade-test"),
                new CaseId(CASE_ID),
                TARGET,
                MarketComplianceKind.STALL,
                Optional.of("new-stall"),
                ACTOR,
                Optional.empty(),
                OptionalLong.empty(),
                NOW.plus(Duration.ofDays(7)),
                NOW.plus(Duration.ofDays(30)),
                NOW
        );
    }
}
