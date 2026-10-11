package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Opt-in, strictly staging-only transport settings for non-enforcing mirror registration. */
record AiMirrorPublicationConfig(
        URI endpoint,
        String clientId,
        String token,
        long guildId,
        Set<Long> allowedChannels,
        String channelProfile
) {
    static final String ENABLED = "ENTHUSIA_STAFF_BOT_AI_MIRROR_REGISTER_ENABLED";
    static final String ENDPOINT = "ENTHUSIA_STAFF_BOT_AI_MIRROR_REGISTER_URL";
    static final String CLIENT_ID = "ENTHUSIA_STAFF_BOT_AI_MIRROR_CLIENT_ID";
    static final String TOKEN_FILE = "ENTHUSIA_STAFF_BOT_AI_MIRROR_TOKEN_FILE";
    static final String PROFILE = "ENTHUSIA_STAFF_BOT_AI_MIRROR_DISCORD_PROFILE";

    AiMirrorPublicationConfig {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(allowedChannels, "allowedChannels");
        if (!validEndpoint(endpoint) || !clientId.matches("[A-Za-z0-9._:-]{1,80}")
                || token.isBlank() || token.length() > 512 || guildId <= 0
                || allowedChannels.isEmpty() || allowedChannels.stream().anyMatch(id -> id <= 0)
                || !validProfile(channelProfile)) {
            throw new IllegalArgumentException("staging AI mirror registration configuration is invalid");
        }
        allowedChannels = Set.copyOf(allowedChannels);
    }

    static Optional<AiMirrorPublicationConfig> fromEnvironment(
            StaffBotEnvironment environment,
            StaffBotChatBridgeConfiguration chatBridge,
            Map<String, String> values
    ) throws IOException {
        Objects.requireNonNull(values, "values");
        String enabled = values.getOrDefault(ENABLED, "false");
        if ("false".equalsIgnoreCase(enabled)) {
            return Optional.empty();
        }
        if (!"true".equalsIgnoreCase(enabled)
                || environment != StaffBotEnvironment.STAGING || chatBridge == null) {
            throw new IllegalArgumentException("AI mirror publishing is disabled outside staging");
        }
        Path tokenPath = Path.of(required(values, TOKEN_FILE));
        if (!Files.isRegularFile(tokenPath, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(tokenPath)) {
            throw new IllegalArgumentException("AI mirror private token file is unavailable");
        }
        String token = Files.readString(tokenPath, StandardCharsets.UTF_8).strip();
        return Optional.of(new AiMirrorPublicationConfig(
                URI.create(required(values, ENDPOINT)),
                required(values, CLIENT_ID),
                token,
                environment.guildId(),
                Set.copyOf(chatBridge.routes().values()),
                required(values, PROFILE)
        ));
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required AI mirror registration setting");
        }
        return value.strip();
    }

    private static boolean validProfile(String value) {
        return "discord_general".equals(value) || "discord_gaming".equals(value);
    }

    private static boolean validEndpoint(URI uri) {
        if (!"/v1/mirror-aliases".equals(uri.getRawPath()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || uri.getPort() < 1) {
            return false;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return true;
        }
        String host = uri.getHost();
        return "http".equalsIgnoreCase(uri.getScheme())
                && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)
                    || "::1".equals(host) || "[::1]".equals(host));
    }
}
