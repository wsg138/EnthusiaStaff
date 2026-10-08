package net.enthusia.staff.discordbot;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import net.enthusia.staff.common.security.SecretKeyMaterial;

/** Default-off configuration for the ephemeral Velocity <-> StaffBot chat relay. */
final class StaffBotChatBridgeConfiguration {
    static final String ENABLED_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ENABLED";
    static final String MODE_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE";
    static final String CUTOVER_ACK_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_CUTOVER_ACK";
    static final String MIGRATION_ACK_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MIGRATION_ACK";
    static final String HOST_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_HOST";
    static final String PORT_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_PORT";
    static final String CLIENT_HMAC_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_CLIENT_SECRET";
    static final String PROXY_HMAC_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_PROXY_SECRET";
    static final String TRUST_STORE_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_TRUST_STORE";
    static final String TRUST_STORE_ACCESS_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_TRUST_STORE_PASSWORD";
    static final String ROUTES_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ROUTES";
    static final String INGRESS_ROUTES_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_INGRESS_ROUTES";
    static final String QUEUE_CAPACITY_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_QUEUE_CAPACITY";
    static final String DEDUPE_CAPACITY_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_DEDUPE_CAPACITY";

    static final String PEER_ID = "STAFFBOT";
    static final String PROXY_ID = "VELOCITY";

    private static final String ENABLED_VALUE = "true";
    private static final String DISABLED_VALUE = "false";
    private static final String AUTHORITATIVE_ACK = "I_ACKNOWLEDGE_DISCORDSRV_CHAT_CUTOVER";
    private static final String MIGRATION_ACK = "I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION";
    private static final int DEFAULT_PORT = 28_765;
    private static final int DEFAULT_QUEUE_CAPACITY = 256;
    private static final int DEFAULT_DEDUPE_CAPACITY = 4_096;
    private static final int MAX_QUEUE_CAPACITY = 4_096;
    private static final int MAX_DEDUPE_CAPACITY = 65_536;
    private static final Pattern ROUTE_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private final Mode mode;
    private final String host;
    private final int port;
    private final SecretKey clientKey;
    private final SecretKey proxyKey;
    private final Path trustStore;
    private final char[] trustStorePassword;
    private final Map<Route, Long> routes;
    private final Map<Long, Route> ingressRoutes;
    private final int queueCapacity;
    private final int dedupeCapacity;

