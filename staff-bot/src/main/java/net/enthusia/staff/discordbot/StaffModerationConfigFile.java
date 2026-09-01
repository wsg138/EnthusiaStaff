package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Loads the panel-friendly D06/D16 read-only runtime properties without logging values. */
final class StaffModerationConfigFile {
    static final String JDBC_URL_PROPERTY = "db.jdbc-url";
    static final String DB_USERNAME_PROPERTY = "db.username";
    static final String DB_CREDENTIAL_PROPERTY = "db.password";
    static final String AUTHORITY_URL_PROPERTY = "authority.url";
    static final String AUTHORITY_CREDENTIAL_PROPERTY = "authority.secret";
    static final String COMPONENT_CREDENTIAL_PROPERTY = "component.secret";
    static final String DB_POOL_SIZE_PROPERTY = "db.pool-size";
    static final String DB_TIMEOUT_MILLIS_PROPERTY = "db.timeout-millis";

    private static final Map<String, String> PROPERTY_TO_ENV = Map.of(
            JDBC_URL_PROPERTY, StaffModerationConfiguration.JDBC_URL_ENV,
            DB_USERNAME_PROPERTY, StaffModerationConfiguration.DB_USERNAME_ENV,
            DB_CREDENTIAL_PROPERTY, StaffModerationConfiguration.DB_CREDENTIAL_ENV,
            AUTHORITY_URL_PROPERTY, StaffModerationConfiguration.AUTHORITY_URL_ENV,
            AUTHORITY_CREDENTIAL_PROPERTY, StaffModerationConfiguration.AUTHORITY_CREDENTIAL_ENV,
            COMPONENT_CREDENTIAL_PROPERTY, StaffModerationConfiguration.COMPONENT_SIGNING_ENV,
            DB_POOL_SIZE_PROPERTY, StaffModerationConfiguration.DB_POOL_SIZE_ENV,
            DB_TIMEOUT_MILLIS_PROPERTY, StaffModerationConfiguration.DB_TIMEOUT_MILLIS_ENV
    );
    private static final Set<String> ALLOWED_PROPERTIES = PROPERTY_TO_ENV.keySet();

    private StaffModerationConfigFile() {
    }

    static Map<String, String> read(Path path) {
        Objects.requireNonNull(path, "path");
        Properties properties = load(path);
        if (!ALLOWED_PROPERTIES.containsAll(properties.stringPropertyNames())) {
            throw new IllegalArgumentException("moderation config file contains unsupported properties");
        }
        Map<String, String> values = new HashMap<>();
        PROPERTY_TO_ENV.forEach((property, envName) -> {
            String value = properties.getProperty(property);
            if (value != null) {
                values.put(envName, value);
            }
        });
        return Map.copyOf(values);
    }

    private static Properties load(Path path) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalArgumentException("moderation config file is unavailable", exception);
        }
    }
}
