package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModerationPreviewWebConfigTest {
    private static final String LOOPBACK_BIND = "127.0.0.1:8765";
    private static final String PUBLIC_ORIGIN = "https://staff-preview.example.test";

    @Test
    void defaultsToEphemeralLoopbackWithoutPublicLauncher() {
        ModerationPreviewWebConfig config = ModerationPreviewWebConfig.fromEnvironment(Map.of());

        assertEquals("127.0.0.1", config.bindAddress().getHostString());
        assertEquals(0, config.bindAddress().getPort());
        assertTrue(config.publicBaseUri().isEmpty());
        assertFalse(config.secureCookie());
    }

    @Test
    void publicDeploymentRequiresFixedLoopbackBindAndHttps() {
        Map<String, String> valid = Map.of(
                ModerationPreviewWebConfig.BIND_ENV, LOOPBACK_BIND,
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, PUBLIC_ORIGIN);
        ModerationPreviewWebConfig config = ModerationPreviewWebConfig.fromEnvironment(valid);

        assertEquals(8765, config.bindAddress().getPort());
        assertEquals(PUBLIC_ORIGIN, config.publicBaseUri().orElseThrow().toString());
        assertTrue(config.secureCookie());

        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, PUBLIC_ORIGIN)));
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, LOOPBACK_BIND,
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, "http://staff-preview.example.test")));
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "0.0.0.0:8765",
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, PUBLIC_ORIGIN)));
    }

    @Test
    void bindUsesExplicitLoopbackAllowlistWithoutDnsResolution() {
        ModerationPreviewWebConfig localhost = ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "localhost:8765"));
        ModerationPreviewWebConfig ipv6 = ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "[::1]:8765"));

        assertTrue(localhost.bindAddress().getAddress().isLoopbackAddress());
        assertTrue(ipv6.bindAddress().getAddress().isLoopbackAddress());
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "example.test:8765")));
    }

    @Test
    void rawNonLoopbackListenerIsRejectedEvenWithoutPublicLauncher() {
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "0.0.0.0:8765")));
    }

    @Test
    void loopbackHttpIsAllowedForLocalStagingDevelopment() {
        ModerationPreviewWebConfig config = ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, LOOPBACK_BIND,
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, "http://127.0.0.1:8765"));

        assertFalse(config.secureCookie());
    }
}
