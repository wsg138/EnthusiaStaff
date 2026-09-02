package net.enthusia.staff.paper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import net.enthusia.staff.persistence.DatabaseConfig;
import org.bukkit.configuration.file.FileConfiguration;

final class PaperDatabaseConfiguration {
    static final String DEFAULT_CREDENTIALS_FILE = "plugins/EnthusiaStaff/database.properties";
    static final String JDBC_URL_PROPERTY = "db.jdbc-url";
    static final String DB_USERNAME_PROPERTY = "db.username";
    static final String DB_CREDENTIAL_PROPERTY = "db.password";

    private static final Set<String> ALLOWED_PROPERTIES = Set.of(
            JDBC_URL_PROPERTY,
            DB_USERNAME_PROPERTY,
            DB_CREDENTIAL_PROPERTY
    );

    private final Settings settings;
    private final Function<String, String> environment;

    PaperDatabaseConfiguration(Settings settings, Function<String, String> environment) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    static Settings snapshot(FileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new Settings(
                configuration.getString("storage.jdbc-url-environment"),
                configuration.getString("storage.username-environment"),
                configuration.getString("storage.password-environment"),
                configuration.getString("storage.credentials-file", DEFAULT_CREDENTIALS_FILE),
                configuration.getInt("storage.maximum-pool-size", 8),
                configuration.getLong("storage.connection-timeout-millis", 5_000)
        );
    }

    Optional<DatabaseConfig> load() {
        Optional<DatabaseConfig> configuredEnvironment = fromEnvironment();
        if (configuredEnvironment.isPresent()) {
            return configuredEnvironment;
        }
        Path credentialsFile = credentialsFile();
        if (credentialsFile == null || !Files.exists(credentialsFile)) {
            return Optional.empty();
        }
        return Optional.of(fromFile(credentialsFile));
    }

    private Optional<DatabaseConfig> fromEnvironment() {
        String url = environment(settings.jdbcUrlEnvironment());
        String username = environment(settings.usernameEnvironment());
        String credential = environment(settings.passwordEnvironment());
        int present = countPresent(url, username, credential);
        if (present == 0) {
            return Optional.empty();
        }
        if (present != 3) {
            throw new IllegalArgumentException("database environment configuration is incomplete");
        }
        return Optional.of(database(url, username, credential));
    }

    private DatabaseConfig fromFile(Path file) {
        Properties properties = loadProperties(file);
        if (!ALLOWED_PROPERTIES.containsAll(properties.stringPropertyNames())) {
            throw new IllegalArgumentException("database credentials file contains unsupported properties");
        }
        return database(
                required(properties.getProperty(JDBC_URL_PROPERTY), JDBC_URL_PROPERTY),
                required(properties.getProperty(DB_USERNAME_PROPERTY), DB_USERNAME_PROPERTY),
                required(properties.getProperty(DB_CREDENTIAL_PROPERTY), DB_CREDENTIAL_PROPERTY)
        );
    }

    private DatabaseConfig database(String url, String username, String credential) {
        return new DatabaseConfig(
                url.trim(),
                username.trim(),
                credential,
                settings.maximumPoolSize(),
                settings.connectionTimeoutMillis()
        );
    }

    private Path credentialsFile() {
        String configured = settings.credentialsFile();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        try {
            return Path.of(configured.trim()).normalize();
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("storage credentials file path is invalid", exception);
        }
    }

    private static Properties loadProperties(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalArgumentException("database credentials file is unavailable", exception);
        }
    }

    private String environment(String variable) {
        if (variable == null || variable.isBlank()) {
            return null;
        }
        String value = environment.apply(variable);
        return value == null || value.isBlank() ? null : value;
    }

    private static int countPresent(String... values) {
        int count = 0;
        for (String value : values) {
            if (value != null) {
                count++;
            }
        }
        return count;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    record Settings(
            String jdbcUrlEnvironment,
            String usernameEnvironment,
            String passwordEnvironment,
            String credentialsFile,
            int maximumPoolSize,
            long connectionTimeoutMillis
    ) {
    }
}
