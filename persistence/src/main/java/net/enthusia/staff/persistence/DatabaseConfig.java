package net.enthusia.staff.persistence;

public final class DatabaseConfig {
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int maximumPoolSize;
    private final long connectionTimeoutMillis;

    public DatabaseConfig(
            String jdbcUrl,
            String username,
            String password,
            int maximumPoolSize,
            long connectionTimeoutMillis
    ) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mariadb://")) {
            throw new IllegalArgumentException("production JDBC URL must use MariaDB");
        }
        if (username == null || username.isBlank() || password == null) {
            throw new IllegalArgumentException("database credentials must be present");
        }
        if (maximumPoolSize < 1 || maximumPoolSize > 32) {
            throw new IllegalArgumentException("maximumPoolSize must be between 1 and 32");
        }
        if (connectionTimeoutMillis < 250 || connectionTimeoutMillis > 60_000) {
            throw new IllegalArgumentException("connection timeout must be between 250 and 60000 ms");
        }
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.maximumPoolSize = maximumPoolSize;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public int maximumPoolSize() {
        return maximumPoolSize;
    }

    public long connectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }
}
