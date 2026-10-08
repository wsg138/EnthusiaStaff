package net.enthusia.staff.discordbot;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

/** Parsed process arguments for the isolated staff Discord bot. */
final class StaffBotCommandLine {
    private static final String SMOKE_TEST_ARGUMENT = "--smoke-test";
    private static final String STAGING_UI_PREVIEW_ARGUMENT = "--staging-ui-preview";
    private static final String TOKEN_FILE_PREFIX = "--token-file=";
    private static final String ENVIRONMENT_PREFIX = "--environment=";
    private static final String MODERATION_CONFIG_FILE_PREFIX = "--moderation-config-file=";
    private static final String CHAT_SETTINGS_FILE_PREFIX = "--chat-bridge-config-file=";
    private static final String TUNNEL_BINARY_FILE_PREFIX = "--tunnel-binary-file=";
    private static final String TUNNEL_TOKEN_FILE_PREFIX = "--tunnel-token-file=";
    private static final String PREVIEW_WEB_BIND_PREFIX = "--preview-web-bind=";
    private static final String PREVIEW_PUBLIC_URL_PREFIX = "--preview-public-url=";
    private static final String MODERATION_WEB_URL_PREFIX = "--moderation-web-url=";

    private final boolean smokeTest;
    private final boolean stagingUiPreview;
    private final Path tokenFile;
    private final StaffBotEnvironment environment;
    private final Path moderationConfigFile;
    private final Path chatSettingsFile;
    private final Path tunnelBinaryFile;
    private final Path tunnelTokenFile;
    private final String previewWebBind;
    private final String previewPublicUrl;
    private final String moderationWebUrl;

    private StaffBotCommandLine(Parser parser) {
        this.smokeTest = parser.smokeTest;
        this.stagingUiPreview = parser.stagingUiPreview;
        this.tokenFile = parser.tokenFile;
        this.environment = parser.environment;
        this.moderationConfigFile = parser.moderationConfigFile;
        this.chatSettingsFile = parser.chatSettingsFile;
        this.tunnelBinaryFile = parser.tunnelBinaryFile;
        this.tunnelTokenFile = parser.tunnelTokenFile;
        this.previewWebBind = parser.previewWebBind;
        this.previewPublicUrl = parser.previewPublicUrl;
        this.moderationWebUrl = parser.moderationWebUrl;
    }

    static StaffBotCommandLine parse(String[] arguments) {
        if (arguments == null) {
            throw invalidArguments();
        }
        Parser parser = new Parser();
        for (String argument : arguments) {
            if (argument == null) {
                throw invalidArguments();
            }
            parser.accept(argument);
        }
        return parser.finish();
    }

    boolean smokeTest() {
        return smokeTest;
    }

    boolean stagingUiPreview() {
        return stagingUiPreview;
    }

    Optional<Path> tokenFile() {
        return Optional.ofNullable(tokenFile);
    }

    Optional<StaffBotEnvironment> environment() {
        return Optional.ofNullable(environment);
    }

    Optional<Path> moderationConfigFile() {
        return Optional.ofNullable(moderationConfigFile);
    }

    Optional<Path> chatSettingsFile() {
        return Optional.ofNullable(chatSettingsFile);
    }

    boolean fileBackedStartup() {
        return !stagingUiPreview && tokenFile != null && moderationConfigFile != null;
    }

    Optional<TunnelFiles> tunnelFiles() {
        return tunnelBinaryFile == null
                ? Optional.empty()
                : Optional.of(new TunnelFiles(tunnelBinaryFile, tunnelTokenFile));
    }

    Optional<String> previewWebBind() {
        return Optional.ofNullable(previewWebBind);
    }

    Optional<String> previewPublicUrl() {
        return Optional.ofNullable(previewPublicUrl);
    }

    Optional<String> moderationWebUrl() {
        return Optional.ofNullable(moderationWebUrl);
    }

    @Override
    public String toString() {
        return "StaffBotCommandLine[smokeTest=" + smokeTest
                + ", stagingUiPreview=" + stagingUiPreview
                + ", fileBackedStartup=" + fileBackedStartup()
                + ", environment=" + (environment == null ? "<default>" : environment.label())
                + ", tokenFile=" + configured(tokenFile)
                + ", moderationConfigFile=" + configured(moderationConfigFile)
                + ", chatSettingsFile=" + configured(chatSettingsFile)
                + ", tunnelBinaryFile=" + configured(tunnelBinaryFile)
                + ", tunnelTokenFile=" + configured(tunnelTokenFile)
                + ", previewWebBind=" + configured(previewWebBind)
                + ", previewPublicUrl=" + configured(previewPublicUrl)
                + ", moderationWebUrl=" + configured(moderationWebUrl) + "]";
    }

    private static String configured(Object value) {
        return value == null ? "<none>" : "<configured>";
    }

    private static IllegalArgumentException invalidArguments() {
        return new IllegalArgumentException("unsupported or malformed staff bot arguments");
    }

