package net.enthusia.staff.discordbot;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

/** Parsed process arguments for the isolated staff Discord bot. */
final class StaffBotCommandLine {
    private static final String SMOKE_TEST_ARGUMENT = "--smoke-test";
    private static final String STAGING_UI_PREVIEW_ARGUMENT = "--staging-ui-preview";
    private static final String TOKEN_FILE_PREFIX = "--token-file=";
    private static final String MODERATION_CONFIG_FILE_PREFIX = "--moderation-config-file=";
    private static final String TUNNEL_BINARY_FILE_PREFIX = "--tunnel-binary-file=";
    private static final String TUNNEL_TOKEN_FILE_PREFIX = "--tunnel-token-file=";
    private static final String PREVIEW_WEB_BIND_PREFIX = "--preview-web-bind=";
    private static final String PREVIEW_PUBLIC_URL_PREFIX = "--preview-public-url=";

    private final boolean smokeTest;
    private final boolean stagingUiPreview;
    private final Path tokenFile;
    private final Path moderationConfigFile;
    private final Path tunnelBinaryFile;
    private final Path tunnelTokenFile;
    private final String previewWebBind;
    private final String previewPublicUrl;

    private StaffBotCommandLine(
            boolean smokeTest,
            boolean stagingUiPreview,
            Path tokenFile,
            Path moderationConfigFile,
            Path tunnelBinaryFile,
            Path tunnelTokenFile,
            String previewWebBind,
            String previewPublicUrl
    ) {
        this.smokeTest = smokeTest;
        this.stagingUiPreview = stagingUiPreview;
        this.tokenFile = tokenFile;
        this.moderationConfigFile = moderationConfigFile;
        this.tunnelBinaryFile = tunnelBinaryFile;
        this.tunnelTokenFile = tunnelTokenFile;
        this.previewWebBind = previewWebBind;
        this.previewPublicUrl = previewPublicUrl;
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

    Optional<Path> moderationConfigFile() {
        return Optional.ofNullable(moderationConfigFile);
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

    @Override
    public String toString() {
        return "StaffBotCommandLine[smokeTest=" + smokeTest
                + ", stagingUiPreview=" + stagingUiPreview
                + ", tokenFile=" + configured(tokenFile)
                + ", moderationConfigFile=" + configured(moderationConfigFile)
                + ", tunnelBinaryFile=" + configured(tunnelBinaryFile)
                + ", tunnelTokenFile=" + configured(tunnelTokenFile)
                + ", previewWebBind=" + configured(previewWebBind)
                + ", previewPublicUrl=" + configured(previewPublicUrl) + "]";
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
        private Path moderationConfigFile;
        private Path tunnelBinaryFile;
        private Path tunnelTokenFile;
        private String previewWebBind;
        private String previewPublicUrl;

        private void accept(String argument) {
            if (acceptFlag(argument) || acceptPath(argument) || acceptValue(argument)) {
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

        private boolean acceptValue(String argument) {
            if (argument.startsWith(PREVIEW_WEB_BIND_PREFIX)) {
                previewWebBind = setStringOnce(previewWebBind, argument, PREVIEW_WEB_BIND_PREFIX);
                return true;
            }
            if (argument.startsWith(PREVIEW_PUBLIC_URL_PREFIX)) {
                previewPublicUrl = setStringOnce(previewPublicUrl, argument, PREVIEW_PUBLIC_URL_PREFIX);
                return true;
            }
            return false;
        }

        private StaffBotCommandLine finish() {
            validatePreviewTokenPair();
            boolean tunnelRequested = tunnelRequested();
            validateTunnelConfiguration(tunnelRequested);
            validatePreviewOnlyConfiguration(tunnelRequested);
            return commandLine();
        }

        private void validatePreviewTokenPair() {
            if (stagingUiPreview != (tokenFile != null)) {
                throw invalidArguments();
            }
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

        private void validatePreviewOnlyConfiguration(boolean tunnelRequested) {
            if (stagingUiPreview) {
                return;
            }
            if (moderationConfigFile != null
                    || tunnelRequested || previewWebBind != null || previewPublicUrl != null) {
                throw invalidArguments();
            }
        }

        private StaffBotCommandLine commandLine() {
            return new StaffBotCommandLine(
                    smokeTest,
                    stagingUiPreview,
                    tokenFile,
                    moderationConfigFile,
                    tunnelBinaryFile,
                    tunnelTokenFile,
                    previewWebBind,
                    previewPublicUrl);
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
