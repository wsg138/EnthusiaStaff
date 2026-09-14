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

/** Loads the panel-friendly D06/D16/D13 runtime properties without logging values. */
final class StaffModerationConfigFile {
    static final String JDBC_URL_PROPERTY = "db.jdbc-url";
    static final String DB_USERNAME_PROPERTY = "db.username";
    static final String DB_CREDENTIAL_PROPERTY = "db.password";
    static final String AUTHORITY_URL_PROPERTY = "authority.url";
    static final String AUTHORITY_CREDENTIAL_PROPERTY = "authority.secret";
    static final String AUTHORITY_TRANSPORT_PROPERTY = "authority.transport";
    static final String COMPONENT_CREDENTIAL_PROPERTY = "component.secret";
    static final String DB_POOL_SIZE_PROPERTY = "db.pool-size";
    static final String DB_TIMEOUT_MILLIS_PROPERTY = "db.timeout-millis";
    static final String ROLE_SYNC_MAPPINGS_PROPERTY = "role-sync.mappings";
    static final String ROLE_SYNC_PROTECTED_ROLES_PROPERTY = "role-sync.protected-role-ids";
    static final String ROLE_SYNC_MODE_PROPERTY = "role-sync.mode";
    static final String ROLE_SYNC_INTERVAL_PROPERTY = "role-sync.interval-seconds";
    static final String ROLE_SYNC_BATCH_SIZE_PROPERTY = "role-sync.batch-size";

    private static final Map<String, String> PROPERTY_TO_ENV = Map.ofEntries(
            Map.entry(JDBC_URL_PROPERTY, StaffModerationConfiguration.JDBC_URL_ENV),
            Map.entry(DB_USERNAME_PROPERTY, StaffModerationConfiguration.DB_USERNAME_ENV),
            Map.entry(DB_CREDENTIAL_PROPERTY, StaffModerationConfiguration.DB_CREDENTIAL_ENV),
            Map.entry(AUTHORITY_URL_PROPERTY, StaffModerationConfiguration.AUTHORITY_URL_ENV),
            Map.entry(AUTHORITY_CREDENTIAL_PROPERTY, StaffModerationConfiguration.AUTHORITY_CREDENTIAL_ENV),
            Map.entry(AUTHORITY_TRANSPORT_PROPERTY, StaffModerationConfiguration.AUTHORITY_TRANSPORT_ENV),
            Map.entry(COMPONENT_CREDENTIAL_PROPERTY, StaffModerationConfiguration.COMPONENT_SIGNING_ENV),
            Map.entry(DB_POOL_SIZE_PROPERTY, StaffModerationConfiguration.DB_POOL_SIZE_ENV),
            Map.entry(DB_TIMEOUT_MILLIS_PROPERTY, StaffModerationConfiguration.DB_TIMEOUT_MILLIS_ENV),
            Map.entry(ROLE_SYNC_MAPPINGS_PROPERTY, DiscordRoleSyncConfiguration.MAPPINGS_ENV),
            Map.entry(ROLE_SYNC_PROTECTED_ROLES_PROPERTY, DiscordRoleSyncConfiguration.PROTECTED_ROLES_ENV),
            Map.entry(ROLE_SYNC_MODE_PROPERTY, DiscordRoleSyncConfiguration.MODE_ENV),
            Map.entry(ROLE_SYNC_INTERVAL_PROPERTY, DiscordRoleSyncConfiguration.INTERVAL_SECONDS_ENV),
            Map.entry(ROLE_SYNC_BATCH_SIZE_PROPERTY, DiscordRoleSyncConfiguration.BATCH_SIZE_ENV)
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
