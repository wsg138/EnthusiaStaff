package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModerationPreviewWebConfigTest {
    private static final String LOOPBACK_BIND = "127.0.0.1:8766";
    private static final String PUBLIC_ORIGIN = "https://staff-preview.example.test";

    @Test
    void defaultsToEphemeralLoopbackWithoutPublicLauncher() {
        ModerationPreviewWebConfig config = ModerationPreviewWebConfig.fromEnvironment(Map.of());

        assertEquals("127.0.0.1", config.bindAddress().getHostString());
        assertEquals(0, config.bindAddress().getPort());
        assertTrue(config.publicBaseUri().isEmpty());
        assertFalse(config.hostedExternally());
        assertFalse(config.secureCookie());
    }

    @Test
    void externalHttpsOriginUsesCloudflareHostedModeWithoutLocalListener() {
        ModerationPreviewWebConfig config = ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, PUBLIC_ORIGIN));

        assertEquals(0, config.bindAddress().getPort());
        assertEquals(PUBLIC_ORIGIN, config.publicBaseUri().orElseThrow().toString());
        assertTrue(config.hostedExternally());
        assertTrue(config.secureCookie());

        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, "http://staff-preview.example.test")));
    }

    @Test
    void localPublicDevelopmentRequiresFixedLoopbackBind() {
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, "http://127.0.0.1:8766")));
        ModerationPreviewWebConfig config = ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, LOOPBACK_BIND,
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, "http://127.0.0.1:8766"));
        assertFalse(config.hostedExternally());
        assertFalse(config.secureCookie());
    }

    @Test
    void bindUsesExplicitLoopbackAllowlistWithoutDnsResolution() {
        ModerationPreviewWebConfig localhost = ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "localhost:8766"));
        ModerationPreviewWebConfig ipv6 = ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "[::1]:8766"));

        assertTrue(localhost.bindAddress().getAddress().isLoopbackAddress());
        assertTrue(ipv6.bindAddress().getAddress().isLoopbackAddress());
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "example.test:8766")));
    }

    @Test
    void explicitBindPortIsRestrictedToDocumentedStagingPort() {
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "127.0.0.1:8765")));
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "127.0.0.1:9000")));
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "localhost:65535")));
    }

    @Test
    void rawNonLoopbackListenerIsRejectedEvenForExternallyHostedOrigin() {
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "0.0.0.0:8766",
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, PUBLIC_ORIGIN)));
    }
}
