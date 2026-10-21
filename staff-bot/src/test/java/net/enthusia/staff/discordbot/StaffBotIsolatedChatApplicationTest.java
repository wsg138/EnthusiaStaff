package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaffBotIsolatedChatApplicationTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String STAGING_CHANNEL = "1541286004298752091";
    private static final String CHAT_APP = "1650000000000000008";
    private static final String TEST_ROUTE = "TEST/global=" + STAGING_CHANNEL;

    @Test
    void onlyPrivateChatFileAndOptionalSmokeFlagAreAccepted() {
        var normal = StaffBotIsolatedChatApplication.parse(new String[]{"--chat=private.properties"});
        assertEquals(Path.of("private.properties"), normal.chatFile());
        assertFalse(normal.smokeTest());
        assertTrue(StaffBotIsolatedChatApplication.parse(
                new String[]{"--smoke-test", "--chat=private.properties"}).smokeTest());
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.parse(new String[]{"--environment=production"}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.parse(new String[]{"--chat=private", "--token-file=tp"}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.parse(new String[]{"--chat=one", "--chat=two"}));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.parse(new String[]{"--chat="}));
    }

    @Test
    void isolatedTransportHasDifferentFixedPeerIdentityFromProduction() {
        assertNotEquals(StaffBotChatBridgeConfiguration.PEER_ID,
                StaffBotChatTransport.ISOLATED_TEST_PEER_ID);
        assertEquals("STAFFBOT_TEST", StaffBotChatTransport.ISOLATED_TEST_PEER_ID);
    }

    @Test
    void invalidCliNeverStartsAnyDiscordGateway() {
        assertEquals(2, StaffBotIsolatedChatApplication.run(
                new String[]{"--environment=production"}));
    }

    @Test
    void onlyOutboundTestGlobalToPinnedPrivateChannelIsAccepted() {
        var selected = StaffBotIsolatedChatApplication.select(validValues());
        assertEquals(StaffBotChatBridgeConfiguration.Mode.SHADOW, selected.bridge().mode());
        assertEquals(1, selected.bridge().routes().size());
        assertTrue(selected.bridge().ingressRoutes().isEmpty());
        assertEquals(Long.parseLong(CHAT_APP), selected.publicApp().applicationId());
    }

    @Test
    void refusesProductionOrAdditionalOutboundRoute() {
        var values = validValues();
        values.put(StaffBotChatBridgeConfiguration.ROUTES_ENV, "SMP/global=" + STAGING_CHANNEL);
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.select(values));
        values.put(StaffBotChatBridgeConfiguration.ROUTES_ENV,
                TEST_ROUTE + ";SMP/global=" + STAGING_CHANNEL);
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.select(values));
    }

    @Test
    void refusesDiscordIngressAndAuthoritativeMode() {
        var values = validValues();
        values.put(StaffBotChatBridgeConfiguration.INGRESS_ROUTES_ENV,
                STAGING_CHANNEL + "=TEST/global");
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.select(values));
        var authoritative = validValues();
        authoritative.put(StaffBotChatBridgeConfiguration.MODE_ENV, "AUTHORITATIVE");
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.select(authoritative));
    }

    @Test
    void rejectsStaffApplicationIdentitiesAndMissingPublicChatToken() {
        var values = validValues();
        values.put(PublicChatDiscordConfiguration.APPLICATION_ID_ENV,
                Long.toString(StaffBotEnvironment.PRODUCTION.applicationId()));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.select(values));
        values.put(PublicChatDiscordConfiguration.APPLICATION_ID_ENV,
                Long.toString(StaffBotEnvironment.STAGING.applicationId()));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.select(values));
        var withoutToken = validValues();
        withoutToken.remove(PublicChatDiscordConfiguration.TOKEN_ENV);
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.select(withoutToken));
    }

    @Test
    void refusesBridgeWithoutAuthenticatedVelocitySecrets() {
        var values = validValues();
        values.remove(StaffBotChatBridgeConfiguration.CLIENT_HMAC_ENV);
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotIsolatedChatApplication.select(values));
    }

    private static Map<String, String> validValues() {
        Map<String, String> values = new HashMap<>();
        values.put(StaffBotChatBridgeConfiguration.MODE_ENV, "SHADOW");
        values.put(StaffBotChatBridgeConfiguration.HOST_ENV, "velocity.internal");
        values.put(StaffBotChatBridgeConfiguration.PORT_ENV, "28765");
        values.put(StaffBotChatBridgeConfiguration.CLIENT_HMAC_ENV, KEY);
        values.put(StaffBotChatBridgeConfiguration.PROXY_HMAC_ENV, KEY);
        values.put(StaffBotChatBridgeConfiguration.TRUST_STORE_ENV, "synthetic-trust.p12");
        values.put(StaffBotChatBridgeConfiguration.TRUST_STORE_ACCESS_ENV, "not-a-real-password");
        values.put(StaffBotChatBridgeConfiguration.ROUTES_ENV, TEST_ROUTE);
        values.put(PublicChatDiscordConfiguration.TOKEN_ENV, "SYNTHETIC_NOT_A_DISCORD_TOKEN");
        values.put(PublicChatDiscordConfiguration.APPLICATION_ID_ENV, CHAT_APP);
        return values;
    }
}
