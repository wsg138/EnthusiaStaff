package net.enthusia.staff.paper.commandbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaperCommandBridgeConfigurationTest {
    private static final String SECRET = "p".repeat(40);

    @Test
    void absentConfigurationLeavesEndpointDisabled() {
        assertTrue(PaperCommandBridgeConfiguration.fromEnvironment(Map.of()).isEmpty());
    }

    @Test
    void enabledConfigurationBuildsExplicitRulesAndRedactsCredential() {
        Map<String, String> values = new HashMap<>();
        values.put(PaperCommandBridgeConfiguration.ENABLED_ENV, "true");
        values.put(PaperCommandBridgeConfiguration.CREDENTIAL_ENV, SECRET);
        values.put(PaperCommandBridgeConfiguration.RULES_ENV,
                "list|HELPER|minecraft.command.list|0;kick|MOD|minecraft.command.kick|2");

        PaperCommandBridgeConfiguration configuration =
                PaperCommandBridgeConfiguration.fromEnvironment(values).orElseThrow();

        assertEquals("127.0.0.1", configuration.bindHost());
        assertEquals(8772, configuration.port());
        assertEquals(2, configuration.rules().size());
        assertFalse(configuration.toString().contains(SECRET));
    }

    @Test
    void orphanSettingsWeakSecretsAndSystemAuthorityFailClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                PaperCommandBridgeConfiguration.fromEnvironment(Map.of(
                        PaperCommandBridgeConfiguration.CREDENTIAL_ENV, SECRET
                )));
        assertThrows(IllegalArgumentException.class, () ->
                PaperCommandBridgeConfiguration.fromEnvironment(Map.of(
                        PaperCommandBridgeConfiguration.ENABLED_ENV, "true",
                        PaperCommandBridgeConfiguration.CREDENTIAL_ENV, "weak",
                        PaperCommandBridgeConfiguration.RULES_ENV,
                        "list|HELPER|minecraft.command.list|0"
                )));
        assertThrows(IllegalArgumentException.class, () ->
                PaperCommandBridgeConfiguration.fromEnvironment(Map.of(
                        PaperCommandBridgeConfiguration.ENABLED_ENV, "true",
                        PaperCommandBridgeConfiguration.CREDENTIAL_ENV, SECRET,
                        PaperCommandBridgeConfiguration.RULES_ENV,
                        "stop|SYSTEM|minecraft.command.stop|0"
                )));
    }
}
