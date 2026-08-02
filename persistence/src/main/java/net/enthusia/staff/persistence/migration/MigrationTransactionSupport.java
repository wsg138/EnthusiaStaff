package net.enthusia.staff.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import net.enthusia.staff.persistence.ModerationPersistenceException;

final class MigrationTransactionSupport {
    private static final int SINGLE_ROW = 1;

    private MigrationTransactionSupport() {
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

    static void requireSingleUpdate(int updateCount, String message) throws SQLException {
        if (updateCount != SINGLE_ROW) {
            throw new SQLException(message);
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
