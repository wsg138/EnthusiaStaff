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
    private static final String MODERATION_FILE_NAME = "staff-bot-runtime.properties";
    private static final String MODERATION_FILE_ARGUMENT = "--moderation-config-file=" + MODERATION_FILE_NAME;
    private static final String TUNNEL_BINARY_NAME = "cloudflared";
    private static final String TUNNEL_BINARY_ARGUMENT = "--tunnel-binary-file=" + TUNNEL_BINARY_NAME;
    private static final String TUNNEL_TOKEN_NAME = "cloudflared-token.txt";
    private static final String TUNNEL_TOKEN_ARGUMENT = "--tunnel-token-file=" + TUNNEL_TOKEN_NAME;
    private static final String WEB_BIND_ARGUMENT = "--preview-web-bind=127.0.0.1:8766";
    private static final String PUBLIC_URL_ARGUMENT = "--preview-public-url=http://127.0.0.1:8766";

    @Test
    void validStagingPreviewCliParsesPanelFiles() {
        StaffBotCommandLine commandLine = StaffBotCommandLine.parse(new String[] {
                PREVIEW_ARGUMENT,
                TOKEN_FILE_ARGUMENT,
                MODERATION_FILE_ARGUMENT,
                TUNNEL_BINARY_ARGUMENT,
                TUNNEL_TOKEN_ARGUMENT,
                WEB_BIND_ARGUMENT,
                PUBLIC_URL_ARGUMENT
        });

        assertTrue(commandLine.stagingUiPreview());
        assertFalse(commandLine.smokeTest());
        assertEquals(Path.of(TOKEN_FILE_NAME), commandLine.tokenFile().orElseThrow());
        assertEquals(Path.of(MODERATION_FILE_NAME), commandLine.moderationConfigFile().orElseThrow());
        StaffBotCommandLine.TunnelFiles tunnel = commandLine.tunnelFiles().orElseThrow();
        assertEquals(Path.of(TUNNEL_BINARY_NAME), tunnel.binaryFile());
        assertEquals(Path.of(TUNNEL_TOKEN_NAME), tunnel.tokenFile());
        assertEquals("127.0.0.1:8766", commandLine.previewWebBind().orElseThrow());
        assertEquals("http://127.0.0.1:8766", commandLine.previewPublicUrl().orElseThrow());
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
        assertTrue(smokeOnly.moderationConfigFile().isEmpty());
        assertTrue(smokeOnly.tunnelFiles().isEmpty());
        assertTrue(previewSmoke.smokeTest());
        assertTrue(previewSmoke.stagingUiPreview());
    }

    @Test
    void tunnelFilesRequireEachOtherModerationConfigurationAndPreview() {
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {
                        PREVIEW_ARGUMENT, TOKEN_FILE_ARGUMENT, MODERATION_FILE_ARGUMENT, TUNNEL_BINARY_ARGUMENT
                }));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {
                        PREVIEW_ARGUMENT, TOKEN_FILE_ARGUMENT, MODERATION_FILE_ARGUMENT, TUNNEL_TOKEN_ARGUMENT
                }));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {
                        PREVIEW_ARGUMENT, TOKEN_FILE_ARGUMENT, TUNNEL_BINARY_ARGUMENT, TUNNEL_TOKEN_ARGUMENT
                }));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {
                        TUNNEL_BINARY_ARGUMENT, TUNNEL_TOKEN_ARGUMENT, MODERATION_FILE_ARGUMENT
                }));
    }

    @Test
    void malformedAndUnknownArgumentsFailSafely() {
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {"--unknown"}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {"--token-file="}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {"--moderation-config-file="}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {"--tunnel-binary-file="}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {"--tunnel-token-file="}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {PREVIEW_ARGUMENT}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {TOKEN_FILE_ARGUMENT}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {MODERATION_FILE_ARGUMENT}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {WEB_BIND_ARGUMENT}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {PUBLIC_URL_ARGUMENT}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotCommandLine.parse(new String[] {SMOKE_TEST_ARGUMENT, SMOKE_TEST_ARGUMENT}));
    }

    @Test
    void renderedCommandLineDoesNotReconstructConfiguredValues() {
        StaffBotCommandLine commandLine = StaffBotCommandLine.parse(new String[] {
                PREVIEW_ARGUMENT,
                "--token-file=private/path/" + TOKEN_FILE_NAME,
                "--moderation-config-file=private/path/" + MODERATION_FILE_NAME,
                "--tunnel-binary-file=private/path/" + TUNNEL_BINARY_NAME,
                "--tunnel-token-file=private/path/" + TUNNEL_TOKEN_NAME,
                WEB_BIND_ARGUMENT,
                PUBLIC_URL_ARGUMENT
        });

        String rendered = commandLine.toString();
        assertFalse(rendered.contains("private/path"));
        assertFalse(rendered.contains(TOKEN_FILE_NAME));
        assertFalse(rendered.contains(MODERATION_FILE_NAME));
        assertFalse(rendered.contains(TUNNEL_BINARY_NAME));
        assertFalse(rendered.contains(TUNNEL_TOKEN_NAME));
        assertFalse(rendered.contains("127.0.0.1:8766"));
        assertTrue(rendered.contains("tokenFile=<configured>"));
        assertTrue(rendered.contains("moderationConfigFile=<configured>"));
        assertTrue(rendered.contains("tunnelBinaryFile=<configured>"));
        assertTrue(rendered.contains("tunnelTokenFile=<configured>"));
    }
}
