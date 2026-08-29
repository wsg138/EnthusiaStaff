package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffBotCommandLineTest {
    @Test
    void validStagingPreviewCliParses() {
        StaffBotCommandLine commandLine = StaffBotCommandLine.parse(new String[] {
                "--staging-ui-preview",
                "--token-file=staging-bot-token.txt"
        });

        assertTrue(commandLine.stagingUiPreview());
        assertFalse(commandLine.smokeTest());
        assertEquals(Path.of("staging-bot-token.txt"), commandLine.tokenFile().orElseThrow());
    }

    @Test
    void smokeTestBehaviorRemainsSupportedAndComposable() {
        StaffBotCommandLine smokeOnly = StaffBotCommandLine.parse(new String[] {"--smoke-test"});
        StaffBotCommandLine previewSmoke = StaffBotCommandLine.parse(new String[] {
                "--token-file=staging-bot-token.txt",
                "--smoke-test",
                "--staging-ui-preview"
        });

        assertTrue(smokeOnly.smokeTest());
        assertFalse(smokeOnly.stagingUiPreview());
        assertTrue(smokeOnly.tokenFile().isEmpty());
        assertTrue(previewSmoke.smokeTest());
        assertTrue(previewSmoke.stagingUiPreview());
    }

    @Test
    void malformedAndUnknownArgumentsFailSafely() {
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {"--unknown"}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {"--token-file="}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {"--token-file"}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {"--staging-ui-preview"}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {"--token-file=staging-bot-token.txt"}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {"--smoke-test", "--smoke-test"}));
    }

    @Test
    void renderedCommandLineDoesNotReconstructTokenFileArgument() {
        StaffBotCommandLine commandLine = StaffBotCommandLine.parse(new String[] {
                "--staging-ui-preview",
                "--token-file=private/path/staging-bot-token.txt"
        });

        assertFalse(commandLine.toString().contains("private/path"));
        assertFalse(commandLine.toString().contains("staging-bot-token.txt"));
        assertTrue(commandLine.toString().contains("tokenFile=<configured>"));
    }
}
