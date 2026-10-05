package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiscordRoleSyncWriterConfigurationTest {
    private static final String READ_PASSWORD = "r".repeat(40);
    private static final String WRITE_PASSWORD = "w".repeat(40);
    private static final String AUTHORITY_SECRET = "a".repeat(40);
    private static final String COMPONENT_SECRET = "c".repeat(40);

    @Test
    void roleSyncRequiresSeparateWritePrincipal() {
        Map<String, String> values = complete();
        values.put(DiscordRoleSyncConfiguration.MAPPINGS_ENV, "mod=1002");

        assertThrows(IllegalArgumentException.class,
                () -> StaffModerationConfiguration.fromEnvironment(values));

        values.put(StaffModerationConfiguration.ROLE_SYNC_DB_USERNAME_ENV, "role_sync_writer");
        values.put(StaffModerationConfiguration.ROLE_SYNC_DB_CREDENTIAL_ENV, WRITE_PASSWORD);
        StaffModerationConfiguration configuration = StaffModerationConfiguration.fromEnvironment(values).orElseThrow();

        assertTrue(configuration.roleSync().isPresent());
        assertEquals("readonly", configuration.database().username());
        assertEquals("role_sync_writer", configuration.roleSyncDatabase().orElseThrow().username());
        assertFalse(configuration.toString().contains(READ_PASSWORD));
        assertFalse(configuration.toString().contains(WRITE_PASSWORD));
    }

    @Test
    void orphanWriterCredentialsFailClosed() {
        Map<String, String> values = complete();
        values.put(StaffModerationConfiguration.ROLE_SYNC_DB_USERNAME_ENV, "role_sync_writer");
        values.put(StaffModerationConfiguration.ROLE_SYNC_DB_CREDENTIAL_ENV, WRITE_PASSWORD);

        assertThrows(IllegalArgumentException.class,
                () -> StaffModerationConfiguration.fromEnvironment(values));
    }

    private static Map<String, String> complete() {
        Map<String, String> values = new HashMap<>();
        values.put(StaffModerationConfiguration.JDBC_URL_ENV, "jdbc:mariadb://localhost/enthusia");
        values.put(StaffModerationConfiguration.DB_USERNAME_ENV, "readonly");
        values.put(StaffModerationConfiguration.DB_CREDENTIAL_ENV, READ_PASSWORD);
        values.put(StaffModerationConfiguration.AUTHORITY_URL_ENV, "http://127.0.0.1:8771/v1/staff-rank");
        values.put(StaffModerationConfiguration.AUTHORITY_CREDENTIAL_ENV, AUTHORITY_SECRET);
        values.put(StaffModerationConfiguration.COMPONENT_SIGNING_ENV, COMPONENT_SECRET);
        return values;
    }
}
