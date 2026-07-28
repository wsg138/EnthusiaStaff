package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class JdbcTransactionSupportTest {
    @Test
    void rollsBackErrorsAndPreservesCleanupFailures() {
        ConnectionState state = new ConnectionState(true, true);
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
        ConnectionState state = new ConnectionState(false, true);

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
        private final boolean failRollback;
        private final boolean failAutoCommitReset;
        private int rollbackCalls;
        private int commitCalls;
        private int autoCommitResetCalls;
        private int closeCalls;

        private ConnectionState(boolean failRollback, boolean failAutoCommitReset) {
            this.failRollback = failRollback;
            this.failAutoCommitReset = failAutoCommitReset;
        }

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
            }
            return null;
        }

        private Object commit() {
            commitCalls++;
            return null;
        }

        private Object rollback() throws SQLException {
            rollbackCalls++;
            if (failRollback) {
                throw new SQLException("rollback failed");
            }
            return null;
        }

        private Object close() {
            closeCalls++;
            return null;
        }
    }
}
