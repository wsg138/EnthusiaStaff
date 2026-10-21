package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class VelocityStaffBotPeerRouteTest {
    private static final String TEST = "TEST";
    private static final String PEER = VelocityStaffBotPeerRoute.TEST_PEER;

    @Test
    void testRouteAndProductionRouteCannotCollide() {
        assertEquals("STAFFBOT_TEST", PEER);
        assertEquals(PEER, VelocityStaffBotPeerRoute.target(TEST));
        assertEquals(VelocityStaffBotChatSink.PEER_ID,
                VelocityStaffBotPeerRoute.target("SMP"));
        assertFalse(PEER.equals(VelocityStaffBotChatSink.PEER_ID));
    }

    @Test
    void testPeerIsNotARequiredDurableBackend() {
        assertEquals(Set.of(TEST, "SMP"),
                VelocityChannelPeerPolicy.requiredPaperBackends(
                        Set.of(TEST, "SMP", PEER, VelocityStaffBotChatSink.PEER_ID)));
        assertThrows(IllegalArgumentException.class,
                () -> VelocityChannelPeerPolicy.requiredPaperBackends(
                        Set.of(PEER, VelocityStaffBotChatSink.PEER_ID)));
    }

    @Test
    void testPeerMustUseTrustedProxyIdentity() {
        assertTrue(VelocityStaffBotPeerRoute.isStaffBotPeer(PEER));
        assertThrows(IllegalArgumentException.class,
                () -> VelocityChannelPeerPolicy.validateProxyIdentity(
                        "WRONG", Set.of(TEST, PEER)));
        VelocityChannelPeerPolicy.validateProxyIdentity("VELOCITY", Set.of(TEST, PEER));
    }
}
