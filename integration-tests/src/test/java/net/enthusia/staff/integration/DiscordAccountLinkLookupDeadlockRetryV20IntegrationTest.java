package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariDataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTransactionRollbackException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.persistence.JdbcAccountLinkingStore;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.JdbcPlayerDirectory;
import net.enthusia.staff.persistence.MariaDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordAccountLinkLookupDeadlockRetryV20IntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-25T03:15:00Z");
    private static final int FIRST_CONNECTION_ATTEMPT = 1;

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_discord_lookup_retry_v20")
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
    void minecraftInitiatorLookupRetriesFromFreshConnectionAfterDeadlockVictim() throws Exception {
        UUID minecraftPlayerId = UUID.fromString("6908f6de-17d9-4c6a-b7d9-1908197a0f29");
        String codeHash = "b".repeat(64);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            new JdbcPlayerDirectory(dataSource).recordSeenVerified(
                    minecraftPlayerId,
                    "LookupRetryPlayer",
                    PlayerPlatform.JAVA,
                    "integration-test",
                    NOW
            );
            new JdbcDiscordModerationPersistenceStore(dataSource).ensureMinecraftSubject(minecraftPlayerId, NOW);
            new JdbcAccountLinkingStore(dataSource)
                    .issueFromMinecraft(minecraftPlayerId, codeHash, NOW, NOW.plusSeconds(300));

            OneDeadlockDataSource deadlockOnce = new OneDeadlockDataSource(dataSource);
            JdbcAccountLinkingStore store = new JdbcAccountLinkingStore(deadlockOnce);

            assertEquals(minecraftPlayerId, store.minecraftInitiatorForCode(codeHash, NOW.plusSeconds(1)));
            assertEquals(2, deadlockOnce.connectionAttempts());
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
            if (connectionAttempts.incrementAndGet() == FIRST_CONNECTION_ATTEMPT) {
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
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
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
