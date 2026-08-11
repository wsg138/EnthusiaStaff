package net.enthusia.staff.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.common.security.SecretKeyMaterial;
import net.enthusia.staff.persistence.DatabaseConfig;

public record VelocityConfiguration(
        String jdbcUrlEnvironment,
        String usernameEnvironment,
        String passwordEnvironment,
        int maximumPoolSize,
        long connectionTimeoutMillis,
        boolean failClosedWhileActive,
        String appealsUrl,
        String serverId,
        boolean websiteApiEnabled,
        String websiteApiBindAddress,
        int websiteApiPort,
        String websiteApiBearerTokenEnvironment,
        String websiteApiHmacSecretEnvironment,
        int punishmentCodeKeyVersion,
        String punishmentCodeSecretEnvironment,
        int websiteApiTimestampSkewSeconds,
        int websiteApiMaximumBodyBytes,
        int websiteApiWorkerThreads,
        int websiteApiQueueCapacity,
        boolean channelEnabled,
        String channelBindAddress,
        int channelPort,
        String channelProxyId,
        String channelProxySecretEnvironment,
        Path channelTlsKeyStorePath,
        String channelTlsKeyStorePasswordEnvironment,
        Map<String, String> backendSecretEnvironments,
        boolean networkIdentityEnabled,
        int networkIdentityHmacKeyVersion,
        String networkIdentityHmacSecretEnvironment,
        int networkIdentityEncryptionKeyVersion,
        String networkIdentityEncryptionSecretEnvironment,
        boolean discordEnabled,
        String discordRouteEnvironment,
        String discordStagingAllowedHostsEnvironment,
        Map<String, String> discordWebhookEnvironments,
        int discordMaximumAttempts,
        int discordFailureThreshold,
        int discordCircuitOpenSeconds,
        int discordRequestTimeoutMillis,
        String liteBansJdbcUrlEnvironment,
        String liteBansUsernameEnvironment,
        String liteBansPasswordEnvironment,
        int liteBansMaximumPoolSize,
        long liteBansConnectionTimeoutMillis,
        String liteBansTablePrefix,
        int liteBansBatchSize,
        boolean liteBansShadowScheduleEnabled,
        int liteBansShadowIntervalHours
) {
    public VelocityConfiguration {
        backendSecretEnvironments = Map.copyOf(backendSecretEnvironments);
        discordWebhookEnvironments = Map.copyOf(discordWebhookEnvironments);
    }

    public static VelocityConfiguration load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path file = dataDirectory.resolve("config.properties");
        if (Files.notExists(file)) {
            try (InputStream defaults = VelocityConfiguration.class.getResourceAsStream("/velocity-config.properties")) {
                if (defaults == null) {
                    throw new IOException("Bundled velocity-config.properties is missing");
                }
                try (OutputStream output = Files.newOutputStream(file)) {
                    defaults.transferTo(output);
                }
            }
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return new VelocityConfiguration(
                required(properties, "storage.jdbc-url-environment"),
                required(properties, "storage.username-environment"),
                required(properties, "storage.password-environment"),
                integer(properties, "storage.maximum-pool-size", 1, 64),
                integer(properties, "storage.connection-timeout-millis", 250, 60_000),
                bool(properties, "enforcement.fail-closed-while-active"),
                required(properties, "appeals.url"),
                required(properties, "server.id"),
                bool(properties, "website-api.enabled"),
                required(properties, "website-api.bind-address"),
                integer(properties, "website-api.port", 1, 65_535),
                required(properties, "website-api.bearer-token-environment"),
                required(properties, "website-api.hmac-secret-environment"),
                integer(properties, "website-api.code-key-version", 1, Integer.MAX_VALUE),
                required(properties, "website-api.code-secret-environment"),
                integer(properties, "website-api.timestamp-skew-seconds", 30, 900),
                integer(properties, "website-api.maximum-body-bytes", 1_024, 1_048_576),
                integer(properties, "website-api.worker-threads", 1, 16),
                integer(properties, "website-api.queue-capacity", 8, 2_048),
                bool(properties, "channel.enabled"),
                required(properties, "channel.bind-address"),
                integer(properties, "channel.port", 1, 65_535),
                required(properties, "channel.proxy-id"),
                required(properties, "channel.proxy-secret-environment"),
                configuredPath(dataDirectory, properties, "channel.tls-key-store", "channel-server.p12"),
                value(properties, "channel.tls-key-store-password-environment",
                        "ES_CHANNEL_TLS_KEYSTORE_PASSWORD"),
                backendSecrets(properties),
                bool(properties, "network-identity.enabled"),
                integer(properties, "network-identity.hmac-key-version", 1, Integer.MAX_VALUE),
                required(properties, "network-identity.hmac-secret-environment"),
                integer(properties, "network-identity.encryption-key-version", 1, Integer.MAX_VALUE),
                required(properties, "network-identity.encryption-secret-environment"),
                bool(properties, "discord.enabled"),
                required(properties, "discord.route-environment"),
                required(properties, "discord.staging-allowed-hosts-environment"),
                discordWebhookEnvironments(properties),
                integer(properties, "discord.maximum-attempts", 1, 100),
                integer(properties, "discord.failure-threshold", 1, 100),
                integer(properties, "discord.circuit-open-seconds", 10, 86_400),
                integer(properties, "discord.request-timeout-millis", 500, 15_000),
                required(properties, "litebans.jdbc-url-environment"),
                required(properties, "litebans.username-environment"),
                required(properties, "litebans.password-environment"),
                integer(properties, "litebans.maximum-pool-size", 1, 8),
                integer(properties, "litebans.connection-timeout-millis", 250, 60_000),
                required(properties, "litebans.table-prefix"),
                integer(properties, "litebans.batch-size", 1, 5_000),
                bool(properties, "litebans.shadow-schedule-enabled", false),
                integer(properties, "litebans.shadow-interval-hours", 24, 1, 24)
        );
    }

    private static Map<String, String> backendSecrets(Properties properties) {
        String prefix = "channel.backend.";
        String suffix = ".secret-environment";
        Map<String, String> secrets = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix) && key.endsWith(suffix)) {
                String backendId = key.substring(prefix.length(), key.length() - suffix.length());
                if (!backendId.matches("[A-Za-z0-9_-]{1,64}")) {
                    throw new IllegalArgumentException("Invalid backend server ID in channel configuration");
                }
                secrets.put(backendId, required(properties, key));
            }
        }
        return secrets;
    }

    private static Map<String, String> discordWebhookEnvironments(Properties properties) {
        Map<String, String> environments = new LinkedHashMap<>();
        for (String destination : new String[]{"punishments", "reports", "logs-staffmode", "alerts"}) {
            environments.put(destination, required(properties, "discord." + destination + ".webhook-environment"));
        }
        return environments;
    }

    public Map<String, DiscordWebhookRoute> discordWebhookRoutesFromEnvironment() {
        DiscordRouteEnvironment routeEnvironment = DiscordRouteEnvironment.parse(discordRouteEnvironment);
        Set<String> stagingHosts = routeEnvironment == DiscordRouteEnvironment.STAGING
                ? discordStagingHostsFromEnvironment()
                : Set.of();
        Map<String, DiscordWebhookRoute> routes = new LinkedHashMap<>();
        discordWebhookEnvironments.forEach((destination, environmentName) -> {
            String raw = System.getenv(environmentName);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("A required Discord webhook environment variable is missing");
            }
            final URI uri;
            try {
                uri = URI.create(raw.trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("A Discord webhook environment variable is not a valid URI", exception);
            }
            final DiscordWebhookRoute route;
            try {
                route = routeEnvironment == DiscordRouteEnvironment.STAGING
                        ? DiscordWebhookRoute.approvedStaging(destination, uri, stagingHosts)
                        : DiscordWebhookRoute.approvedProduction(destination, uri);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("A Discord webhook route failed destination policy validation", exception);
            }
            routes.put(destination, route);
        });
        return Map.copyOf(routes);
    }

    private Set<String> discordStagingHostsFromEnvironment() {
        String raw = System.getenv(discordStagingAllowedHostsEnvironment);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("The Discord staging approved-host environment variable is missing");
        }
        Set<String> hosts = new LinkedHashSet<>();
        for (String candidate : raw.split(",")) {
            String host = candidate.trim();
            if (!host.isEmpty()) {
                hosts.add(host);
            }
        }
        if (hosts.isEmpty()) {
            throw new IllegalStateException("The Discord staging approved-host environment variable is empty");
        }
        return Set.copyOf(hosts);
    }

    public DatabaseConfig databaseFromEnvironment() {
        String url = System.getenv(jdbcUrlEnvironment);
        String username = System.getenv(usernameEnvironment);
        String password = System.getenv(passwordEnvironment);
        if (url == null || url.isBlank() || username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new IllegalStateException("Required MariaDB environment variables are missing");
        }
        return new DatabaseConfig(url, username, password, maximumPoolSize, connectionTimeoutMillis);
    }

    public DatabaseConfig liteBansDatabaseFromEnvironment() {
        String url = System.getenv(liteBansJdbcUrlEnvironment);
        String username = System.getenv(liteBansUsernameEnvironment);
        String password = System.getenv(liteBansPasswordEnvironment);
        if (url == null || url.isBlank() || username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new IllegalStateException("Required LiteBans database environment variables are missing");
        }
        return new DatabaseConfig(
                url,
                username,
                password,
                liteBansMaximumPoolSize,
                liteBansConnectionTimeoutMillis
        );
    }

    public String websiteApiBearerTokenFromEnvironment() {
        return websiteSecret(websiteApiBearerTokenEnvironment, "bearer token");
    }

    public String websiteApiHmacSecretFromEnvironment() {
        return websiteSecret(websiteApiHmacSecretEnvironment, "HMAC secret");
    }

    public PunishmentCodeProtector punishmentCodeProtectorFromEnvironment() {
        String encoded = System.getenv(punishmentCodeSecretEnvironment);
        return new PunishmentCodeProtector(
                punishmentCodeKeyVersion,
                SecretKeyMaterial.hmacSha256FromBase64(encoded)
        );
    }

    private static String websiteSecret(String environment, String label) {
        String configured = System.getenv(environment);
        if (configured == null || configured.isBlank()
                || configured.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("The website API " + label + " must contain at least 32 bytes");
        }
        return configured;
    }

    private static String required(Properties properties, String key) {
        String configured = properties.getProperty(key);
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException(key + " must be configured");
        }
        return configured.trim();
    }

    private static String value(Properties properties, String key, String defaultValue) {
        String configured = properties.getProperty(key);
        return configured == null || configured.isBlank() ? defaultValue : configured.trim();
    }

    private static Path configuredPath(
            Path dataDirectory,
            Properties properties,
            String key,
            String defaultValue
    ) {
        Path configured = Path.of(value(properties, key, defaultValue));
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path base = dataDirectory.toAbsolutePath().normalize();
        Path resolved = base.resolve(configured).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException(key + " must remain in the plugin data directory when relative");
        }
        return resolved;
    }

    private static int integer(Properties properties, String key, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(required(properties, key));
            if (parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static int integer(
            Properties properties,
            String key,
            int defaultValue,
            int minimum,
            int maximum
    ) {
        String configured = properties.getProperty(key);
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        return integer(properties, key, minimum, maximum);
    }

    private static boolean bool(Properties properties, String key) {
        String configured = required(properties, key);
        if (!configured.equalsIgnoreCase("true") && !configured.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(configured);
    }

    private static boolean bool(Properties properties, String key, boolean defaultValue) {
        String configured = properties.getProperty(key);
        return configured == null || configured.isBlank() ? defaultValue : bool(properties, key);
    }
}
