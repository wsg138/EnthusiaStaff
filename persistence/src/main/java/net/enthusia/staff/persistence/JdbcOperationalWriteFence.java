package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import net.enthusia.staff.domain.OperationalMode;

final class JdbcOperationalWriteFence {
    private JdbcOperationalWriteFence() {
    }

    static boolean authoritativeWritesAllowed(Connection connection) throws SQLException {
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
            OperationalMode mode;
            try {
                mode = OperationalMode.valueOf(result.getString("mode"));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new SQLException("unknown persisted operational mode while fencing authoritative write", exception);
            }
            if (result.next()) {
                throw new SQLException("multiple operational state singleton rows found");
            }
            return mode.destructiveWritesAllowed();
        }
    }

    static void requireAuthoritativeWrites(Connection connection) throws SQLException {
        if (!authoritativeWritesAllowed(connection)) {
            throw new SQLException("authoritative moderation writes are disabled by the operational mode");
        }
    }
}
