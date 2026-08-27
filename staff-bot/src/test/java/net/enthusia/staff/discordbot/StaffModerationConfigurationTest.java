package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaffModerationConfigurationTest {
    private static final String AUTHORITY_SECRET = "authority-secret-0123456789-abcdefgh";
    private static final String COMPONENT_SECRET = "component-secret-0123456789-abcdefgh";

    @Test
    void completelyAbsentFeatureConfigurationLeavesD05RuntimeUnchanged() {
        assertTrue(StaffModerationConfiguration.fromEnvironment(Map.of()).isEmpty());
    }

    @Test
    void partialConfigurationFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> StaffModerationConfiguration.fromEnvironment(Map.of(
                StaffModerationConfiguration.JDBC_URL_KEY, "jdbc:mariadb://localhost/enthusia"
        )));
    }

    @Test
    void acceptsOnlyCompleteLoopbackAuthorityConfigurationAndRedactsSecrets() {
        Map<String, String> values = complete();
        StaffModerationConfiguration configuration = StaffModerationConfiguration.fromEnvironment(values).orElseThrow();

        assertTrue(configuration.authorityUri().toString().startsWith("http://127.0.0.1:"));
        assertFalse(configuration.toString().contains("db-secret"));
        assertFalse(configuration.toString().contains(AUTHORITY_SECRET));
        assertFalse(configuration.toString().contains(COMPONENT_SECRET));
    }

    @Test
    void rejectsNonLoopbackAuthorityEndpointAndWeakCryptoSecret() {
        Map<String, String> values = complete();
        values.put(StaffModerationConfiguration.AUTHORITY_URL_KEY, "http://10.0.0.2:8771/v1/staff-rank");
        assertThrows(IllegalArgumentException.class, () -> StaffModerationConfiguration.fromEnvironment(values));

        values = complete();
        values.put(StaffModerationConfiguration.COMPONENT_SECRET_KEY, "too-short");
        assertThrows(IllegalArgumentException.class, () -> StaffModerationConfiguration.fromEnvironment(values));
    }

    private static Map<String, String> complete() {
        Map<String, String> values = new HashMap<>();
        values.put(StaffModerationConfiguration.JDBC_URL_KEY, "jdbc:mariadb://localhost/enthusia");
        values.put(StaffModerationConfiguration.DB_USERNAME_KEY, "readonly");
        values.put(StaffModerationConfiguration.DB_PASSWORD_KEY, "db-secret");
        values.put(StaffModerationConfiguration.AUTHORITY_URL_KEY, "http://127.0.0.1:8771/v1/staff-rank");
        values.put(StaffModerationConfiguration.AUTHORITY_SECRET_KEY, AUTHORITY_SECRET);
        values.put(StaffModerationConfiguration.COMPONENT_SECRET_KEY, COMPONENT_SECRET);
        return values;
    }
}
