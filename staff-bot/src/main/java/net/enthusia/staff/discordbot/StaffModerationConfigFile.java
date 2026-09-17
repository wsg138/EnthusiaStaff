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

/** Loads the allowlisted D06/D07/D16 moderation runtime properties without logging values. */
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
    static final String ENFORCEMENT_ENABLED_PROPERTY = "discord-enforcement.enabled";
    static final String MUTE_ROLE_PROPERTY = "discord-enforcement.mute-role-id";
    static final String SUPPORT_SCOPES_PROPERTY = "discord-enforcement.support-scope-ids";
    static final String SUPPORT_MESSAGE_PROPERTY = "discord-enforcement.support-message";
    static final String HELPER_MAX_MUTE_PROPERTY = "discord-enforcement.helper-max-mute-seconds";
    static final String MOD_MAX_MUTE_PROPERTY = "discord-enforcement.mod-max-mute-seconds";
    static final String MOD_MAX_BAN_PROPERTY = "discord-enforcement.mod-max-ban-seconds";
    static final String MOD_MAX_RESTRICTION_PROPERTY = "discord-enforcement.mod-max-restriction-seconds";
    static final String RECONCILE_SECONDS_PROPERTY = "discord-enforcement.reconcile-seconds";
    static final String WORKER_MILLIS_PROPERTY = "discord-enforcement.worker-millis";

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
            Map.entry(ENFORCEMENT_ENABLED_PROPERTY, DiscordPunishmentConfiguration.ENABLED_ENV),
            Map.entry(MUTE_ROLE_PROPERTY, DiscordPunishmentConfiguration.MUTE_ROLE_ENV),
            Map.entry(SUPPORT_SCOPES_PROPERTY, DiscordPunishmentConfiguration.SUPPORT_SCOPES_ENV),
            Map.entry(SUPPORT_MESSAGE_PROPERTY, DiscordPunishmentConfiguration.SUPPORT_MESSAGE_ENV),
            Map.entry(HELPER_MAX_MUTE_PROPERTY, DiscordPunishmentConfiguration.HELPER_MAX_MUTE_ENV),
            Map.entry(MOD_MAX_MUTE_PROPERTY, DiscordPunishmentConfiguration.MOD_MAX_MUTE_ENV),
            Map.entry(MOD_MAX_BAN_PROPERTY, DiscordPunishmentConfiguration.MOD_MAX_BAN_ENV),
            Map.entry(MOD_MAX_RESTRICTION_PROPERTY, DiscordPunishmentConfiguration.MOD_MAX_RESTRICTION_ENV),
            Map.entry(RECONCILE_SECONDS_PROPERTY, DiscordPunishmentConfiguration.RECONCILE_SECONDS_ENV),
            Map.entry(WORKER_MILLIS_PROPERTY, DiscordPunishmentConfiguration.WORKER_MILLIS_ENV)
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
