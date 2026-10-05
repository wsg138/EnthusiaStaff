package net.enthusia.staff.paper.commandbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class DiscordCommandBridgeEndpointTest {
    @Test
    void peerPolicyAllowsOnlyLoopbackOrPrivateNetworkAddresses() throws Exception {
        assertTrue(DiscordCommandBridgeEndpoint.privatePeer(InetAddress.getByName("127.0.0.1")));
        assertTrue(DiscordCommandBridgeEndpoint.privatePeer(InetAddress.getByName("10.20.30.40")));
        assertFalse(DiscordCommandBridgeEndpoint.privatePeer(InetAddress.getByName("8.8.8.8")));
    }

    @Test
    void bindSurfaceIsExplicitlyLimited() {
        assertEquals("127.0.0.1", DiscordCommandBridgeEndpoint.bindAddress("127.0.0.1", 8772)
                .getAddress().getHostAddress());
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordCommandBridgeEndpoint.bindAddress("192.168.1.10", 8772)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordCommandBridgeEndpoint.bindAddress("0.0.0.0", 0)
        );
    }
}
