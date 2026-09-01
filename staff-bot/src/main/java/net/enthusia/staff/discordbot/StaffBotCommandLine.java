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
    private static final String PREVIEW_WEB_BIND_PREFIX = "--preview-web-bind=";
    private static final String PREVIEW_PUBLIC_URL_PREFIX = "--preview-public-url=";

    private final boolean smokeTest;
    private final boolean stagingUiPreview;
    private final Path tokenFile;
    private final Path moderationConfigFile;
    private final String previewWebBind;
    private final String previewPublicUrl;

    private StaffBotCommandLine(
            boolean smokeTest,
            boolean stagingUiPreview,
            Path tokenFile,
            Path moderationConfigFile,
            String previewWebBind,
            String previewPublicUrl
    ) {
        this.smokeTest = smokeTest;
        this.stagingUiPreview = stagingUiPreview;
        this.tokenFile = tokenFile;
        this.moderationConfigFile = moderationConfigFile;
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

    private static final class Parser {
        private boolean smokeTest;
        private boolean stagingUiPreview;
        private Path tokenFile;
        private Path moderationConfigFile;
        private String previewWebBind;
        private String previewPublicUrl;

        private void accept(String argument) {
            if (SMOKE_TEST_ARGUMENT.equals(argument)) {
                smokeTest = setOnce(smokeTest);
                return;
            }
            if (STAGING_UI_PREVIEW_ARGUMENT.equals(argument)) {
                stagingUiPreview = setOnce(stagingUiPreview);
                return;
            }
            if (argument.startsWith(TOKEN_FILE_PREFIX)) {
                tokenFile = setPathOnce(tokenFile, argument, TOKEN_FILE_PREFIX);
                return;
            }
            if (argument.startsWith(MODERATION_CONFIG_FILE_PREFIX)) {
                moderationConfigFile = setPathOnce(
                        moderationConfigFile, argument, MODERATION_CONFIG_FILE_PREFIX);
                return;
            }
            if (argument.startsWith(PREVIEW_WEB_BIND_PREFIX)) {
                if (previewWebBind != null) {
                    throw invalidArguments();
                }
                previewWebBind = parseNonBlank(argument.substring(PREVIEW_WEB_BIND_PREFIX.length()));
                return;
            }
            if (argument.startsWith(PREVIEW_PUBLIC_URL_PREFIX)) {
                if (previewPublicUrl != null) {
                    throw invalidArguments();
                }
                previewPublicUrl = parseNonBlank(argument.substring(PREVIEW_PUBLIC_URL_PREFIX.length()));
                return;
            }
            throw invalidArguments();
        }

        private StaffBotCommandLine finish() {
            if (stagingUiPreview != (tokenFile != null)) {
                throw invalidArguments();
            }
            if (!stagingUiPreview
                    && (moderationConfigFile != null || previewWebBind != null || previewPublicUrl != null)) {
                throw invalidArguments();
            }
            return new StaffBotCommandLine(
                    smokeTest,
                    stagingUiPreview,
                    tokenFile,
                    moderationConfigFile,
                    previewWebBind,
                    previewPublicUrl);
        }

        private static Path setPathOnce(Path current, String argument, String prefix) {
            if (current != null) {
                throw invalidArguments();
            }
            return parsePath(argument.substring(prefix.length()));
        }

        private static boolean setOnce(boolean currentValue) {
            if (currentValue) {
                throw invalidArguments();
            }
            return true;
        }
    }
}
