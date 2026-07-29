package net.enthusia.staff.integration;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import net.enthusia.staff.persistence.DatabaseConfig;
import org.testcontainers.containers.MariaDBContainer;

final class MariaDbIntegrationSupport {
    private MariaDbIntegrationSupport() {
    }

    static DatabaseConfig databaseConfig(MariaDBContainer<?> database) {
        return new DatabaseConfig(
                jdbcUrl(database),
                database.getUsername(),
                database.getPassword(),
                4,
                5_000
        );
    }

    static Connection connection(MariaDBContainer<?> database) throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(database),
                database.getUsername(),
                database.getPassword()
        );
    }

    static byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    static void insertPlayer(
            MariaDBContainer<?> database,
            UUID playerId,
            String username,
            Instant now
    ) throws SQLException {
        try (Connection connection = connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT IGNORE INTO players(
                         player_id, current_username, lowercase_username, platform,
                         first_seen_at, last_seen_at
                     ) VALUES (?, ?, ?, 'JAVA', ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(playerId));
            statement.setString(2, username);
            statement.setString(3, username.toLowerCase(Locale.ROOT));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    static void insertCase(
            MariaDBContainer<?> database,
            String caseId,
            UUID targetId,
            UUID actorId,
            Instant now
    ) throws SQLException {
        try (Connection connection = connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT IGNORE INTO cases(
                         case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                         public_reason, exact_reason_id, sanction_family, internal_explanation,
                         configuration_version, visibility, state, issued_at
                     ) VALUES (?, ?, ?, ?, 'P2wn', 'OWNER', 'Integration test', 'integration.test',
                         'TEST', 'Asset journal verification', 'integration-test-v1',
                         'PRIVATE', 'OPEN', ?)
                     """)) {
            statement.setString(1, caseId);
            statement.setString(2, "case:test:" + caseId);
            statement.setBytes(3, uuidBytes(targetId));
            statement.setBytes(4, uuidBytes(actorId));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static String jdbcUrl(MariaDBContainer<?> database) {
        return database.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:");
    }
}
