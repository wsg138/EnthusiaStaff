package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class JdbcDiscordOutboxClaimRetryTest {
    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    private static final String CLAIM_FAILURE_MESSAGE = "claim";

    @Test
    void recordChangedRetriesOnFreshTransactionsAndPreservesReadCommitted() {
        FakeSqlStore fixture = new FakeSqlStore(2, 1020, "HY000");
        var messages = new JdbcDiscordOutboxStore(fixture.dataSource())
                .claimDue("velocity-discord:retry", 1, Duration.ofMinutes(1), NOW);

        assertTrue(messages.isEmpty());
        assertEquals(3, fixture.connections);
        assertEquals(3, fixture.queries);
        assertEquals(2, fixture.rollbacks);
        assertEquals(1, fixture.commits);
        assertEquals(3, fixture.readCommittedSettings);
        assertEquals(3, fixture.closed);
    }

    @Test
    void persistentConflictStopsAfterSixClaimsWithoutLeasing() {
        FakeSqlStore fixture = new FakeSqlStore(10, 1020, "HY000");
        assertThrows(ModerationPersistenceException.class,
                () -> new JdbcDiscordOutboxStore(fixture.dataSource())
                        .claimDue("velocity-discord:bounded", 1, Duration.ofSeconds(30), NOW));
        assertEquals(6, fixture.queries);
        assertEquals(6, fixture.rollbacks);
        assertEquals(0, fixture.commits);
        assertEquals(6, fixture.closed);
    }

    @Test
    void prolongedRecordChangesCanRecoverWithoutDiscordSideEffects() {
        FakeSqlStore fixture = new FakeSqlStore(4, 1020, "HY000");
        var messages = new JdbcDiscordOutboxStore(fixture.dataSource())
                .claimDue("velocity-discord:extended", 1, Duration.ofSeconds(30), NOW);
        assertTrue(messages.isEmpty());
        assertEquals(5, fixture.queries);
        assertEquals(4, fixture.rollbacks);
        assertEquals(1, fixture.commits);
        assertEquals(5, fixture.readCommittedSettings);
    }

    @Test
    void interruptedRetryFailsClosedAndPreservesInterruptStatus() {
        FakeSqlStore fixture = new FakeSqlStore(10, 1020, "HY000");
        Thread.currentThread().interrupt();
        try {
            assertThrows(ModerationPersistenceException.class,
                    () -> new JdbcDiscordOutboxStore(fixture.dataSource())
                            .claimDue("velocity-discord:interrupted", 1, Duration.ofSeconds(30), NOW));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, fixture.queries);
            assertEquals(1, fixture.rollbacks);
            assertEquals(0, fixture.commits);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void nonTransientSqlFailureIsNotRetried() {
        FakeSqlStore fixture = new FakeSqlStore(10, 1045, "28000");
        assertThrows(ModerationPersistenceException.class,
                () -> new JdbcDiscordOutboxStore(fixture.dataSource())
                        .claimDue("velocity-discord:failclosed", 1, Duration.ofSeconds(30), NOW));
        assertEquals(1, fixture.queries);
        assertEquals(1, fixture.rollbacks);
        assertEquals(0, fixture.commits);
    }

    @Test
    void onlyDatabaseConflictStatesTriggerRetries() {
        assertTrue(JdbcDiscordOutboxStore.retryableClaimConflict(
                new ModerationPersistenceException(CLAIM_FAILURE_MESSAGE, new SQLException("conflict", "HY000", 1020))));
        assertTrue(JdbcDiscordOutboxStore.retryableClaimConflict(
                new ModerationPersistenceException(CLAIM_FAILURE_MESSAGE, new SQLException("deadlock", "40001", 1213))));
        assertTrue(JdbcDiscordOutboxStore.retryableClaimConflict(
                new ModerationPersistenceException(CLAIM_FAILURE_MESSAGE, new SQLException("serialization", "40001"))));
        assertFalse(JdbcDiscordOutboxStore.retryableClaimConflict(
                new ModerationPersistenceException(CLAIM_FAILURE_MESSAGE, new SQLException("permission denied", "28000", 1045))));
        assertFalse(JdbcDiscordOutboxStore.retryableClaimConflict(
                new ModerationPersistenceException(CLAIM_FAILURE_MESSAGE, new SQLException("network", "08006"))));
    }

    private static final class FakeSqlStore {
        private final int failuresBeforeSuccess;
        private final int code;
        private final String state;
        int connections;
        int queries;
        int rollbacks;
        int commits;
        int closed;
        int readCommittedSettings;

        private FakeSqlStore(int failuresBeforeSuccess, int code, String state) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
            this.code = code;
            this.state = state;
        }

        DataSource dataSource() {
            return proxy(DataSource.class, (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    connections++;
                    return connection();
                }
                if ("toString".equals(method.getName())) return "FakeSqlStore";
                throw new UnsupportedOperationException(method.getName());
            });
        }

        Connection connection() {
            return proxy(Connection.class, (proxy, method, args) -> switch (method.getName()) {
                case "setTransactionIsolation" -> {
                    assertEquals(Connection.TRANSACTION_READ_COMMITTED, args[0]);
                    readCommittedSettings++;
                    yield null;
                }
                case "setAutoCommit" -> null;
                case "prepareStatement" -> statement();
                case "commit" -> { commits++; yield null; }
                case "rollback" -> { rollbacks++; yield null; }
                case "close" -> { closed++; yield null; }
                case "isClosed" -> false;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        PreparedStatement statement() {
            return proxy(PreparedStatement.class, (proxy, method, args) -> switch (method.getName()) {
                case "setTimestamp", "setInt", "close" -> null;
                case "executeBatch" -> new int[0];
                case "executeQuery" -> {
                    queries++;
                    if (queries <= failuresBeforeSuccess) {
                        throw new SQLException("injected outbox conflict", state, code);
                    }
                    yield proxy(ResultSet.class, (r, m, a) -> switch (m.getName()) {
                        case "next" -> false;
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{type}, handler);
        }
    }
}
