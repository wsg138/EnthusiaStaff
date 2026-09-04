package net.enthusia.staff.authoritybridge;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import net.enthusia.staff.persistence.DatabaseConfig;

/** Optional runtime-only database configuration for the owner-authorized transition collector. */
final class TransitionCollectorConfiguration {
    static final String FILE_NAME = "collector.properties";
    private static final String JDBC_URL = "db.jdbc-url";
    private static final String DB_USERNAME = "db.username";
    private static final String DB_PASSWORD = "db.password";
    private static final String DB_POOL_SIZE = "db.pool-size";
    private static final String DB_TIMEOUT = "db.timeout-millis";
    private static final String SERVER_ID = "collector.server-id";
    private static final String INTERVAL_SECONDS = "collector.interval-seconds";
    private static final Set<String> ALLOWED = Set.of(
            JDBC_URL, DB_USERNAME, DB_PASSWORD, DB_POOL_SIZE, DB_TIMEOUT, SERVER_ID, INTERVAL_SECONDS);
    private static final Pattern SAFE_SERVER_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final int DEFAULT_POOL_SIZE = 2;
    private static final int DEFAULT_TIMEOUT_MILLIS = 3_000;
    private static final int DEFAULT_INTERVAL_SECONDS = 60;

    private TransitionCollectorConfiguration() {
    }

    static java.util.Optional<Value> loadIfPresent(Path dataFolder) {
        if (dataFolder == null) {
            throw new IllegalArgumentException("data folder must be present");
        }
        Path file = dataFolder.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return java.util.Optional.empty();
        }
        Properties properties = load(file);
        rejectUnknown(properties);
        DatabaseConfig database = new DatabaseConfig(
                required(properties, JDBC_URL),
                required(properties, DB_USERNAME),
                required(properties, DB_PASSWORD),
                boundedInt(properties, DB_POOL_SIZE, DEFAULT_POOL_SIZE, 2, 8),
                boundedInt(properties, DB_TIMEOUT, DEFAULT_TIMEOUT_MILLIS, 250, 60_000));
        String serverId = properties.getProperty(SERVER_ID, "SMP").trim();
        if (!SAFE_SERVER_ID.matcher(serverId).matches()) {
            throw new IllegalArgumentException("collector server id is invalid");
        }
        int seconds = boundedInt(properties, INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS, 30, 3_600);
        return java.util.Optional.of(new Value(database, serverId, Duration.ofSeconds(seconds)));
    }

    private static Properties load(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalArgumentException("transition collector configuration is unavailable", exception);
        }
    }

    private static void rejectUnknown(Properties properties) {
        if (!ALLOWED.containsAll(properties.stringPropertyNames())) {
            throw new IllegalArgumentException("transition collector configuration contains unsupported properties");
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("required transition collector property is missing");
        }
        return value.trim();
    }

    private static int boundedInt(Properties properties, String key, int fallback, int min, int max) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                throw new IllegalArgumentException("transition collector numeric property is outside its safe range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("transition collector numeric property must be an integer", exception);
        }
    }

    record Value(DatabaseConfig database, String serverId, Duration interval) {
        @Override
        public String toString() {
            return "TransitionCollectorConfiguration.Value[database=<redacted>, serverId="
                    + serverId + ", interval=" + interval + "]";
        }
    }
}
