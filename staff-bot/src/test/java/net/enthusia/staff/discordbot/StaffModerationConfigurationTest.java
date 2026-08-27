package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaffModerationConfigurationTest {
    private static final String AUTHORITY_SECRET = testSecret('a');
    private static final String COMPONENT_SECRET = testSecret('c');
    private static final String DATABASE_PASSWORD = testSecret('d');

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
        assertFalse(configuration.toString().contains(DATABASE_PASSWORD));
        assertFalse(configuration.toString().contains(AUTHORITY_SECRET));
        assertFalse(configuration.toString().contains(COMPONENT_SECRET));
    }

    @Test
    void rejectsNonLoopbackAuthorityEndpointAndWeakCryptoSecret() {
        Map<String, String> nonLoopback = complete();
        nonLoopback.put(StaffModerationConfiguration.AUTHORITY_URL_KEY, "http://10.0.0.2:8771/v1/staff-rank");
        assertThrows(IllegalArgumentException.class,
                () -> StaffModerationConfiguration.fromEnvironment(nonLoopback));

        Map<String, String> weakSecret = complete();
        weakSecret.put(StaffModerationConfiguration.COMPONENT_SECRET_KEY, Character.toString('w').repeat(8));
        assertThrows(IllegalArgumentException.class,
                () -> StaffModerationConfiguration.fromEnvironment(weakSecret));
    }

    private static Map<String, String> complete() {
        Map<String, String> values = new HashMap<>();
        values.put(StaffModerationConfiguration.JDBC_URL_KEY, "jdbc:mariadb://localhost/enthusia");
        values.put(StaffModerationConfiguration.DB_USERNAME_KEY, "readonly");
        values.put(StaffModerationConfiguration.DB_PASSWORD_KEY, DATABASE_PASSWORD);
        values.put(StaffModerationConfiguration.AUTHORITY_URL_KEY, "http://127.0.0.1:8771/v1/staff-rank");
        values.put(StaffModerationConfiguration.AUTHORITY_SECRET_KEY, AUTHORITY_SECRET);
        values.put(StaffModerationConfiguration.COMPONENT_SECRET_KEY, COMPONENT_SECRET);
        return values;
    }

    private static String testSecret(char marker) {
        return Character.toString(marker).repeat(40);
    }
}
