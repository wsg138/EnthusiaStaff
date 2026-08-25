package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.persistence.JdbcAccountLinkingStore;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.MariaDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordAccountLinkIssuanceDeadlockRetryV20IntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-25T02:45:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_discord_issue_retry_v20")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void issuanceRetriesFromFreshConnectionAfterDeadlockVictim() throws Exception {
        DiscordUserId discordUserId = new DiscordUserId("18446744073709550101");
        String codeHash = "a".repeat(64);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            new JdbcDiscordModerationPersistenceStore(dataSource).ensureDiscordSubject(discordUserId, NOW);
            OneDeadlockDataSource deadlockOnce = new OneDeadlockDataSource(dataSource);
            JdbcAccountLinkingStore store = new JdbcAccountLinkingStore(deadlockOnce);

            store.issueFromDiscord(discordUserId, codeHash, NOW, NOW.plusSeconds(300));

            assertEquals(2, deadlockOnce.connectionAttempts());
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT state
                         FROM discord_link_codes
                         WHERE code_hash = ?
                         """)) {
                statement.setString(1, codeHash);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("ACTIVE", result.getString("state"));
                    assertTrue(!result.next());
                }
            }
        }
    }

    private static final class OneDeadlockDataSource implements DataSource {
        private final DataSource delegate;
        private final AtomicInteger connectionAttempts = new AtomicInteger();

        private OneDeadlockDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        int connectionAttempts() {
            return connectionAttempts.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (connectionAttempts.incrementAndGet() == 1) {
                throw new SQLTransactionRollbackException("deadlock victim fixture", "40001", 1213);
            }
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return delegate.getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
