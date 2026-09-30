package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import net.enthusia.staff.domain.commandbridge.CommandBridgeOutcome;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.CommandBridgeAuditStore;
import net.enthusia.staff.persistence.JdbcCommandBridgeAuditStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CommandBridgeAuditIntegrationTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUBJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ACTOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-09-30T19:00:00Z");
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String TEST_USERNAME = "command_bridge_user";
    private static final String TEST_PASSWORD = UUID.randomUUID().toString();

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_command_bridge_test")
            .withUsername(TEST_USERNAME)
            .withPassword(TEST_PASSWORD);

    @BeforeAll
    static void migrateSchema() {
        try (MariaDbRuntime ignored = MariaDb.initialize(databaseConfig(DATABASE))) {
            // Flyway migration is the assertion prerequisite.
        }
    }

    @BeforeEach
    void clearAuditRows() throws SQLException {
        try (Connection db = connection(DATABASE);
             PreparedStatement statement = db.prepareStatement(
                     "DELETE FROM audit_events WHERE event_type IN ('DISCORD_CONSOLE_REQUEST', 'DISCORD_CONSOLE_RESULT')")) {
            statement.executeUpdate();
        }
    }

    @Test
    void durableClaimSurvivesStoreRestartAndTerminalOutcomeReplays() throws SQLException {
        JdbcCommandBridgeAuditStore first = new JdbcCommandBridgeAuditStore(dataSource());
        CommandBridgeAuditStore.RequestAudit request = request(FINGERPRINT);

        assertEquals(CommandBridgeAuditStore.Status.CLAIMED, first.claim(request).status());
        JdbcCommandBridgeAuditStore restarted = new JdbcCommandBridgeAuditStore(dataSource());
        assertEquals(CommandBridgeAuditStore.Status.UNRESOLVED_PRIOR_REQUEST, restarted.claim(request).status());

        restarted.complete(REQUEST_ID, CommandBridgeOutcome.SUCCESS, NOW.plusSeconds(1));
        CommandBridgeAuditStore.Claim replay = new JdbcCommandBridgeAuditStore(dataSource()).claim(request);
        assertEquals(CommandBridgeAuditStore.Status.TERMINAL_REPLAY, replay.status());
        assertEquals(CommandBridgeOutcome.SUCCESS, replay.terminalOutcome().orElseThrow());
        assertEquals(2, commandAuditRowCount());
    }

    @Test
    void changedFingerprintConflictsAndAuditNeverStoresRawCommandArguments() throws SQLException {
        JdbcCommandBridgeAuditStore store = new JdbcCommandBridgeAuditStore(dataSource());
        assertEquals(CommandBridgeAuditStore.Status.CLAIMED, store.claim(request(FINGERPRINT)).status());
        assertEquals(CommandBridgeAuditStore.Status.REQUEST_ID_CONFLICT,
                store.claim(request("b".repeat(64))).status());

        String payload = requestPayload();
        assertTrue(payload.contains("\"commandName\":\"list\""));
        assertFalse(payload.contains("password"));
        assertFalse(payload.contains("secret-value"));
    }

    private static CommandBridgeAuditStore.RequestAudit request(String fingerprint) {
        return new CommandBridgeAuditStore.RequestAudit(
                REQUEST_ID,
                new ModerationSubjectId(SUBJECT_ID),
                ACTOR_ID,
                "smp",
                "list",
                fingerprint,
                NOW
        );
    }

    private static DataSource dataSource() {
        return new DriverManagerDataSource(
                DATABASE.getJdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword()
        );
    }

    private static int commandAuditRowCount() throws SQLException {
        try (Connection db = connection(DATABASE);
             PreparedStatement statement = db.prepareStatement("""
                     SELECT COUNT(*) FROM audit_events
                     WHERE event_type IN ('DISCORD_CONSOLE_REQUEST', 'DISCORD_CONSOLE_RESULT')
                     """);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    private static String requestPayload() throws SQLException {
        try (Connection db = connection(DATABASE);
             PreparedStatement statement = db.prepareStatement("""
                     SELECT event_json FROM audit_events
                     WHERE event_type = 'DISCORD_CONSOLE_REQUEST'
                     """);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getString(1);
        }
    }

    private record DriverManagerDataSource(String url, String username, String password) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String user, String pass) throws SQLException {
            return DriverManager.getConnection(url, user, pass);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // Test data source has no global log writer.
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // DriverManager timeout is not mutated by this test adapter.
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Unsupported unwrap");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
