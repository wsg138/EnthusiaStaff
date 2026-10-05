package net.enthusia.staff.discordbot;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

record DiscordCommandBridgeConfiguration(
        Map<String, URI> endpoints,
        String credential,
        Duration timeout
) {
    static final String ENDPOINTS_ENV = "ENTHUSIA_STAFF_BOT_COMMAND_BRIDGE_ENDPOINTS";
    static final String CREDENTIAL_ENV = "ENTHUSIA_STAFF_BOT_COMMAND_BRIDGE_SECRET";
    static final String TIMEOUT_MILLIS_ENV = "ENTHUSIA_STAFF_BOT_COMMAND_BRIDGE_TIMEOUT_MILLIS";
    private static final int DEFAULT_TIMEOUT_MILLIS = 3_000;
    private static final int MIN_TIMEOUT_MILLIS = 250;
    private static final int MAX_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_ENDPOINTS = 16;
    private static final int MIN_SECRET_LENGTH = 32;

    DiscordCommandBridgeConfiguration {
        endpoints = Map.copyOf(endpoints);
        if (endpoints.isEmpty() || endpoints.size() > MAX_ENDPOINTS
                || credential == null || credential.length() < MIN_SECRET_LENGTH
                || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("command bridge configuration is invalid");
        }
    }

    static Optional<DiscordCommandBridgeConfiguration> fromEnvironment(Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("command bridge configuration values are required");
        }
        boolean any = present(values.get(ENDPOINTS_ENV))
                || present(values.get(CREDENTIAL_ENV))
                || present(values.get(TIMEOUT_MILLIS_ENV));
        if (!any) {
            return Optional.empty();
        }
        String rawEndpoints = required(values.get(ENDPOINTS_ENV), ENDPOINTS_ENV);
        String credential = required(values.get(CREDENTIAL_ENV), CREDENTIAL_ENV);
        return Optional.of(new DiscordCommandBridgeConfiguration(
                parseEndpoints(rawEndpoints),
                credential,
                Duration.ofMillis(timeout(values.get(TIMEOUT_MILLIS_ENV)))
        ));
    }

    private static Map<String, URI> parseEndpoints(String raw) {
        Map<String, URI> endpoints = new LinkedHashMap<>();
        for (String entry : raw.split(";", -1)) {
            int separator = entry.indexOf('=');
            if (separator < 1 || separator != entry.lastIndexOf('=')) {
                throw new IllegalArgumentException("command bridge endpoints must use server=URI entries");
            }
            String server = entry.substring(0, separator).trim();
            URI uri = parseUri(entry.substring(separator + 1).trim());
            if (!server.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                    || endpoints.putIfAbsent(server, uri) != null
                    || endpoints.size() > MAX_ENDPOINTS) {
                throw new IllegalArgumentException("command bridge endpoint allowlist is invalid");
            }
        }
        return Map.copyOf(endpoints);
    }

    private static URI parseUri(String raw) {
        try {
            URI uri = URI.create(raw);
            if (!validEndpoint(uri)) {
                throw new IllegalArgumentException("command bridge endpoint URI is invalid");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("command bridge endpoint URI is invalid", exception);
        }
    }

    private static boolean validEndpoint(URI uri) {
        return validAuthority(uri) && validTarget(uri) && validScheme(uri.getScheme());
    }

    private static boolean validAuthority(URI uri) {
        return uri.getHost() != null && uri.getUserInfo() == null;
    }

    private static boolean validTarget(URI uri) {
        return uri.getQuery() == null
                && uri.getFragment() == null
                && HttpMinecraftCommandBridgeClient.ENDPOINT_PATH.equals(uri.getPath());
    }

    private static boolean validScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static int timeout(String raw) {
        if (!present(raw)) {
            return DEFAULT_TIMEOUT_MILLIS;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < MIN_TIMEOUT_MILLIS || value > MAX_TIMEOUT_MILLIS) {
                throw new IllegalArgumentException("command bridge timeout is outside its safe range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("command bridge timeout must be numeric", exception);
        }
    }

    private static String required(String value, String name) {
        if (!present(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "DiscordCommandBridgeConfiguration[endpoints=" + endpoints.keySet()
                + ", credential=<redacted>, timeout=" + timeout + "]";
    }
}
