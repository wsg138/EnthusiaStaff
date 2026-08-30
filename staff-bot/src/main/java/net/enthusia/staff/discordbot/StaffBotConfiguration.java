package net.enthusia.staff.discordbot;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Staff-bot runtime configuration. Secrets are deliberately excluded from {@link #toString()}. */
public final class StaffBotConfiguration {
    public static final String ENVIRONMENT_KEY = "ENTHUSIA_STAFF_BOT_ENVIRONMENT";
    public static final String TOKEN_KEY = "ENTHUSIA_STAFF_BOT_TOKEN";
    public static final String UI_PREVIEW_KEY = "ENTHUSIA_STAFF_BOT_UI_PREVIEW";
    public static final String HEALTH_HOST_KEY = "ENTHUSIA_STAFF_BOT_HEALTH_HOST";
    public static final String HEALTH_PORT_KEY = "ENTHUSIA_STAFF_BOT_HEALTH_PORT";
    public static final String WORKER_THREADS_KEY = "ENTHUSIA_STAFF_BOT_WORKER_THREADS";
    public static final String WORKER_QUEUE_CAPACITY_KEY = "ENTHUSIA_STAFF_BOT_WORKER_QUEUE_CAPACITY";
    public static final String INTERACTION_CAPACITY_KEY = "ENTHUSIA_STAFF_BOT_INTERACTION_CAPACITY";
    public static final String INTERACTION_TTL_SECONDS_KEY = "ENTHUSIA_STAFF_BOT_INTERACTION_TTL_SECONDS";

    private static final String IPV4_LOOPBACK_HOST = "127.0.0.1";
    private static final String LOCALHOST = "localhost";
    private static final String IPV6_LOOPBACK_HOST = "::1";
    private static final Set<String> LOOPBACK_HOSTS = Set.of(IPV4_LOOPBACK_HOST, LOCALHOST, IPV6_LOOPBACK_HOST);
    private static final int DEFAULT_HEALTH_PORT = 8765;
    private static final int DEFAULT_WORKER_THREADS = 4;
    private static final int DEFAULT_WORKER_QUEUE_CAPACITY = 256;
    private static final int DEFAULT_INTERACTION_CAPACITY = 4096;
    private static final int DEFAULT_INTERACTION_TTL_SECONDS = 900;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);

    private final StaffBotEnvironment environment;
    private final String discordToken;
    private final boolean uiPreviewEnabled;
    private final InetSocketAddress healthAddress;
    private final int workerThreads;
    private final int workerQueueCapacity;
    private final int interactionCapacity;
    private final Duration interactionTtl;

    StaffBotConfiguration(
            StaffBotEnvironment environment,
            String discordToken,
            boolean uiPreviewEnabled,
            InetSocketAddress healthAddress,
            int workerThreads,
            int workerQueueCapacity,
            int interactionCapacity,
            Duration interactionTtl) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.discordToken = requireSecret(discordToken);
        this.uiPreviewEnabled = uiPreviewEnabled;
        this.healthAddress = Objects.requireNonNull(healthAddress, "healthAddress");
        this.workerThreads = bounded("worker threads", workerThreads, 1, 16);
        this.workerQueueCapacity = bounded("worker queue capacity", workerQueueCapacity, 1, 4096);
        this.interactionCapacity = bounded("interaction capacity", interactionCapacity, 16, 65536);
        this.interactionTtl = Objects.requireNonNull(interactionTtl, "interactionTtl");
        if (interactionTtl.isZero() || interactionTtl.isNegative() || interactionTtl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("interaction TTL must be between 1 second and 24 hours");
        }
        if (uiPreviewEnabled && environment != StaffBotEnvironment.STAGING) {
            throw new IllegalArgumentException("staff bot UI preview is staging-only");
        }
        if (environment == StaffBotEnvironment.PRODUCTION && healthAddress.getPort() == 0) {
            throw new IllegalArgumentException("production health port cannot be ephemeral");
        }
    }

    StaffBotConfiguration(
            StaffBotEnvironment environment,
            String discordToken,
            InetSocketAddress healthAddress,
            int workerThreads,
            int workerQueueCapacity,
            int interactionCapacity,
            Duration interactionTtl) {
        this(
                environment,
                discordToken,
                false,
                healthAddress,
                workerThreads,
                workerQueueCapacity,
                interactionCapacity,
                interactionTtl
        );
    }

    public static StaffBotConfiguration fromSystemEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static StaffBotConfiguration fromStartup(StaffBotCommandLine commandLine) {
        return fromStartup(commandLine, System.getenv());
    }

    static StaffBotConfiguration fromStartup(StaffBotCommandLine commandLine, Map<String, String> values) {
        Objects.requireNonNull(commandLine, "commandLine");
        Objects.requireNonNull(values, "values");
        if (!commandLine.stagingUiPreview()) {
            return fromEnvironment(values);
        }

        rejectProductionPreviewEnvironment(values);
        Map<String, String> effectiveValues = new HashMap<>(values);
        effectiveValues.put(ENVIRONMENT_KEY, StaffBotEnvironment.STAGING.label());
        effectiveValues.put(UI_PREVIEW_KEY, Boolean.TRUE.toString());
        effectiveValues.put(TOKEN_KEY, StaffBotTokenFile.read(commandLine.tokenFile().orElseThrow(
                () -> new IllegalArgumentException("staging UI preview requires a token file"))));
        return fromEnvironment(effectiveValues);
    }

    public static StaffBotConfiguration fromEnvironment(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        StaffBotEnvironment environment = StaffBotEnvironment.parse(required(values, ENVIRONMENT_KEY));
        String token = requireSecret(values.get(TOKEN_KEY));
        boolean uiPreviewEnabled = booleanValue(values, UI_PREVIEW_KEY, false);
        String healthHost = values.getOrDefault(HEALTH_HOST_KEY, IPV4_LOOPBACK_HOST).trim();
        if (!LOOPBACK_HOSTS.contains(healthHost)) {
            throw new IllegalArgumentException("staff bot health endpoint must bind to loopback");
        }
        int healthPort = integer(values, HEALTH_PORT_KEY, DEFAULT_HEALTH_PORT, 0, 65535);
        int workerThreads = integer(values, WORKER_THREADS_KEY, DEFAULT_WORKER_THREADS, 1, 16);
        int queueCapacity = integer(values, WORKER_QUEUE_CAPACITY_KEY, DEFAULT_WORKER_QUEUE_CAPACITY, 1, 4096);
        int interactionCapacity = integer(values, INTERACTION_CAPACITY_KEY, DEFAULT_INTERACTION_CAPACITY, 16, 65536);
        int interactionTtlSeconds = integer(
                values,
                INTERACTION_TTL_SECONDS_KEY,
                DEFAULT_INTERACTION_TTL_SECONDS,
                1,
                86400);
        return new StaffBotConfiguration(
                environment,
                token,
                uiPreviewEnabled,
                loopbackSocketAddress(healthHost, healthPort),
                workerThreads,
                queueCapacity,
                interactionCapacity,
                Duration.ofSeconds(interactionTtlSeconds));
    }

    public StaffBotEnvironment environment() {
        return environment;
    }

    String discordToken() {
        return discordToken;
    }

    public boolean uiPreviewEnabled() {
        return uiPreviewEnabled;
    }

    public InetSocketAddress healthAddress() {
        return healthAddress;
    }

    public int workerThreads() {
        return workerThreads;
    }

    public int workerQueueCapacity() {
        return workerQueueCapacity;
    }

    public int interactionCapacity() {
        return interactionCapacity;
    }

    public Duration interactionTtl() {
        return interactionTtl;
    }

    public int maxReconnectDelaySeconds() {
        return MAX_RECONNECT_DELAY_SECONDS;
    }

    public Duration shutdownTimeout() {
        return SHUTDOWN_TIMEOUT;
    }

    @Override
    public String toString() {
        return "StaffBotConfiguration[environment=" + environment.label()
                + ", uiPreviewEnabled=" + uiPreviewEnabled
                + ", healthAddress=" + healthAddress
                + ", workerThreads=" + workerThreads
                + ", workerQueueCapacity=" + workerQueueCapacity
                + ", interactionCapacity=" + interactionCapacity
                + ", interactionTtl=" + interactionTtl
                + ", discordToken=<redacted>]";
    }

    private static void rejectProductionPreviewEnvironment(Map<String, String> values) {
        String configuredEnvironment = values.get(ENVIRONMENT_KEY);
        if (configuredEnvironment == null || configuredEnvironment.isBlank()) {
            return;
        }
        if (StaffBotEnvironment.parse(configuredEnvironment) == StaffBotEnvironment.PRODUCTION) {
            throw new IllegalArgumentException("staging UI preview rejects production environment configuration");
        }
    }

    static InetSocketAddress loopbackSocketAddress(String healthHost, int healthPort) {
        return switch (healthHost) {
            case IPV4_LOOPBACK_HOST, LOCALHOST -> {
                // nosemgrep -- Literal IPv4 loopback bind; validated allowlist input cannot reach this sink.
                yield new InetSocketAddress(IPV4_LOOPBACK_HOST, healthPort);
            }
            case IPV6_LOOPBACK_HOST -> {
                // nosemgrep -- Literal IPv6 loopback bind; validated allowlist input cannot reach this sink.
                yield new InetSocketAddress(IPV6_LOOPBACK_HOST, healthPort);
            }
            default -> throw new IllegalArgumentException("staff bot health endpoint must bind to loopback");
        };
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String requireSecret(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(TOKEN_KEY + " is required");
        }
        return value;
    }

    private static boolean booleanValue(Map<String, String> values, String key, boolean fallback) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(raw.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw.trim())) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be true or false");
    }

    private static int integer(Map<String, String> values, String key, int fallback, int minimum, int maximum) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
        return bounded(key, parsed, minimum, maximum);
    }

    private static int bounded(String label, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
