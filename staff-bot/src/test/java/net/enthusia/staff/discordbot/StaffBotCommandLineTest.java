package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffBotCommandLineTest {
    private static final String PREVIEW_ARGUMENT = "--staging-ui-preview";
    private static final String SMOKE_TEST_ARGUMENT = "--smoke-test";
    private static final String TOKEN_FILE_NAME = "staging-bot-token.txt";
    private static final String TOKEN_FILE_ARGUMENT = "--token-file=" + TOKEN_FILE_NAME;

    @Test
    void validStagingPreviewCliParses() {
        StaffBotCommandLine commandLine = StaffBotCommandLine.parse(new String[] {
                PREVIEW_ARGUMENT,
                TOKEN_FILE_ARGUMENT
        });

        assertTrue(commandLine.stagingUiPreview());
        assertFalse(commandLine.smokeTest());
        assertEquals(Path.of(TOKEN_FILE_NAME), commandLine.tokenFile().orElseThrow());
    }

    @Test
    void smokeTestBehaviorRemainsSupportedAndComposable() {
        StaffBotCommandLine smokeOnly = StaffBotCommandLine.parse(new String[] {SMOKE_TEST_ARGUMENT});
        StaffBotCommandLine previewSmoke = StaffBotCommandLine.parse(new String[] {
                TOKEN_FILE_ARGUMENT,
                SMOKE_TEST_ARGUMENT,
                PREVIEW_ARGUMENT
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
                () -> StaffBotCommandLine.parse(new String[] {PREVIEW_ARGUMENT}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {TOKEN_FILE_ARGUMENT}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {SMOKE_TEST_ARGUMENT, SMOKE_TEST_ARGUMENT}));
    }

    @Test
    void renderedCommandLineDoesNotReconstructTokenFileArgument() {
        StaffBotCommandLine commandLine = StaffBotCommandLine.parse(new String[] {
                PREVIEW_ARGUMENT,
                "--token-file=private/path/" + TOKEN_FILE_NAME
        });

        assertFalse(commandLine.toString().contains("private/path"));
        assertFalse(commandLine.toString().contains(TOKEN_FILE_NAME));
        assertTrue(commandLine.toString().contains("tokenFile=<configured>"));
    }
}
