package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class StaffBotSharedJdaTestPeerTest {
    private static final String PIN = "1541286004298752091";
    private static final String PRIMARY_KEY = key((byte) 1);
    private static final String TEST_KEY = key((byte) 7);

    private static String key(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static Map<String, String> values() {
        Map<String, String> v = new HashMap<>();
        v.put(StaffBotChatBridgeConfiguration.MODE_ENV, "SHADOW");
        v.put(StaffBotChatBridgeConfiguration.MIGRATION_ACK_ENV,
                "I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION");
        v.put(StaffBotChatBridgeConfiguration.HOST_ENV, "velocity.invalid");
        v.put(StaffBotChatBridgeConfiguration.PORT_ENV, "28765");
        v.put(StaffBotChatBridgeConfiguration.CLIENT_HMAC_ENV, PRIMARY_KEY);
        v.put(StaffBotChatBridgeConfiguration.PROXY_HMAC_ENV, PRIMARY_KEY);
        v.put(StaffBotChatBridgeConfiguration.TRUST_STORE_ENV, "fake.p12");
        v.put(StaffBotChatBridgeConfiguration.TRUST_STORE_ACCESS_ENV, "synthetic");
        v.put(StaffBotChatBridgeConfiguration.ROUTES_ENV, "SMP/global=" + PIN);
        return v;
    }

    private static Optional<StaffBotChatBridgeConfiguration> select(Map<String, String> v) {
        return StaffBotSharedJdaTestPeer.select(
                StaffBotEnvironment.PRODUCTION,
                StaffBotChatBridgeConfiguration.fromEnvironment(StaffBotEnvironment.PRODUCTION, v),
                true, true, v);
    }

    private static Map<String, String> enabled() {
        var v = values();
        v.put(StaffBotSharedJdaTestPeer.ENABLED, "true");
        v.put(StaffBotSharedJdaTestPeer.CLIENT_SECRET, TEST_KEY);
        return v;
    }

    @Test void defaultOff() { assertTrue(select(values()).isEmpty()); }

    @Test void fixedRouteNoIngressAndDistinctHmac() {
        var v = enabled();
        var test = select(v).orElseThrow();
        assertEquals(Map.of(new StaffBotChatBridgeConfiguration.Route("TEST", "global"),
                Long.parseLong(PIN)), test.routes());
        assertTrue(test.ingressRoutes().isEmpty());
        assertEquals(StaffBotChatBridgeConfiguration.Mode.SHADOW, test.mode());
        var primary = StaffBotChatBridgeConfiguration.fromEnvironment(
                StaffBotEnvironment.PRODUCTION, v).orElseThrow();
        assertFalse(Arrays.equals(primary.clientKey().getEncoded(), test.clientKey().getEncoded()));
    }

    @Test void missingDuplicateHmacAndInvalidToggleRejected() {
        var v = enabled();
        v.remove(StaffBotSharedJdaTestPeer.CLIENT_SECRET);
        assertThrows(IllegalArgumentException.class, () -> select(v));
        v.put(StaffBotSharedJdaTestPeer.CLIENT_SECRET, PRIMARY_KEY);
        assertThrows(IllegalArgumentException.class, () -> select(v));
        v.put(StaffBotSharedJdaTestPeer.CLIENT_SECRET, TEST_KEY);
        v.put(StaffBotSharedJdaTestPeer.ENABLED, "yes");
        assertThrows(IllegalArgumentException.class, () -> select(v));
    }

    @Test void refusesSeparateLoginAndTestRouteOnPrimary() {
        var v = enabled();
        var primary = StaffBotChatBridgeConfiguration.fromEnvironment(
                StaffBotEnvironment.PRODUCTION, v);
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotSharedJdaTestPeer.select(StaffBotEnvironment.STAGING,
                        primary, true, true, v));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotSharedJdaTestPeer.select(StaffBotEnvironment.PRODUCTION,
                        primary, false, true, v));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotSharedJdaTestPeer.select(StaffBotEnvironment.PRODUCTION,
                        primary, true, false, v));
        v.put(StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP/global=" + PIN + ";TEST/global=" + PIN);
        assertThrows(IllegalArgumentException.class, () -> select(v));
        v.put(StaffBotChatBridgeConfiguration.ROUTES_ENV, "HUB/global=" + PIN);
        assertThrows(IllegalArgumentException.class, () -> select(v));
    }

    @Test void ingressRejected() {
        var v = enabled();
        v.put(StaffBotChatBridgeConfiguration.INGRESS_ROUTES_ENV, PIN + "=SMP/global");
        assertThrows(IllegalArgumentException.class, () -> select(v));
    }

    @Test void bothLifecycleAndPauseOnFailure() {
        Stub primary = new Stub(), test = new Stub();
        var both = new StaffBotDualChatLifecycle(primary, test);
        both.start(); both.resume(); both.pause(); both.close();
        assertEquals("start,resume,pause,close", primary.log.toString());
        assertEquals(primary.log.toString(), test.log.toString());
        assertThrows(IllegalArgumentException.class,
                () -> new StaffBotDualChatLifecycle(primary, primary));
        Stub failing = new Stub();
        failing.fail = true;
        Stub first = new Stub();
        var composite = new StaffBotDualChatLifecycle(first, failing);
        assertThrows(IllegalStateException.class, composite::resume);
        assertEquals("resume,pause", first.log.toString());
    }

    private static final class Stub implements StaffBotChatLifecycle {
        final StringJoiner log = new StringJoiner(",");
        boolean fail;
        public void start() { log.add("start"); }
        public void resume() { if (fail) throw new IllegalStateException(); log.add("resume"); }
        public void pause() { log.add("pause"); }
        public void close() { log.add("close"); }
    }
}
