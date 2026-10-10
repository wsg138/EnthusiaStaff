package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.persistence.MariaDb;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PolicyV2V28MigrationIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-06T23:00:00Z");
    private static final String LEGACY_CASE = "P2LEGACY00000001";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_policy_v2_upgrade")
            .withUsername("staff_test")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void upgradesCurrentSchemaWithoutInventingPolicyV2FactsForLegacyCases() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        try (HikariDataSource dataSource = MariaDb.open(databaseConfig(DATABASE))) {
            boolean hasFlywayV27 = hasFlywayV27();
            migrateTo(dataSource, hasFlywayV27 ? "27" : "26");
            if (!hasFlywayV27) {
                ensureCurrentV27Schema();
            }
            insertPlayer(DATABASE, targetId, "LegacyTarget", NOW.minusSeconds(120));
            insertPlayer(DATABASE, actorId, "LegacyActor", NOW.minusSeconds(120));
            insertCase(DATABASE, LEGACY_CASE, targetId, actorId, NOW.minusSeconds(60));

            assertFalse(tableExists("policy_v2_cases"));
            MariaDb.migrate(dataSource);

            assertTrue(tableExists("policy_v2_cases"));
            assertTrue(tableExists("policy_v2_remedy_enforcement"));
            assertTrue(tableExists("policy_v2_full_overturns"));
            assertEquals("31", latestMigrationVersion());
            assertTrue(tableExists("discord_investigation_cases"));
            assertEquals(1, legacyCaseCount());
            assertEquals(0, policyV2CaseCount());
        }
    }

    private static boolean hasFlywayV27() {
        return Thread.currentThread().getContextClassLoader()
                .getResource("db/migration/V27__staff_preferences.sql") != null;
    }

    private static void ensureCurrentV27Schema() throws Exception {
        if (tableExists("staff_preferences")) {
            return;
        }
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE staff_preferences (
                        staff_id BINARY(16) NOT NULL,
                        tool_inventory_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        updated_at TIMESTAMP(6) NOT NULL,
                        PRIMARY KEY (staff_id)
                    ) ENGINE=InnoDB
                    """);
        }
    }

    private static void migrateTo(HikariDataSource dataSource, String target) {
        Flyway.configure(Thread.currentThread().getContextClassLoader())
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    private static boolean tableExists(String table) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             var statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM information_schema.tables
                     WHERE table_schema = DATABASE() AND table_name = ?
                     """)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }

    private static String latestMigrationVersion() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             var statement = connection.prepareStatement("""
                     SELECT version
                     FROM flyway_schema_history
                     WHERE success = 1 AND version IS NOT NULL
                     ORDER BY installed_rank DESC
                     LIMIT 1
                     """);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getString(1);
        }
    }

    private static int legacyCaseCount() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             var statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM cases
                     WHERE case_id = ? AND exact_reason_id = 'integration.test'
                       AND configuration_version = 'integration-test-v1'
                     """)) {
            statement.setString(1, LEGACY_CASE);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int policyV2CaseCount() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             var statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM policy_v2_cases
                     WHERE case_id = ?
                     """)) {
            statement.setString(1, LEGACY_CASE);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
