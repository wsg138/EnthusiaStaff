package net.enthusia.staff.persistence.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import net.enthusia.staff.persistence.ModerationPersistenceException;

final class MigrationDatabaseLock implements AutoCloseable {
    private static final String LOCK_NAME = "enthusiastaff:litebans-cutover";

    private final Connection connection;

    private MigrationDatabaseLock(Connection connection) {
        this.connection = connection;
    }

    static MigrationDatabaseLock acquire(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("migration lock data source must be present");
        }
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, LOCK_NAME);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        connection.close();
                        throw new ModerationPersistenceException(
                                "Another migration or cutover operation owns the database lock"
                        );
                    }
                }
            }
            return new MigrationDatabaseLock(connection);
        } catch (SQLException exception) {
            closeAfterFailedAcquire(connection, exception);
            throw new ModerationPersistenceException("Unable to acquire the migration database lock", exception);
        }
    }

    @Override
    public void close() {
        SQLException failure = null;
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, LOCK_NAME);
            statement.executeQuery().close();
        } catch (SQLException exception) {
            failure = exception;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw new ModerationPersistenceException("Unable to release the migration database lock", failure);
        }
    }

    void closeAfter(Throwable operationFailure) {
        try {
            close();
        } catch (RuntimeException releaseFailure) {
            if (operationFailure == null) {
                throw releaseFailure;
            }
            operationFailure.addSuppressed(releaseFailure);
        }
    }

    private static void closeAfterFailedAcquire(Connection connection, SQLException original) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            original.addSuppressed(closeFailure);
        }
    }
}
