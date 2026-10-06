package net.enthusia.staff.discordbot;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Optional private read-only API configuration for Enthusia AI. */
final class AiModerationReadConfiguration {
    static final String ENABLED_KEY = "ENTHUSIA_STAFF_BOT_AI_READ_ENABLED";
    static final String HOST_KEY = "ENTHUSIA_STAFF_BOT_AI_READ_HOST";
    static final String PORT_KEY = "ENTHUSIA_STAFF_BOT_AI_READ_PORT";
    static final String BEARER_KEY = "ENTHUSIA_STAFF_BOT_AI_READ_BEARER_TOKEN";

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 8767;
    private static final int MIN_SECRET_LENGTH = 32;

    private final String host;
    private final int port;
    private final String bearerToken;

    private AiModerationReadConfiguration(String host, int port, String bearerToken) {
        this.host = requireHost(host);
        this.port = requirePort(port);
        this.bearerToken = requireBearer(bearerToken);
    }

    static Optional<AiModerationReadConfiguration> fromEnvironment(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        if (!Boolean.parseBoolean(values.getOrDefault(ENABLED_KEY, "false"))) {
            return Optional.empty();
        }
        String host = values.getOrDefault(HOST_KEY, DEFAULT_HOST);
        int port = parsePort(values.get(PORT_KEY));
        return Optional.of(new AiModerationReadConfiguration(host, port, values.get(BEARER_KEY)));
    }

    static Optional<AiModerationReadConfiguration> fromSystemEnvironment() {
        return fromEnvironment(System.getenv());
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    String bearerToken() {
        return bearerToken;
    }

    @Override
    public String toString() {
        return "AiModerationReadConfiguration[host=" + host
                + ", port=" + port + ", bearerToken=<redacted>]";
    }

    private static String requireHost(String value) {
        String host = value == null ? "" : value.trim();
        if (host.isEmpty() || host.length() > 255 || host.indexOf('/') >= 0 || host.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("AI moderation read host is invalid");
        }
        return host;
    }

    private static int parsePort(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            return requirePort(Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("AI moderation read port is invalid", exception);
        }
    }

    private static int requirePort(int value) {
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException("AI moderation read port is invalid");
        }
        return value;
    }

    private static String requireBearer(String value) {
        if (value == null || value.length() < MIN_SECRET_LENGTH || value.length() > 512) {
            throw new IllegalArgumentException("AI moderation read bearer credential is missing or invalid");
        }
        return value;
    }
}
