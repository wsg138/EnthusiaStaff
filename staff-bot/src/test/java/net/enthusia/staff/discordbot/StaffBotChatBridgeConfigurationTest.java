package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaffBotChatBridgeConfigurationTest {
    private static final long STAGING_CHANNEL_ID = 1541286004298752091L;
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void bridgeIsDefaultOff() {
        assertTrue(StaffBotChatBridgeConfiguration.fromEnvironment(
                StaffBotEnvironment.STAGING, Map.of()).isEmpty());
        assertTrue(StaffBotChatBridgeConfiguration.fromEnvironment(
                StaffBotEnvironment.STAGING,
                Map.of(StaffBotChatBridgeConfiguration.ENABLED_ENV, "false")).isEmpty());
    }

    @Test
    void parsesExplicitStagingRoutesAndRedactsSecrets() {
        Map<String, String> values = enabledValues();
        StaffBotChatBridgeConfiguration configuration =
                StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, values).orElseThrow();

        assertEquals(StaffBotChatBridgeConfiguration.Mode.SHADOW, configuration.mode());
        assertEquals("velocity.internal", configuration.host());
        assertEquals(28_765, configuration.port());
        assertEquals(256, configuration.queueCapacity());
        assertEquals(4_096, configuration.dedupeCapacity());
        assertTrue(configuration.ingressRoutes().isEmpty());
        assertEquals(
                STAGING_CHANNEL_ID,
                configuration.routes().get(new StaffBotChatBridgeConfiguration.Route("SMP", "global")));
        assertEquals(
                STAGING_CHANNEL_ID,
                configuration.routes().get(new StaffBotChatBridgeConfiguration.Route("HUB", "global")));
        assertFalse(configuration.toString().contains(KEY));
        assertFalse(configuration.toString().contains("trust-password"));
        assertTrue(configuration.toString().contains("clientKey=<redacted>"));
    }

    @Test
    void parsesOneExplicitInboundRouteWithoutReversingAmbiguousOutboundRoutes() {
        Map<String, String> values = enabledValues();
        values.put(
                StaffBotChatBridgeConfiguration.INGRESS_ROUTES_ENV,
                STAGING_CHANNEL_ID + "=SMP/global");

        StaffBotChatBridgeConfiguration configuration =
                StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, values).orElseThrow();

        assertEquals(
                new StaffBotChatBridgeConfiguration.Route("SMP", "global"),
                configuration.ingressRoutes().get(STAGING_CHANNEL_ID));
    }

    @Test
    void inboundRouteMustBePinnedAndSymmetric() {
        Map<String, String> wrongChannel = enabledValues();
        wrongChannel.put(
                StaffBotChatBridgeConfiguration.INGRESS_ROUTES_ENV,
                "123456789=SMP/global");
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, wrongChannel));

        Map<String, String> missingOutbound = enabledValues();
        missingOutbound.put(
                StaffBotChatBridgeConfiguration.INGRESS_ROUTES_ENV,
                STAGING_CHANNEL_ID + "=SURVIVAL/global");
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, missingOutbound));
    }

    @Test
    void productionRejectsLegacyEnableWithoutExplicitAuthoritativeMode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, enabledValues()));
    }

    @Test
    void productionAuthoritativeModeRequiresExplicitCutoverAcknowledgement() {
        Map<String, String> values = productionValues();
        values.remove(StaffBotChatBridgeConfiguration.CUTOVER_ACK_ENV);

        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, values));
    }

    @Test
    void authoritativeModeRequiresReplacementInboundRoute() {
        Map<String, String> values = productionValues();

        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, values));
    }

    @Test
    void productionAuthoritativeModeAllowsExplicitSymmetricRoutes() {
        Map<String, String> values = productionValues();
        values.put(
                StaffBotChatBridgeConfiguration.INGRESS_ROUTES_ENV,
                "1650000000000000001=SMP/global;1650000000000000002=HUB/global");

        StaffBotChatBridgeConfiguration configuration =
                StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, values).orElseThrow();

        assertEquals(StaffBotChatBridgeConfiguration.Mode.AUTHORITATIVE, configuration.mode());
        assertEquals(
                1650000000000000001L,
                configuration.routes().get(
                        new StaffBotChatBridgeConfiguration.Route("SMP", "global")));
        assertEquals(
                new StaffBotChatBridgeConfiguration.Route("SMP", "global"),
                configuration.ingressRoutes().get(1650000000000000001L));
    }

    @Test
    void authoritativeModeRejectsPartialInboundChannelCoverage() {
        Map<String, String> values = productionValues();
        values.put(
                StaffBotChatBridgeConfiguration.INGRESS_ROUTES_ENV,
                "1650000000000000001=SMP/global");

        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, values));
    }

    @Test
    void productionShadowModeRequiresExplicitMigrationAcknowledgement() {
        Map<String, String> values = enabledValues();
        values.put(StaffBotChatBridgeConfiguration.MODE_ENV, "SHADOW");

        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, values));

        values.put(
                StaffBotChatBridgeConfiguration.MIGRATION_ACK_ENV,
                "I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION");
        StaffBotChatBridgeConfiguration configuration =
                StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, values).orElseThrow();

        assertEquals(StaffBotChatBridgeConfiguration.Mode.SHADOW, configuration.mode());
        assertEquals(
                STAGING_CHANNEL_ID,
                configuration.routes().get(new StaffBotChatBridgeConfiguration.Route("SMP", "global")));
        assertTrue(configuration.ingressRoutes().isEmpty());
    }

    @Test
    void productionShadowModeRejectsRoutesOutsidePinnedTestChannel() {
        Map<String, String> values = enabledValues();
        values.put(StaffBotChatBridgeConfiguration.MODE_ENV, "SHADOW");
        values.put(
                StaffBotChatBridgeConfiguration.MIGRATION_ACK_ENV,
                "I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION");
        values.put(
                StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP/global=1650000000000000001");

        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, values));
    }

    @Test
    void productionRejectsConflictingLegacyBoolean() {
        Map<String, String> conflict = productionValues();
        conflict.put(StaffBotChatBridgeConfiguration.ENABLED_ENV, "false");
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, conflict));
    }

    @Test
    void routeMustStayOnPinnedStagingChannel() {
        Map<String, String> values = enabledValues();
        values.put(
                StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP/global=123456789");

        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, values));
    }

    @Test
    void duplicateAndMalformedRoutesAreRejected() {
        Map<String, String> duplicate = enabledValues();
        duplicate.put(
                StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP/global=" + STAGING_CHANNEL_ID + ";SMP/global=" + STAGING_CHANNEL_ID);
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, duplicate));

        Map<String, String> malformed = enabledValues();
        malformed.put(
                StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP=" + STAGING_CHANNEL_ID);
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, malformed));
    }

    @Test
    void enabledBridgeRequiresValidSecretsAndBounds() {
        Map<String, String> invalidSecret = enabledValues();
        invalidSecret.put(StaffBotChatBridgeConfiguration.CLIENT_HMAC_ENV, "not-base64");
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, invalidSecret));

        Map<String, String> invalidPort = enabledValues();
        invalidPort.put(StaffBotChatBridgeConfiguration.PORT_ENV, "70000");
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, invalidPort));

        Map<String, String> invalidQueue = enabledValues();
        invalidQueue.put(StaffBotChatBridgeConfiguration.QUEUE_CAPACITY_ENV, "0");
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, invalidQueue));
    }

    private static Map<String, String> productionValues() {
        Map<String, String> values = enabledValues();
        values.put(StaffBotChatBridgeConfiguration.MODE_ENV, "AUTHORITATIVE");
        values.put(
                StaffBotChatBridgeConfiguration.CUTOVER_ACK_ENV,
                "I_ACKNOWLEDGE_DISCORDSRV_CHAT_CUTOVER");
        values.put(
                StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP/global=1650000000000000001;HUB/global=1650000000000000002");
        return values;
    }

    private static Map<String, String> enabledValues() {
        Map<String, String> values = new HashMap<>();
        values.put(StaffBotChatBridgeConfiguration.ENABLED_ENV, "true");
        values.put(StaffBotChatBridgeConfiguration.HOST_ENV, "velocity.internal");
        values.put(StaffBotChatBridgeConfiguration.CLIENT_HMAC_ENV, KEY);
        values.put(StaffBotChatBridgeConfiguration.PROXY_HMAC_ENV, KEY);
        values.put(StaffBotChatBridgeConfiguration.TRUST_STORE_ENV, "channel-trust.p12");
        values.put(StaffBotChatBridgeConfiguration.TRUST_STORE_ACCESS_ENV, "trust-password");
        values.put(
                StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP/global=" + STAGING_CHANNEL_ID + ";HUB/global=" + STAGING_CHANNEL_ID);
        return values;
    }
}
