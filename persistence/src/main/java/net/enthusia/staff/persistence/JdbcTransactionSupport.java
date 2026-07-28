package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

final class JdbcTransactionSupport {
    private static final int SINGLE_ROW = 1;

    private JdbcTransactionSupport() {
    }

    static <T> T execute(
            DataSource dataSource,
            String failureMessage,
            TransactionWork<T> work
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            Throwable transactionFailure = null;
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error exception) {
                transactionFailure = exception;
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection, transactionFailure);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException(failureMessage, exception);
        }
    }

    static boolean updatedOne(int updateCount) {
        return updateCount == SINGLE_ROW;
    }

    static void requireSingleUpdate(int updateCount, String message) throws SQLException {
        if (!updatedOne(updateCount)) {
            throw new SQLException(message);
        }
    }

    static void requireOptionalSingleUpdate(int updateCount, String message) throws SQLException {
        if (updateCount < 0 || updateCount > SINGLE_ROW) {
            throw new SQLException(message);
        }
    }

    static void requireBatchUpdate(
            int[] updateCounts,
            int expectedCount,
            String message
    ) throws SQLException {
        requireBatchUpdate(updateCounts, expectedCount, message, false);
    }

    static void requireIdempotentBatchUpdate(
            int[] updateCounts,
            int expectedCount,
            String message
    ) throws SQLException {
        requireBatchUpdate(updateCounts, expectedCount, message, true);
    }

    private static void requireBatchUpdate(
            int[] updateCounts,
            int expectedCount,
            String message,
            boolean allowNoChange
    ) throws SQLException {
        if (updateCounts.length != expectedCount) {
            throw new SQLException(message);
        }
        for (int updateCount : updateCounts) {
            boolean accepted = updatedOne(updateCount)
                    || updateCount == Statement.SUCCESS_NO_INFO
                    || (allowNoChange && updateCount == 0);
            if (!accepted) {
                throw new SQLException(message);
            }
        }
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection, Throwable transactionFailure) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException resetFailure) {
            if (transactionFailure != null) {
                transactionFailure.addSuppressed(resetFailure);
            }
        }
    }

    @FunctionalInterface
    interface TransactionWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