    private StaffBotChatBridgeConfiguration(
            Mode mode,
            String host,
            int port,
            SecretConfiguration secrets,
            Map<Route, Long> routes,
            Map<Long, Route> ingressRoutes,
            QueueBounds bounds
    ) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.host = requireText(host, HOST_ENV);
        this.port = bounded(PORT_ENV, port, 1, 65_535);
        this.clientKey = secrets.clientKey();
        this.proxyKey = secrets.proxyKey();
        this.trustStore = secrets.trustStore().toAbsolutePath().normalize();
        this.trustStorePassword = secrets.trustStorePassword().clone();
        if (this.trustStorePassword.length == 0) {
            throw new IllegalArgumentException(TRUST_STORE_ACCESS_ENV + " is required");
        }
        this.routes = Map.copyOf(routes);
        if (this.routes.isEmpty()) {
            throw new IllegalArgumentException(ROUTES_ENV + " must contain at least one route");
        }
        this.ingressRoutes = Map.copyOf(ingressRoutes);
        validateIngressRoutes(this.routes, this.ingressRoutes);
        this.queueCapacity = bounded(
                QUEUE_CAPACITY_ENV, bounds.queueCapacity(), 1, MAX_QUEUE_CAPACITY);
        this.dedupeCapacity = bounded(
                DEDUPE_CAPACITY_ENV, bounds.dedupeCapacity(), 1, MAX_DEDUPE_CAPACITY);
    }

    static Optional<StaffBotChatBridgeConfiguration> fromEnvironment(
            StaffBotEnvironment environment,
            Map<String, String> values
    ) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(values, "values");
        Optional<Mode> selectedMode = mode(environment, values);
        if (selectedMode.isEmpty()) {
            return Optional.empty();
        }
        Mode mode = selectedMode.orElseThrow();

        OptionalLong pinnedChannel = pinnedChannel(environment, mode);
        if (environment == StaffBotEnvironment.PRODUCTION) {
            if (mode == Mode.AUTHORITATIVE) {
                if (!AUTHORITATIVE_ACK.equals(values.get(CUTOVER_ACK_ENV))) {
                    throw new IllegalArgumentException(
                            CUTOVER_ACK_ENV + " must explicitly acknowledge production chat cutover");
                }
            } else if (mode == Mode.SHADOW) {
                if (!MIGRATION_ACK.equals(values.get(MIGRATION_ACK_ENV))) {
                    throw new IllegalArgumentException(
                            MIGRATION_ACK_ENV + " must explicitly acknowledge production shadow migration");
                }
            }
        }

        String host = requireText(values.get(HOST_ENV), HOST_ENV);
        int port = integer(values.get(PORT_ENV), DEFAULT_PORT, PORT_ENV, 1, 65_535);
        Map<Route, Long> outboundRoutes = routes(
                requireText(values.get(ROUTES_ENV), ROUTES_ENV), pinnedChannel);
        Map<Long, Route> inboundRoutes = ingressRoutes(
                values.get(INGRESS_ROUTES_ENV), pinnedChannel, outboundRoutes);
        if (mode == Mode.AUTHORITATIVE) {
            java.util.Set<Long> outboundChannels = java.util.Set.copyOf(outboundRoutes.values());
            if (!inboundRoutes.keySet().equals(outboundChannels)) {
                throw new IllegalArgumentException(
                        "AUTHORITATIVE Discord chat bridge requires one replacement ingress mapping "
                                + "for every routed Discord channel"
                );
            }
        }
        SecretConfiguration secrets = secretConfiguration(values);
        try {
            return Optional.of(new StaffBotChatBridgeConfiguration(
                    mode,
                    host,
                    port,
                    secrets,
                    outboundRoutes,
                    inboundRoutes,
                    queueBounds(values)
            ));
        } finally {
            secrets.clearPassword();
        }
    }

    private static Optional<Mode> mode(
            StaffBotEnvironment environment,
            Map<String, String> values
    ) {
        String rawMode = values.get(MODE_ENV);
        boolean legacyEnabled = legacyEnabled(values);
        if (rawMode == null || rawMode.isBlank()) {
            return legacyMode(environment, legacyEnabled);
        }

        Mode selected = Mode.parse(rawMode);
        validateLegacyMode(values, selected, legacyEnabled);
        return selected == Mode.DISABLED ? Optional.empty() : Optional.of(selected);
    }

    private static boolean legacyEnabled(Map<String, String> values) {
        String legacyValue = values.get(ENABLED_ENV);
        return legacyValue != null && !legacyValue.isBlank() && enabled(legacyValue);
    }

    private static Optional<Mode> legacyMode(
            StaffBotEnvironment environment,
            boolean legacyEnabled
    ) {
        if (!legacyEnabled) {
            return Optional.empty();
        }
        if (environment != StaffBotEnvironment.STAGING) {
            throw new IllegalArgumentException(
                    "production chat bridge requires explicit " + MODE_ENV + "=AUTHORITATIVE");
        }
        return Optional.of(Mode.SHADOW);
    }

    private static void validateLegacyMode(
            Map<String, String> values,
            Mode selected,
            boolean legacyEnabled
    ) {
        String legacyValue = values.get(ENABLED_ENV);
        if (legacyValue == null || legacyValue.isBlank()) {
            return;
        }
        boolean modeEnabled = selected != Mode.DISABLED;
        if (legacyEnabled != modeEnabled) {
            throw new IllegalArgumentException(
                    ENABLED_ENV + " conflicts with explicit " + MODE_ENV);
        }
    }

    private static OptionalLong pinnedChannel(
            StaffBotEnvironment environment,
            Mode mode
    ) {
        if (environment != StaffBotEnvironment.STAGING
                && !(environment == StaffBotEnvironment.PRODUCTION && mode == Mode.SHADOW)) {
            return OptionalLong.empty();
        }
        OptionalLong stagingChannel = StaffBotEnvironment.STAGING.testChannelId();
        if (stagingChannel.isEmpty()) {
            throw new IllegalArgumentException("shadow Discord chat bridge requires a pinned test channel");
        }
        return stagingChannel;
    }

    private static SecretConfiguration secretConfiguration(Map<String, String> values) {
        return new SecretConfiguration(
                SecretKeyMaterial.hmacSha256FromBase64(
                        requireText(values.get(CLIENT_HMAC_ENV), CLIENT_HMAC_ENV)),
                SecretKeyMaterial.hmacSha256FromBase64(
                        requireText(values.get(PROXY_HMAC_ENV), PROXY_HMAC_ENV)),
                Path.of(requireText(values.get(TRUST_STORE_ENV), TRUST_STORE_ENV)),
                requireText(values.get(TRUST_STORE_ACCESS_ENV), TRUST_STORE_ACCESS_ENV).toCharArray()
        );
    }

    private static QueueBounds queueBounds(Map<String, String> values) {
        return new QueueBounds(
                integer(
                        values.get(QUEUE_CAPACITY_ENV),
                        DEFAULT_QUEUE_CAPACITY,
                        QUEUE_CAPACITY_ENV,
                        1,
                        MAX_QUEUE_CAPACITY),
                integer(
                        values.get(DEDUPE_CAPACITY_ENV),
                        DEFAULT_DEDUPE_CAPACITY,
                        DEDUPE_CAPACITY_ENV,
                        1,
                        MAX_DEDUPE_CAPACITY)
        );
    }

    private static boolean enabled(String value) {
        if (value == null || value.isBlank() || DISABLED_VALUE.equalsIgnoreCase(value.trim())) {
            return false;
        }
        if (ENABLED_VALUE.equalsIgnoreCase(value.trim())) {
            return true;
        }
        throw new IllegalArgumentException(ENABLED_ENV + " must be true or false");
    }

    private static Map<Route, Long> routes(String raw, OptionalLong pinnedChannel) {
        Map<Route, Long> parsed = new LinkedHashMap<>();
        for (String entry : raw.split(";", -1)) {
            int separator = entry.indexOf('=');
            if (separator < 1 || separator != entry.lastIndexOf('=')) {
                throw new IllegalArgumentException(ROUTES_ENV + " must use server/channel=discordChannel entries");
            }
            Route route = route(entry.substring(0, separator).trim());
            long channelId = positiveLong(entry.substring(separator + 1).trim(), ROUTES_ENV);
            if (pinnedChannel.isPresent() && channelId != pinnedChannel.getAsLong()) {
                throw new IllegalArgumentException(
                        "staging Discord chat bridge routes must target the pinned staging channel");
            }
            if (parsed.putIfAbsent(route, channelId) != null) {
                throw new IllegalArgumentException(ROUTES_ENV + " contains a duplicate route");
            }
        }
        return Map.copyOf(parsed);
    }

    private static Map<Long, Route> ingressRoutes(
            String raw,
            OptionalLong pinnedChannel,
            Map<Route, Long> outboundRoutes
    ) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<Long, Route> parsed = new LinkedHashMap<>();
        for (String entry : raw.split(";", -1)) {
            int separator = entry.indexOf('=');
            if (separator < 1 || separator != entry.lastIndexOf('=')) {
                throw new IllegalArgumentException(
                        INGRESS_ROUTES_ENV + " must use discordChannel=server/channel entries");
            }
            long channelId = positiveLong(entry.substring(0, separator).trim(), INGRESS_ROUTES_ENV);
            if (pinnedChannel.isPresent() && channelId != pinnedChannel.getAsLong()) {
                throw new IllegalArgumentException(
                        "staging Discord chat ingress must use the pinned staging channel");
            }
            Route route = route(entry.substring(separator + 1).trim());
            Long outboundChannel = outboundRoutes.get(route);
            if (outboundChannel == null || outboundChannel.longValue() != channelId) {
                throw new IllegalArgumentException(
                        INGRESS_ROUTES_ENV + " must select an existing symmetric outbound route");
            }
            if (parsed.putIfAbsent(channelId, route) != null) {
                throw new IllegalArgumentException(INGRESS_ROUTES_ENV + " contains a duplicate Discord channel");
            }
        }
        return Map.copyOf(parsed);
    }

    private static void validateIngressRoutes(
            Map<Route, Long> outboundRoutes,
            Map<Long, Route> inboundRoutes
    ) {
        for (Map.Entry<Long, Route> entry : inboundRoutes.entrySet()) {
            Long outboundChannel = outboundRoutes.get(entry.getValue());
            if (outboundChannel == null || !outboundChannel.equals(entry.getKey())) {
                throw new IllegalArgumentException("Discord chat ingress route is not symmetric");
            }
        }
    }

    private static Route route(String raw) {
        int separator = raw.indexOf('/');
        if (separator < 1 || separator != raw.lastIndexOf('/') || separator == raw.length() - 1) {
            throw new IllegalArgumentException(ROUTES_ENV + " route keys must use server/channel");
        }
        return new Route(
                routeToken(raw.substring(0, separator), "server"),
                routeToken(raw.substring(separator + 1), "channel"));
    }

    private static String routeToken(String value, String label) {
        String normalized = value.trim();
        if (!ROUTE_TOKEN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(ROUTES_ENV + " contains an invalid " + label + " token");
        }
        return normalized;
    }

    private static long positiveLong(String value, String label) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(label + " channel ids must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " channel ids must be numeric", exception);
        }
    }

    private static int integer(
            String raw,
            int fallback,
            String label,
            int minimum,
            int maximum
    ) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return bounded(label, Integer.parseInt(raw.trim()), minimum, maximum);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be an integer", exception);
        }
    }

    private static int bounded(String label, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    Mode mode() {
        return mode;
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    SecretKey clientKey() {
        return clientKey;
    }

    SecretKey proxyKey() {
        return proxyKey;
    }

    Path trustStore() {
        return trustStore;
    }

    char[] trustStorePassword() {
        return trustStorePassword.clone();
    }

    Map<Route, Long> routes() {
        return routes;
    }

    Map<Long, Route> ingressRoutes() {
        return ingressRoutes;
    }

    int queueCapacity() {
        return queueCapacity;
    }

    int dedupeCapacity() {
        return dedupeCapacity;
    }

    @Override
    public String toString() {
        return "StaffBotChatBridgeConfiguration[mode=" + mode
                + ", host=" + host
                + ", port=" + port
                + ", routes=" + routes.keySet()
                + ", ingressRoutes=" + ingressRoutes
                + ", queueCapacity=" + queueCapacity
                + ", dedupeCapacity=" + dedupeCapacity
                + ", clientKey=<redacted>, proxyKey=<redacted>, trustStorePassword=<redacted>]";
    }

    enum Mode {
        DISABLED,
        SHADOW,
        AUTHORITATIVE;

        static Mode parse(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(MODE_ENV + " is required");
            }
            try {
                return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        MODE_ENV + " must be DISABLED, SHADOW, or AUTHORITATIVE",
                        exception
                );
            }
        }
    }

    private record SecretConfiguration(
            SecretKey clientKey,
            SecretKey proxyKey,
            Path trustStore,
            char[] trustStorePassword
    ) {
        private SecretConfiguration {
            Objects.requireNonNull(clientKey, "clientKey");
            Objects.requireNonNull(proxyKey, "proxyKey");
            Objects.requireNonNull(trustStore, "trustStore");
            Objects.requireNonNull(trustStorePassword, "trustStorePassword");
        }

        private void clearPassword() {
            Arrays.fill(trustStorePassword, '\0');
        }
    }

    private record QueueBounds(int queueCapacity, int dedupeCapacity) {
    }

    record Route(String sourceServerId, String logicalChannelId) {
        Route {
            sourceServerId = routeToken(sourceServerId, "server");
            logicalChannelId = routeToken(logicalChannelId, "channel");
        }
    }
}
