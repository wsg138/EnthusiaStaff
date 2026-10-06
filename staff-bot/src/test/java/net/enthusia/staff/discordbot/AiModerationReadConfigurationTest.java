package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiModerationReadConfigurationTest {
    private static final String TOKEN = "t".repeat(32);

    @Test
    void disabledByDefault() {
        assertTrue(AiModerationReadConfiguration.fromEnvironment(Map.of()).isEmpty());
    }

    @Test
    void requiresStrongBearerWhenExplicitlyEnabled() {
        Map<String, String> values = new HashMap<>();
        values.put(AiModerationReadConfiguration.ENABLED_KEY, "true");
        values.put(AiModerationReadConfiguration.BEARER_KEY, "short");

        assertThrows(IllegalArgumentException.class,
                () -> AiModerationReadConfiguration.fromEnvironment(values));
    }

    @Test
    void parsesExplicitPrivateServiceBindingWithoutRenderingSecret() {
        Map<String, String> values = new HashMap<>();
        values.put(AiModerationReadConfiguration.ENABLED_KEY, "true");
        values.put(AiModerationReadConfiguration.HOST_KEY, "0.0.0.0");
        values.put(AiModerationReadConfiguration.PORT_KEY, "28767");
        values.put(AiModerationReadConfiguration.BEARER_KEY, TOKEN);

        Optional<AiModerationReadConfiguration> configured =
                AiModerationReadConfiguration.fromEnvironment(values);
        assertTrue(configured.isPresent());
        assertEquals("0.0.0.0", configured.orElseThrow().host());
        assertEquals(28767, configured.orElseThrow().port());
        assertFalse(configured.orElseThrow().toString().contains(TOKEN));
        assertTrue(configured.orElseThrow().toString().contains("<redacted>"));
    }
}
