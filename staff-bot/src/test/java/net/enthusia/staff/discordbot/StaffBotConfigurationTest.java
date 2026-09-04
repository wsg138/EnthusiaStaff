package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaffBotConfigurationTest {
    private static final String STAGING = "staging";
    private static final String PRODUCTION = "production";
    private static final String DUMMY_TOKEN = "token";
    private static final String TRUE = "true";

    @TempDir
    Path tempDir;

    @Test
    void stagingUsesFixedIdentityAndRedactsToken() {
        Map<String, String> values = new HashMap<>();
        values.put(StaffBotConfiguration.ENVIRONMENT_KEY, STAGING);
        values.put(StaffBotConfiguration.TOKEN_KEY, "secret-token-value");
        values.put(StaffBotConfiguration.HEALTH_PORT_KEY, "0");

        StaffBotConfiguration configuration = StaffBotConfiguration.fromEnvironment(values);

        assertEquals(StaffBotEnvironment.STAGING, configuration.environment());
        assertEquals(1541279616881397772L, configuration.environment().applicationId());
        assertEquals(1410303324745371709L, configuration.environment().guildId());
        assertEquals(0, configuration.healthAddress().getPort());
        assertFalse(configuration.uiPreviewEnabled());
        assertFalse(configuration.toString().contains("secret-token-value"));
        assertTrue(configuration.toString().contains("discordToken=<redacted>"));
    }

    @Test
    void stagingPreviewCliReadsAndTrimsTokenFile() throws IOException {
        Path tokenFile = writeTokenFile(" \t\r\npreview-secret-token\r\n \t");
        StaffBotConfiguration configuration = StaffBotConfiguration.fromStartup(
                previewCommandLine(tokenFile),
                Map.of());

        assertEquals(StaffBotEnvironment.STAGING, configuration.environment());
        assertTrue(configuration.uiPreviewEnabled());
        assertEquals("preview-secret-token", configuration.discordToken());
        assertEquals(8765, configuration.healthAddress().getPort());
    }

    @Test
    void missingTokenFileFailsAsConfigurationError() {
        Path missing = tempDir.resolve("missing-token.txt");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromStartup(previewCommandLine(missing), Map.of()));

        assertTrue(exception.getMessage().contains("token file"));
        assertFalse(exception.getMessage().contains(missing.toString()));
    }

    @Test
    void emptyTokenFileFailsAsConfigurationError() throws IOException {
        Path tokenFile = writeTokenFile(" \t\r\n ");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromStartup(previewCommandLine(tokenFile), Map.of()));

        assertTrue(exception.getMessage().contains("token file"));
    }

    @Test
    void unreadableTokenFileFailsAsConfigurationError() throws IOException {
        Path tokenDirectory = Files.createDirectory(tempDir.resolve("token-directory"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromStartup(previewCommandLine(tokenDirectory), Map.of()));

        assertTrue(exception.getMessage().contains("token file"));
        assertFalse(exception.getMessage().contains(tokenDirectory.toString()));
    }

    @Test
    void tokenNeverAppearsInRenderedConfigurationOrErrors() throws IOException {
        String token = UUID.randomUUID().toString();
        Path validTokenFile = writeTokenFile(token);
        StaffBotConfiguration configuration = StaffBotConfiguration.fromStartup(
                previewCommandLine(validTokenFile),
                Map.of());

        assertFalse(configuration.toString().contains(token));

        Path invalidTokenFile = tempDir.resolve("invalid-token.txt");
        Files.writeString(invalidTokenFile, token + " invalid");
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromStartup(previewCommandLine(invalidTokenFile), Map.of()));

        assertFalse(exception.toString().contains(token));
    }

    @Test
    void environmentConfigurationStillWorksWithoutCliPreview() {
        StaffBotCommandLine commandLine = StaffBotCommandLine.parse(new String[0]);
        StaffBotConfiguration configuration = StaffBotConfiguration.fromStartup(
                commandLine,
                Map.of(
                        StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                        StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                        StaffBotConfiguration.UI_PREVIEW_KEY, TRUE));

        assertEquals(StaffBotEnvironment.STAGING, configuration.environment());
        assertEquals(DUMMY_TOKEN, configuration.discordToken());
        assertTrue(configuration.uiPreviewEnabled());
    }

    @Test
    void productionEnvironmentCannotBeOverriddenByPreviewCli() throws IOException {
        Path tokenFile = writeTokenFile("staging-preview-token");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromStartup(
                        previewCommandLine(tokenFile),
                        Map.of(StaffBotConfiguration.ENVIRONMENT_KEY, PRODUCTION)));

        assertTrue(exception.getMessage().contains(PRODUCTION));
    }

    @Test
    void previewDedicatedHostNeedsOnlyTokenEnvironmentAndFlag() {
        StaffBotConfiguration configuration = StaffBotConfiguration.fromEnvironment(Map.of(
                StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                StaffBotConfiguration.UI_PREVIEW_KEY, TRUE));

        assertTrue(configuration.uiPreviewEnabled());
        assertEquals(StaffBotEnvironment.STAGING, configuration.environment());
        assertEquals(8765, configuration.healthAddress().getPort());
    }

    @Test
    void previewRequiresExplicitStagingPair() {
        StaffBotConfiguration staging = StaffBotConfiguration.fromEnvironment(Map.of(
                StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                StaffBotConfiguration.UI_PREVIEW_KEY, TRUE,
                StaffBotConfiguration.HEALTH_PORT_KEY, "0"));

        assertTrue(staging.uiPreviewEnabled());
        assertThrows(IllegalArgumentException.class, () -> StaffBotConfiguration.fromEnvironment(Map.of(
                StaffBotConfiguration.ENVIRONMENT_KEY, PRODUCTION,
                StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                StaffBotConfiguration.UI_PREVIEW_KEY, TRUE)));
        assertThrows(IllegalArgumentException.class, () -> StaffBotConfiguration.fromEnvironment(Map.of(
                StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                StaffBotConfiguration.UI_PREVIEW_KEY, "yes")));
    }

    @Test
    void productionRejectsEphemeralHealthPort() {
        Map<String, String> values = new HashMap<>();
        values.put(StaffBotConfiguration.ENVIRONMENT_KEY, PRODUCTION);
        values.put(StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN);
        values.put(StaffBotConfiguration.HEALTH_PORT_KEY, "0");

        assertThrows(IllegalArgumentException.class, () -> StaffBotConfiguration.fromEnvironment(values));
    }

    @Test
    void rejectsMissingSecretAndNonLoopbackHealthBinding() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromEnvironment(Map.of(
                        StaffBotConfiguration.ENVIRONMENT_KEY, STAGING)));

        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromEnvironment(Map.of(
                        StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                        StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                        StaffBotConfiguration.HEALTH_HOST_KEY, "0.0.0.0")));
    }

    @Test
    void validatesNumericBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromEnvironment(Map.of(
                        StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                        StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                        StaffBotConfiguration.WORKER_THREADS_KEY, "0")));
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromEnvironment(Map.of(
                        StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                        StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                        StaffBotConfiguration.INTERACTION_TTL_SECONDS_KEY, "86401")));
    }

    private Path writeTokenFile(String content) throws IOException {
        Path tokenFile = tempDir.resolve("staging-bot-token.txt");
        Files.writeString(tokenFile, content);
        return tokenFile;
    }

    private static StaffBotCommandLine previewCommandLine(Path tokenFile) {
        return StaffBotCommandLine.parse(new String[] {
                "--staging-ui-preview",
                "--token-file=" + tokenFile
        });
    }
}
