package net.enthusia.staff.persistence.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import javax.sql.DataSource;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.persistence.ModerationPersistenceException;

final class AuthoritativeWriteFence {
    private static final ReentrantLock LOCAL_SERIALIZER = new ReentrantLock(true);

    private final DataSource dataSource;

    AuthoritativeWriteFence(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("authoritative write fence data source must be present");
        }
        this.dataSource = dataSource;
    }

    <T> T execute(Supplier<T> operation, Supplier<T> blockedResult) {
        if (operation == null || blockedResult == null) {
            throw new IllegalArgumentException("authoritative write fence callbacks must be present");
        }
        LOCAL_SERIALIZER.lock();
        try {
            return executeSerialized(operation, blockedResult);
        } finally {
            LOCAL_SERIALIZER.unlock();
        }
    }

    private <T> T executeSerialized(Supplier<T> operation, Supplier<T> blockedResult) {
        try (FenceLease lease = new FenceLease(dataSource)) {
            if (!authoritativeWritesAllowed(lease.connection())) {
                return blockedResult.get();
            }
            T result = operation.get();
            lease.markAuthoritativeOperationCompleted();
            return result;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException(
                    "Unable to enforce the authoritative moderation write fence",
                    exception
            );
        }
    }

    private static boolean authoritativeWritesAllowed(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT mode
                FROM operational_state
                WHERE singleton_id = 1
                FOR UPDATE
                """);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("operational state singleton missing while fencing authoritative write");
            }
            OperationalMode mode = parseMode(result.getString("mode"));
            if (result.next()) {
                throw new SQLException("multiple operational state singleton rows found");
            }
            // BOOTSTRAP permits direct persistence initialization and isolated store verification.
            // Runtime command/service guards still prevent external punishment authority before ACTIVE.
            return mode == OperationalMode.BOOTSTRAP || mode.destructiveWritesAllowed();
        }
    }

    private static OperationalMode parseMode(String persistedMode) throws SQLException {
        if (persistedMode == null) {
            throw new SQLException("persisted operational mode is missing while fencing authoritative write");
        }
        try {
            return OperationalMode.valueOf(persistedMode);
        } catch (IllegalArgumentException exception) {
            throw new SQLException("unknown persisted operational mode while fencing authoritative write", exception);
        }
    }

    private static final class FenceLease implements AutoCloseable {
        private final Connection connection;
        private boolean authoritativeOperationCompleted;

        private FenceLease(DataSource dataSource) throws SQLException {
            connection = dataSource.getConnection();
            try {
                connection.setAutoCommit(false);
            } catch (SQLException exception) {
                closeAfterSetupFailure(exception);
                throw exception;
            }
        }

        private Connection connection() {
            return connection;
        }

        private void markAuthoritativeOperationCompleted() {
            authoritativeOperationCompleted = true;
        }

        @Override
        public void close() throws SQLException {
            SQLException cleanupFailure = null;
            try {
                connection.rollback();
            } catch (SQLException exception) {
                cleanupFailure = exception;
            }
            try {
                connection.setAutoCommit(true);
            } catch (SQLException exception) {
                cleanupFailure = combine(cleanupFailure, exception);
            }
            try {
                connection.close();
            } catch (SQLException exception) {
                cleanupFailure = combine(cleanupFailure, exception);
            }
            if (cleanupFailure != null && !authoritativeOperationCompleted) {
                throw cleanupFailure;
            }
            // Once the delegated authoritative transaction reports a committed outcome,
            // do not replace it with an ambiguous cleanup error from this read-only fence.
        }

        private void closeAfterSetupFailure(SQLException setupFailure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                setupFailure.addSuppressed(closeFailure);
            }
        }
    }

    private static SQLException combine(SQLException existing, SQLException additional) {
        if (existing == null) {
            return additional;
        }
        existing.addSuppressed(additional);
        return existing;
    }
}
