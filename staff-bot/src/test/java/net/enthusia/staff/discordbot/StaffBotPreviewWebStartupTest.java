package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaffBotPreviewWebStartupTest {
    @TempDir
    Path tempDir;

    @Test
    void dedicatedHostCliCarriesLocalWebBindAndPublicOriginWithoutEnvironmentVariables() throws Exception {
        Path tokenFile = tempDir.resolve("staging-bot-token.txt");
        Files.writeString(tokenFile, "preview-test-token\n");
        StaffBotCommandLine commandLine = StaffBotCommandLine.parse(new String[] {
                "--staging-ui-preview",
                "--token-file=" + tokenFile,
                "--preview-web-bind=127.0.0.1:8766",
                "--preview-public-url=http://127.0.0.1:8766"
        });

        StaffBotConfiguration configuration = StaffBotConfiguration.fromStartup(commandLine, Map.of());

        assertTrue(configuration.uiPreviewEnabled());
        assertEquals(8765, configuration.healthAddress().getPort());
        assertEquals(8766, configuration.previewWebConfig().bindAddress().getPort());
        assertEquals(
                "http://127.0.0.1:8766",
                configuration.previewWebConfig().publicBaseUri().orElseThrow().toString());
        assertFalse(configuration.previewWebConfig().hostedExternally());
    }

    @Test
    void cloudflareOriginNeedsNoLocalWebListener() throws Exception {
        Path tokenFile = tempDir.resolve("staging-bot-token.txt");
        Files.writeString(tokenFile, "preview-test-token\n");
        StaffBotCommandLine commandLine = StaffBotCommandLine.parse(new String[] {
                "--staging-ui-preview",
                "--token-file=" + tokenFile,
                "--preview-public-url=https://staff-staging.enthusia.info"
        });

        StaffBotConfiguration configuration = StaffBotConfiguration.fromStartup(commandLine, Map.of());

        assertEquals(0, configuration.previewWebConfig().bindAddress().getPort());
        assertEquals(
                "https://staff-staging.enthusia.info",
                configuration.previewWebConfig().publicBaseUri().orElseThrow().toString());
        assertTrue(configuration.previewWebConfig().hostedExternally());
        assertTrue(configuration.previewWebConfig().secureCookie());
    }
}
