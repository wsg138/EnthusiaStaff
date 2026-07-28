package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

final class JdbcDiscordRetrySupport {
    private JdbcDiscordRetrySupport() {
    }

    static int execute(
            Connection connection,
            String destination,
            Instant now,
            int maximumMessages
    ) throws SQLException {
        if (!lockDestination(connection, destination)) {
            return 0;
        }
        resetChannel(connection, destination, now);
        return retryMessages(connection, destination, now, maximumMessages);
    }

    private static boolean lockDestination(Connection connection, String destination) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM discord_delivery_channels
                WHERE destination = ? FOR UPDATE
                """)) {
            statement.setString(1, destination);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void resetChannel(Connection connection, String destination, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_delivery_channels
                SET consecutive_failures = 0, open_until = NULL, last_error_code = NULL, updated_at = ?
                WHERE destination = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setString(2, destination);
            statement.executeUpdate();
        }
    }

    private static int retryMessages(
            Connection connection,
            String destination,
            Instant now,
            int maximumMessages
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_outbox
                SET state = 'PENDING', attempt_count = 0, available_at = ?, lease_owner = NULL,
                    lease_until = NULL, last_error_code = NULL
                WHERE destination = ? AND state = 'DEAD_LETTER'
                ORDER BY created_at LIMIT ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setString(2, destination);
            statement.setInt(3, maximumMessages);
            return statement.executeUpdate();
        }
    }
}
