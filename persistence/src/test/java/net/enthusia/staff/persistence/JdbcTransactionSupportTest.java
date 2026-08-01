package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class JdbcTransactionSupportTest {
    @Test
    void commitsReturnsAndRestoresSuccessfulTransactions() {
        ConnectionState state = new ConnectionState();

        String result = JdbcTransactionSupport.execute(
                dataSource(state.connection()),
                "Transaction failed",
                connection -> "committed"
        );

        assertEquals("committed", result);
        assertEquals(1, state.autoCommitDisableCalls);
        assertEquals(1, state.commitCalls);
        assertEquals(0, state.rollbackCalls);
        assertEquals(1, state.autoCommitResetCalls);
        assertEquals(1, state.closeCalls);
    }

    @Test
    void rollsBackRuntimeExceptionsAndPropagatesTheOriginalInstance() {
        ConnectionState state = new ConnectionState();
        IllegalStateException failure = new IllegalStateException("transaction work failed");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> JdbcTransactionSupport.execute(
                        dataSource(state.connection()),
                        "Transaction failed",
                        connection -> {
                            throw failure;
                        }
                )
        );

        assertSame(failure, thrown);
        assertEquals(1, state.rollbackCalls);
        assertEquals(1, state.autoCommitResetCalls);
        assertEquals(1, state.closeCalls);
    }

    @Test
    void wrapsSqlWorkFailuresAfterRollback() {
        ConnectionState state = new ConnectionState();
        SQLException failure = new SQLException("work failed");

        ModerationPersistenceException thrown = assertThrows(
                ModerationPersistenceException.class,
                () -> JdbcTransactionSupport.execute(
                        dataSource(state.connection()),
                        "Transaction failed",
                        connection -> {
                            throw failure;
                        }
                )
        );

        assertEquals("Transaction failed", thrown.getMessage());
        assertSame(failure, thrown.getCause());
        assertEquals(1, state.rollbackCalls);
        assertEquals(1, state.autoCommitResetCalls);
        assertEquals(1, state.closeCalls);
    }

    @Test
    void commitFailureRollsBackAndIsWrapped() {
        ConnectionState state = new ConnectionState();
        state.failCommit = true;

        ModerationPersistenceException thrown = assertThrows(
                ModerationPersistenceException.class,
                () -> JdbcTransactionSupport.execute(
                        dataSource(state.connection()),
                        "Commit failed",
                        connection -> "result"
                )
        );

        assertEquals("Commit failed", thrown.getMessage());
        assertEquals("commit failed", thrown.getCause().getMessage());
        assertEquals(1, state.commitCalls);
        assertEquals(1, state.rollbackCalls);
        assertEquals(1, state.autoCommitResetCalls);
        assertEquals(1, state.closeCalls);
    }

    @Test
    void connectionAcquisitionFailureIsWrappedWithTheOperationMessage() {
        SQLException failure = new SQLException("connection failed");
        DataSource dataSource = proxy(DataSource.class, (ignored, method, arguments) -> {
            if (method.getName().equals("getConnection")) {
                throw failure;
            }
            throw new UnsupportedOperationException(method.getName());
        });

        ModerationPersistenceException thrown = assertThrows(
                ModerationPersistenceException.class,
                () -> JdbcTransactionSupport.execute(dataSource, "Open failed", connection -> "unused")
        );

        assertEquals("Open failed", thrown.getMessage());
        assertSame(failure, thrown.getCause());
    }

    @Test
    void disablingAutoCommitFailureClosesWithoutAttemptingRollback() {
        ConnectionState state = new ConnectionState();
        state.failAutoCommitDisable = true;

        ModerationPersistenceException thrown = assertThrows(
                ModerationPersistenceException.class,
                () -> JdbcTransactionSupport.execute(
                        dataSource(state.connection()),
                        "Setup failed",
                        connection -> "unused"
                )
        );

        assertEquals("auto-commit disable failed", thrown.getCause().getMessage());
        assertEquals(1, state.autoCommitDisableCalls);
        assertEquals(0, state.commitCalls);
        assertEquals(0, state.rollbackCalls);
        assertEquals(0, state.autoCommitResetCalls);
        assertEquals(1, state.closeCalls);
    }

    @Test
    void closeFailureAfterCommitIsWrapped() {
        ConnectionState state = new ConnectionState();
        state.failClose = true;

        ModerationPersistenceException thrown = assertThrows(
                ModerationPersistenceException.class,
                () -> JdbcTransactionSupport.execute(
                        dataSource(state.connection()),
                        "Close failed",
                        connection -> "committed"
                )
        );

        assertEquals("close failed", thrown.getCause().getMessage());
        assertEquals(1, state.commitCalls);
        assertEquals(0, state.rollbackCalls);
        assertEquals(1, state.autoCommitResetCalls);
        assertEquals(1, state.closeCalls);
    }

    @Test
    void rollsBackErrorsAndPreservesCleanupFailures() {
        ConnectionState state = new ConnectionState();
        state.failRollback = true;
        state.failAutoCommitReset = true;
        AssertionError failure = new AssertionError("transaction work failed");

        AssertionError thrown = assertThrows(
                AssertionError.class,
                () -> JdbcTransactionSupport.execute(
                        dataSource(state.connection()),
                        "Transaction failed",
                        connection -> {
                            throw failure;
                        }
                )
        );

        assertSame(failure, thrown);
        assertEquals(1, state.rollbackCalls);
        assertEquals(1, state.autoCommitResetCalls);
        assertEquals(1, state.closeCalls);
        assertEquals(2, thrown.getSuppressed().length);
        assertInstanceOf(SQLException.class, thrown.getSuppressed()[0]);
        assertEquals("rollback failed", thrown.getSuppressed()[0].getMessage());
        assertInstanceOf(SQLException.class, thrown.getSuppressed()[1]);
        assertEquals("auto-commit reset failed", thrown.getSuppressed()[1].getMessage());
    }

    @Test
    void returnsCommittedResultWhenAutoCommitResetFails() {
        ConnectionState state = new ConnectionState();
        state.failAutoCommitReset = true;

        String result = JdbcTransactionSupport.execute(
                dataSource(state.connection()),
                "Transaction failed",
                connection -> "committed"
        );

        assertEquals("committed", result);
        assertEquals(1, state.commitCalls);
        assertEquals(0, state.rollbackCalls);
        assertEquals(1, state.autoCommitResetCalls);
        assertEquals(1, state.closeCalls);
    }

    @Test
    void singleUpdateGuardsAcceptExactlyOneChangedRow() throws SQLException {
        assertTrue(JdbcTransactionSupport.updatedOne(1));
        assertFalse(JdbcTransactionSupport.updatedOne(0));
        assertFalse(JdbcTransactionSupport.updatedOne(2));
        assertFalse(JdbcTransactionSupport.updatedOne(Statement.SUCCESS_NO_INFO));

        JdbcTransactionSupport.requireSingleUpdate(1, "wrong count");
        assertThrows(SQLException.class, () -> JdbcTransactionSupport.requireSingleUpdate(0, "wrong count"));
        assertThrows(SQLException.class, () -> JdbcTransactionSupport.requireSingleUpdate(2, "wrong count"));
    }

    @Test
    void optionalSingleUpdateAcceptsZeroOrOneRowOnly() throws SQLException {
        JdbcTransactionSupport.requireOptionalSingleUpdate(0, "wrong count");
        JdbcTransactionSupport.requireOptionalSingleUpdate(1, "wrong count");

        assertThrows(
                SQLException.class,
                () -> JdbcTransactionSupport.requireOptionalSingleUpdate(-1, "wrong count")
        );
        assertThrows(
                SQLException.class,
                () -> JdbcTransactionSupport.requireOptionalSingleUpdate(2, "wrong count")
        );
    }

    @Test
    void strictBatchGuardAcceptsOneOrSuccessNoInfoForEveryExpectedEntry() throws SQLException {
        JdbcTransactionSupport.requireBatchUpdate(
                new int[]{1, Statement.SUCCESS_NO_INFO, 1},
                3,
                "wrong batch"
        );

        assertThrows(SQLException.class, () -> JdbcTransactionSupport.requireBatchUpdate(
                new int[]{1, Statement.SUCCESS_NO_INFO},
                3,
                "wrong batch"
        ));
        assertThrows(SQLException.class, () -> JdbcTransactionSupport.requireBatchUpdate(
                new int[]{1, 0, 1},
                3,
                "wrong batch"
        ));
        assertThrows(SQLException.class, () -> JdbcTransactionSupport.requireBatchUpdate(
                new int[]{1, Statement.EXECUTE_FAILED, 1},
                3,
                "wrong batch"
        ));
    }

    @Test
    void idempotentBatchGuardAlsoAcceptsNoChangeEntries() throws SQLException {
        JdbcTransactionSupport.requireIdempotentBatchUpdate(
                new int[]{1, 0, Statement.SUCCESS_NO_INFO},
                3,
                "wrong batch"
        );

        assertThrows(SQLException.class, () -> JdbcTransactionSupport.requireIdempotentBatchUpdate(
                new int[]{1, Statement.EXECUTE_FAILED, 0},
                3,
                "wrong batch"
        ));
    }

    private static DataSource dataSource(Connection connection) {
        return proxy(DataSource.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "getConnection" -> connection;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                handler
        ));
    }

    private static final class ConnectionState implements InvocationHandler {
        private boolean failAutoCommitDisable;
        private boolean failCommit;
        private boolean failRollback;
        private boolean failAutoCommitReset;
        private boolean failClose;
        private int autoCommitDisableCalls;
        private int rollbackCalls;
        private int commitCalls;
        private int autoCommitResetCalls;
        private int closeCalls;

        private Connection connection() {
            return proxy(Connection.class, this);
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) throws SQLException {
            return switch (method.getName()) {
                case "setAutoCommit" -> setAutoCommit((boolean) arguments[0]);
                case "commit" -> commit();
                case "rollback" -> rollback();
                case "close" -> close();
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private Object setAutoCommit(boolean enabled) throws SQLException {
            if (enabled) {
                autoCommitResetCalls++;
                if (failAutoCommitReset) {
                    throw new SQLException("auto-commit reset failed");
                }
            } else {
                autoCommitDisableCalls++;
                if (failAutoCommitDisable) {
                    throw new SQLException("auto-commit disable failed");
                }
            }
            return null;
        }

        private Object commit() throws SQLException {
            commitCalls++;
            if (failCommit) {
                throw new SQLException("commit failed");
            }
            return null;
        }

        private Object rollback() throws SQLException {
            rollbackCalls++;
            if (failRollback) {
                throw new SQLException("rollback failed");
            }
            return null;
        }

        private Object close() throws SQLException {
            closeCalls++;
            if (failClose) {
                throw new SQLException("close failed");
            }
            return null;
        }
    }
}
