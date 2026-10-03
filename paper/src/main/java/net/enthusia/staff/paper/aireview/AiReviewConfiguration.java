package net.enthusia.staff.paper.aireview;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.configuration.ConfigurationSection;

public record AiReviewConfiguration(
        URI baseUri,
        String clientId,
        String bearerToken,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration pollInterval,
        Duration cacheStaleAfter,
        int reviewLimit,
        int workerThreads,
        int queueCapacity,
        int responseMaxBytes,
        int notifiedCacheSize,
        int pageSize,
        int maximumMessageCharacters,
        int maximumContextItems,
        int maximumReasonCodes,
        int maximumScores,
        boolean adminOverrideEnabled
) {
    public static final String ROOT = "ai-review";

    public AiReviewConfiguration {
        Objects.requireNonNull(baseUri, "baseUri");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(bearerToken, "bearerToken");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        Objects.requireNonNull(pollInterval, "pollInterval");
        Objects.requireNonNull(cacheStaleAfter, "cacheStaleAfter");
    }

    public static LoadResult load(ConfigurationSection section, Function<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        if (section == null || !section.getBoolean("enabled", false)) {
            return new LoadResult(Optional.empty(), null);
        }
        try {
            URI baseUri = validateBaseUri(section.getString("base-url", ""));
            String clientId = required(section.getString("client-id", ""), "client-id");
            String tokenVariable = required(
                    section.getString("token-environment", "ES_AI_REVIEW_TOKEN"),
                    "token-environment"
            );
            String token = environment.apply(tokenVariable);
            if (token == null || token.isBlank()) {
                return disabled("AI review bearer token environment variable is missing");
            }
            AiReviewConfiguration configuration = new AiReviewConfiguration(
                    baseUri,
                    clientId,
                    token,
                    Duration.ofMillis(boundedLong(section, "connect-timeout-millis", 1_000, 100, 5_000)),
                    Duration.ofMillis(boundedLong(section, "request-timeout-millis", 2_000, 250, 10_000)),
                    Duration.ofMillis(boundedLong(section, "poll-interval-millis", 10_000, 1_000, 300_000)),
                    Duration.ofMillis(boundedLong(section, "cache-stale-after-millis", 30_000, 1_000, 600_000)),
                    boundedInt(section, "review-limit", 100, 1, 250),
                    boundedInt(section, "worker-threads", 2, 1, 4),
                    boundedInt(section, "queue-capacity", 32, 1, 128),
                    boundedInt(section, "response-max-bytes", 262_144, 16_384, 1_048_576),
                    boundedInt(section, "notified-cache-size", 512, 32, 2_048),
                    boundedInt(section, "page-size", 21, 1, 28),
                    boundedInt(section, "maximum-message-characters", 800, 80, 2_048),
                    boundedInt(section, "maximum-context-items", 8, 0, 16),
                    boundedInt(section, "maximum-reason-codes", 12, 1, 32),
                    boundedInt(section, "maximum-scores", 10, 1, 32),
                    section.getBoolean("admin-override-enabled", false)
            );
            return new LoadResult(Optional.of(configuration), null);
        } catch (IllegalArgumentException exception) {
            return disabled("AI review configuration is invalid: " + exception.getMessage());
        }
    }

    private static LoadResult disabled(String diagnostic) {
        return new LoadResult(Optional.empty(), diagnostic);
    }

    private static URI validateBaseUri(String value) {
        try {
            URI uri = new URI(required(value, "base-url"));
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException("base-url must be an http(s) origin without credentials/query/fragment");
            }
            String path = uri.getPath();
            if (path != null && !path.isBlank() && !"/".equals(path)) {
                throw new IllegalArgumentException("base-url must not include an API path");
            }
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    null,
                    null,
                    null
            );
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("base-url must be a valid URI", exception);
        }
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static int boundedInt(
            ConfigurationSection section,
            String key,
            int fallback,
            int minimum,
            int maximum
    ) {
        long value = boundedLong(section, key, fallback, minimum, maximum);
        return Math.toIntExact(value);
    }

    private static long boundedLong(
            ConfigurationSection section,
            String key,
            long fallback,
            long minimum,
            long maximum
    ) {
        Object raw = section.get(key);
        long value;
        if (raw == null) {
            value = fallback;
        } else if (raw instanceof Number number) {
            value = number.longValue();
        } else {
            try {
                value = Long.parseLong(raw.toString().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key + " must be an integer", exception);
            }
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    key + " must be between " + minimum + " and " + maximum
            );
        }
        return value;
    }

    public record LoadResult(Optional<AiReviewConfiguration> configuration, String diagnostic) {
        public LoadResult {
            configuration = configuration == null ? Optional.empty() : configuration;
        }

        public boolean enabled() {
            return configuration.isPresent();
        }
    }
}
