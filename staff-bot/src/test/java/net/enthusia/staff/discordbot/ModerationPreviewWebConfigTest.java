package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModerationPreviewWebConfigTest {
    @Test
    void defaultsToEphemeralLoopbackWithoutPublicLauncher() {
        ModerationPreviewWebConfig config = ModerationPreviewWebConfig.fromEnvironment(Map.of());

        assertEquals("127.0.0.1", config.bindAddress().getHostString());
        assertEquals(0, config.bindAddress().getPort());
        assertTrue(config.publicBaseUri().isEmpty());
        assertFalse(config.secureCookie());
    }

    @Test
    void publicDeploymentRequiresFixedBindAndHttps() {
        Map<String, String> valid = Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "127.0.0.1:8765",
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, "https://staff-preview.example.test");
        ModerationPreviewWebConfig config = ModerationPreviewWebConfig.fromEnvironment(valid);

        assertEquals(8765, config.bindAddress().getPort());
        assertEquals("https://staff-preview.example.test", config.publicBaseUri().orElseThrow().toString());
        assertTrue(config.secureCookie());

        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, "https://staff-preview.example.test")));
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "127.0.0.1:8765",
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, "http://staff-preview.example.test")));
    }

    @Test
    void loopbackHttpIsAllowedForLocalStagingDevelopment() {
        ModerationPreviewWebConfig config = ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "127.0.0.1:8765",
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, "http://127.0.0.1:8765"));

        assertFalse(config.secureCookie());
    }
}
