package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class ModerationReadApiServerBindingTest {
    @Test
    void privateReadApiUsesOnlyFixedLoopbackPort() {
        InetSocketAddress address = ModerationReadApiServer.bindAddress();

        assertEquals("127.0.0.1", address.getAddress().getHostAddress());
        assertEquals(8766, address.getPort());
    }
}
