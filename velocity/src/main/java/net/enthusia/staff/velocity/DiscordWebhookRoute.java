package net.enthusia.staff.velocity;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

record DiscordWebhookRoute(String destination, DiscordRouteEnvironment environment, URI endpoint) {
    private static final Pattern DESTINATION = Pattern.compile("[a-z-]{1,32}");
    private static final Pattern PRODUCTION_PATH = Pattern.compile("/api/webhooks/[0-9]+/[A-Za-z0-9._-]+");
    private static final Set<String> PRODUCTION_HOSTS = Set.of("discord.com", "discordapp.com");

    DiscordWebhookRoute {
        if (destination == null || !DESTINATION.matcher(destination).matches()) {
            throw new IllegalArgumentException("Discord route destination is invalid");
        }
        if (environment == null || endpoint == null) {
            throw new IllegalArgumentException("Discord route environment and endpoint are required");
        }
        validateCommon(endpoint);
        if (environment == DiscordRouteEnvironment.PRODUCTION) {
            validateProduction(endpoint);
        }
    }

    static DiscordWebhookRoute approvedStaging(
            String destination,
            URI endpoint,
            Set<String> approvedHosts
    ) {
        if (approvedHosts == null || approvedHosts.isEmpty()) {
            throw new IllegalArgumentException("At least one staging Discord host must be approved");
        }
        String host = normalizedHost(endpoint);
        if (PRODUCTION_HOSTS.contains(host)) {
            throw new IllegalArgumentException("Production Discord hosts cannot be classified as staging routes");
        }
        boolean approved = approvedHosts.stream()
                .map(DiscordWebhookRoute::normalizeConfiguredHost)
                .anyMatch(host::equals);
        if (!approved) {
            throw new IllegalArgumentException("Discord staging webhook host is not approved");
        }
        return new DiscordWebhookRoute(destination, DiscordRouteEnvironment.STAGING, endpoint);
    }

    static DiscordWebhookRoute approvedProduction(String destination, URI endpoint) {
        return new DiscordWebhookRoute(destination, DiscordRouteEnvironment.PRODUCTION, endpoint);
    }

    private static void validateCommon(URI endpoint) {
        if (!endpoint.isAbsolute()
                || !"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null
                || endpoint.getHost().isBlank()
                || endpoint.getUserInfo() != null
                || endpoint.getFragment() != null
                || endpoint.getRawQuery() != null) {
            throw new IllegalArgumentException(
                    "Discord webhook routes must be absolute HTTPS endpoints without user info, query, or fragment"
            );
        }
        if (endpoint.getPort() == 0 || endpoint.getPort() < -1 || endpoint.getPort() > 65_535) {
            throw new IllegalArgumentException("Discord webhook route port is invalid");
        }
    }

    private static void validateProduction(URI endpoint) {
        String host = normalizedHost(endpoint);
        if (!PRODUCTION_HOSTS.contains(host)) {
            throw new IllegalArgumentException("Production Discord webhook host is not approved");
        }
        if (endpoint.getPort() != -1 && endpoint.getPort() != 443) {
            throw new IllegalArgumentException("Production Discord webhooks must use the default HTTPS port");
        }
        String path = endpoint.getRawPath();
        if (path == null || !PRODUCTION_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Production Discord webhook path is invalid");
        }
    }

    private static String normalizedHost(URI endpoint) {
        return normalizeConfiguredHost(endpoint.getHost());
    }

    private static String normalizeConfiguredHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Approved Discord host is blank");
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9.-]{1,253}") || normalized.startsWith(".") || normalized.endsWith(".")) {
            throw new IllegalArgumentException("Approved Discord host is invalid");
        }
        return normalized;
    }
}
