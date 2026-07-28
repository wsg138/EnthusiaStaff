package net.enthusia.staff.integration;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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

    private static String jdbcUrl(MariaDBContainer<?> database) {
        return database.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:");
    }
}
