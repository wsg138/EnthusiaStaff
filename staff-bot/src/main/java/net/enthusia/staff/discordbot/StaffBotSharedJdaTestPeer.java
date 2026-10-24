package net.enthusia.staff.discordbot;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default-off TEST peer in the ALREADY RUNNING public-chat Discord JDA session.
 * This creates no gateway and uses only the pinned private Discord channel.
 */
final class StaffBotSharedJdaTestPeer {
    static final String ENABLED = "ENTHUSIA_STAFF_BOT_TEST_CHAT_ENABLED";
    static final String CLIENT_SECRET = "ENTHUSIA_STAFF_BOT_TEST_CHAT_CLIENT_SECRET";
    private static final StaffBotChatBridgeConfiguration.Route TEST_ROUTE =
            new StaffBotChatBridgeConfiguration.Route("TEST", "global");

    private StaffBotSharedJdaTestPeer() {
    }

    static Optional<StaffBotChatBridgeConfiguration> select(
            StaffBotEnvironment environment,
            Optional<StaffBotChatBridgeConfiguration> primary,
            boolean privateChatFile,
            boolean hasExistingPublicChatGateway,
            Map<String, String> values
    ) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(values, "values");
        String flag = values.getOrDefault(ENABLED, "false").trim();
        if ("false".equalsIgnoreCase(flag)) {
            return Optional.empty();
        }
        if (!"true".equalsIgnoreCase(flag)) {
            throw new IllegalArgumentException("TEST chat enabled must be true or false");
        }
        if (environment != StaffBotEnvironment.PRODUCTION || !privateChatFile
                || !hasExistingPublicChatGateway || primary.isEmpty()) {
            throw new IllegalArgumentException(
                    "shared TEST peer requires production's existing private chat gateway");
        }
        StaffBotChatBridgeConfiguration main = primary.orElseThrow();
        if (main.mode() != StaffBotChatBridgeConfiguration.Mode.SHADOW
                || !main.ingressRoutes().isEmpty()
                || !main.routes().equals(Map.of(
                        new StaffBotChatBridgeConfiguration.Route("SMP", "global"),
                        StaffBotEnvironment.STAGING.testChannelId().orElseThrow()))) {
            throw new IllegalArgumentException(
                    "shared TEST peer requires distinct outbound-only primary SHADOW routes");
        }
        String secret = values.get(CLIENT_SECRET);
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("TEST peer requires its own HMAC secret");
        }
        Map<String, String> candidate = new HashMap<>(values);
        candidate.put(StaffBotChatBridgeConfiguration.MODE_ENV, "SHADOW");
        candidate.remove(StaffBotChatBridgeConfiguration.ENABLED_ENV);
        candidate.put(StaffBotChatBridgeConfiguration.ROUTES_ENV, "TEST/global="
                + StaffBotEnvironment.STAGING.testChannelId().orElseThrow());
        candidate.remove(StaffBotChatBridgeConfiguration.INGRESS_ROUTES_ENV);
        candidate.put(StaffBotChatBridgeConfiguration.CLIENT_HMAC_ENV, secret);
        StaffBotChatBridgeConfiguration test = StaffBotChatBridgeConfiguration
                .fromEnvironment(StaffBotEnvironment.STAGING, candidate).orElseThrow();

        byte[] mainKey = main.clientKey().getEncoded();
        byte[] testKey = test.clientKey().getEncoded();
        try {
            if (mainKey == null || testKey == null
                    || MessageDigest.isEqual(mainKey, testKey)) {
                throw new IllegalArgumentException("TEST and primary HMAC material must differ");
            }
        } finally {
            if (mainKey != null) {
                Arrays.fill(mainKey, (byte) 0);
            }
            if (testKey != null) {
                Arrays.fill(testKey, (byte) 0);
            }
        }
        if (!test.routes().keySet().equals(java.util.Set.of(TEST_ROUTE))
                || !test.ingressRoutes().isEmpty()) {
            throw new IllegalArgumentException("TEST peer must be TEST/global outbound only");
        }
        return Optional.of(test);
    }
}
