package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaffBotFileBackedStartupTest {
    @TempDir
    Path tempDir;

    @Test
    void normalSftpStartupParsesCompleteFileConfiguration() {
        StaffBotCommandLine commandLine = fileBackedCommandLine(Path.of("t"), Path.of("m"));

        assertFalse(commandLine.stagingUiPreview());
        assertTrue(commandLine.fileBackedStaging());
        assertEquals(Path.of("t"), commandLine.tokenFile().orElseThrow());
        assertEquals(Path.of("m"), commandLine.moderationConfigFile().orElseThrow());
        assertTrue(commandLine.tunnelFiles().isEmpty());
    }

    @Test
    void normalSftpStartupReadsTokenWithoutEnablingPreview() throws IOException {
        String token = UUID.randomUUID().toString();
        Path tokenFile = tempDir.resolve("t");
        Files.writeString(tokenFile, token + System.lineSeparator());
        StaffBotCommandLine commandLine = fileBackedCommandLine(tokenFile, tempDir.resolve("m"));

        StaffBotConfiguration configuration = StaffBotConfiguration.fromStartup(commandLine, Map.of());

        assertEquals(StaffBotEnvironment.STAGING, configuration.environment());
        assertEquals(token, configuration.discordToken());
        assertFalse(configuration.uiPreviewEnabled());
        assertFalse(configuration.toString().contains(token));
    }

    @Test
    void fileBackedStartupRejectsProductionProcessConflict() throws IOException {
        Path tokenFile = tempDir.resolve("t");
        Files.writeString(tokenFile, "staging-token");
        StaffBotCommandLine commandLine = fileBackedCommandLine(tokenFile, tempDir.resolve("m"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromStartup(
                        commandLine,
                        Map.of(StaffBotConfiguration.ENVIRONMENT_KEY, "production")));

        assertTrue(exception.getMessage().contains("conflicts"));
    }

    @Test
    void fileBackedStartupAcceptsMatchingStagingProcessEnvironment() throws IOException {
        Path tokenFile = tempDir.resolve("t");
        Files.writeString(tokenFile, "staging-token");
        StaffBotCommandLine commandLine = fileBackedCommandLine(tokenFile, tempDir.resolve("m"));

        StaffBotConfiguration configuration = StaffBotConfiguration.fromStartup(
                commandLine,
                Map.of(StaffBotConfiguration.ENVIRONMENT_KEY, "staging"));

        assertEquals(StaffBotEnvironment.STAGING, configuration.environment());
        assertFalse(configuration.uiPreviewEnabled());
    }

    @Test
    void fileBackedStartupRejectsIncompletePair() {
        assertThrows(IllegalArgumentException.class, () -> StaffBotCommandLine.parse(new String[] {
                "--token-file=t"
        }));
        assertThrows(IllegalArgumentException.class, () -> StaffBotCommandLine.parse(new String[] {
                "--moderation-config-file=m"
        }));
    }

    @Test
    void missingNormalTokenFileFailsWithoutLeakingPath() {
        Path missing = tempDir.resolve("private-token-name");
        StaffBotCommandLine commandLine = fileBackedCommandLine(missing, tempDir.resolve("m"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromStartup(commandLine, Map.of()));

        assertTrue(exception.getMessage().contains("token file"));
        assertFalse(exception.toString().contains(missing.toString()));
    }

    @Test
    void renderedNormalCommandLineRedactsFilePaths() {
        StaffBotCommandLine commandLine = fileBackedCommandLine(
                Path.of("private", "token-file"),
                Path.of("private", "moderation-file"));

        String rendered = commandLine.toString();

        assertFalse(rendered.contains("private"));
        assertFalse(rendered.contains("token-file"));
        assertFalse(rendered.contains("moderation-file"));
        assertTrue(rendered.contains("fileBackedStaging=true"));
        assertTrue(rendered.contains("tokenFile=<configured>"));
        assertTrue(rendered.contains("moderationConfigFile=<configured>"));
    }

    private static StaffBotCommandLine fileBackedCommandLine(Path tokenFile, Path moderationFile) {
        return StaffBotCommandLine.parse(new String[] {
                "--token-file=" + tokenFile,
                "--moderation-config-file=" + moderationFile
        });
    }
}