    private static Path parsePath(String value) {
        if (value.isBlank()) {
            throw invalidArguments();
        }
        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            throw invalidArguments();
        }
    }

    private static String parseNonBlank(String value) {
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw invalidArguments();
        }
        return normalized;
    }

    record TunnelFiles(Path binaryFile, Path tokenFile) {
        TunnelFiles {
            if (binaryFile == null || tokenFile == null) {
                throw invalidArguments();
            }
        }
    }

    private static final class Parser {
        private boolean smokeTest;
        private boolean stagingUiPreview;
        private Path tokenFile;
        private StaffBotEnvironment environment;
        private Path moderationConfigFile;
        private Path chatSettingsFile;
        private Path tunnelBinaryFile;
        private Path tunnelTokenFile;
        private String previewWebBind;
        private String previewPublicUrl;
        private String moderationWebUrl;

        private void accept(String argument) {
            if (acceptFlag(argument) || acceptPath(argument) || acceptPreviewValue(argument)) {
                return;
            }
            throw invalidArguments();
        }

        private boolean acceptFlag(String argument) {
            if (SMOKE_TEST_ARGUMENT.equals(argument)) {
                smokeTest = setOnce(smokeTest);
                return true;
            }
            if (STAGING_UI_PREVIEW_ARGUMENT.equals(argument)) {
                stagingUiPreview = setOnce(stagingUiPreview);
                return true;
            }
            return false;
        }

        private boolean acceptPath(String argument) {
            if (argument.startsWith(TOKEN_FILE_PREFIX)) {
                tokenFile = setPathOnce(tokenFile, argument, TOKEN_FILE_PREFIX);
                return true;
            }
            if (argument.startsWith(CHAT_SETTINGS_FILE_PREFIX)) {
                chatSettingsFile = setPathOnce(chatSettingsFile, argument, CHAT_SETTINGS_FILE_PREFIX);
                return true;
            }
            if (argument.startsWith(MODERATION_CONFIG_FILE_PREFIX)) {
                moderationConfigFile = setPathOnce(
                        moderationConfigFile, argument, MODERATION_CONFIG_FILE_PREFIX);
                return true;
            }
            if (argument.startsWith(TUNNEL_BINARY_FILE_PREFIX)) {
                tunnelBinaryFile = setPathOnce(tunnelBinaryFile, argument, TUNNEL_BINARY_FILE_PREFIX);
                return true;
            }
            if (argument.startsWith(TUNNEL_TOKEN_FILE_PREFIX)) {
                tunnelTokenFile = setPathOnce(tunnelTokenFile, argument, TUNNEL_TOKEN_FILE_PREFIX);
                return true;
            }
            return false;
        }

        private boolean acceptPreviewValue(String argument) {
            if (argument.startsWith(ENVIRONMENT_PREFIX)) {
                if (environment != null) {
                    throw invalidArguments();
                }
                environment = StaffBotEnvironment.parse(argument.substring(ENVIRONMENT_PREFIX.length()));
                return true;
            }
            if (argument.startsWith(PREVIEW_WEB_BIND_PREFIX)) {
                previewWebBind = setStringOnce(previewWebBind, argument, PREVIEW_WEB_BIND_PREFIX);
                return true;
            }
            if (argument.startsWith(PREVIEW_PUBLIC_URL_PREFIX)) {
                previewPublicUrl = setStringOnce(previewPublicUrl, argument, PREVIEW_PUBLIC_URL_PREFIX);
                return true;
            }
            if (argument.startsWith(MODERATION_WEB_URL_PREFIX)) {
                moderationWebUrl = setStringOnce(moderationWebUrl, argument, MODERATION_WEB_URL_PREFIX);
                return true;
            }
            return false;
        }

        private StaffBotCommandLine finish() {
            boolean tunnelRequested = tunnelRequested();
            validateTunnelConfiguration(tunnelRequested);
            validateMode(tunnelRequested);
            return new StaffBotCommandLine(this);
        }

        private boolean tunnelRequested() {
            return tunnelBinaryFile != null || tunnelTokenFile != null;
        }

        private void validateTunnelConfiguration(boolean tunnelRequested) {
            if (!tunnelRequested) {
                return;
            }
            if (tunnelBinaryFile == null || tunnelTokenFile == null || moderationConfigFile == null) {
                throw invalidArguments();
            }
        }

        private void validateMode(boolean tunnelRequested) {
            if (stagingUiPreview) {
                validatePreviewMode();
                return;
            }
            validateNormalMode(tunnelRequested);
        }

        private void validatePreviewMode() {
            if (tokenFile == null || environment != null || moderationWebUrl != null) {
                throw invalidArguments();
            }
        }

        private void validateNormalMode(boolean tunnelRequested) {
            validateNoPreviewOptions(tunnelRequested);
            validateFilePair();
            if (environment != null && tokenFile == null) {
                throw invalidArguments();
            }
            if (moderationWebUrl != null && (environment != StaffBotEnvironment.PRODUCTION
                    || !tunnelRequested || tokenFile == null)) {
                throw invalidArguments();
            }
        }

        private void validateNoPreviewOptions(boolean tunnelRequested) {
            if (tunnelRequested && moderationWebUrl == null) {
                throw invalidArguments();
            }
            if (previewWebBind != null) {
                throw invalidArguments();
            }
            if (previewPublicUrl != null) {
                throw invalidArguments();
            }
        }

        private void validateFilePair() {
            if ((tokenFile == null) != (moderationConfigFile == null)) {
                throw invalidArguments();
            }
        }

        private static Path setPathOnce(Path current, String argument, String prefix) {
            if (current != null) {
                throw invalidArguments();
            }
            return parsePath(argument.substring(prefix.length()));
        }

        private static String setStringOnce(String current, String argument, String prefix) {
            if (current != null) {
                throw invalidArguments();
            }
            return parseNonBlank(argument.substring(prefix.length()));
        }

        private static boolean setOnce(boolean currentValue) {
            if (currentValue) {
                throw invalidArguments();
            }
            return true;
        }
    }
}
