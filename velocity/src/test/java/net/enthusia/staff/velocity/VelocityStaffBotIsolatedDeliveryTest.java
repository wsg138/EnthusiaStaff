package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeArtifact;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import net.enthusia.staff.protocol.PersistentChannelServer;
import org.junit.jupiter.api.Test;

class VelocityStaffBotIsolatedDeliveryTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void allTestChatKindsUseIsolatedPeerWhileSmpUsesPrimaryPeer() {
        AtomicReference<String> peer = new AtomicReference<>();
        var plain = new VelocityStaffBotChatSink((id, event, type, data, timeout) -> {
            peer.set(id);
            return acknowledged();
        });
        var artifact = new VelocityStaffBotChatArtifactSink((id, event, type, data, timeout) -> {
            peer.set(id);
            return acknowledged();
        });
        var rendered = new VelocityStaffBotRenderedChatSink((id, event, type, data, timeout) -> {
            peer.set(id);
            return acknowledged();
        });

        for (String server : List.of("TEST", "SMP")) {
            String expected = VelocityStaffBotPeerRoute.target(server);
            assertTrue(plain.offer(plain(server)));
            assertEquals(expected, peer.get());
            assertTrue(artifact.offer(artifact(server)));
            assertEquals(expected, peer.get());
            assertTrue(rendered.offer(rendered(server)));
            assertEquals(expected, peer.get());
        }
    }

    private static CompletableFuture<PersistentChannelServer.DeliveryStatus> acknowledged() {
        return CompletableFuture.completedFuture(PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED);
    }

    private static ChatBridgeOutboundMessage plain(String server) {
        UUID id = UUID.randomUUID();
        return new ChatBridgeOutboundMessage(id, "mc-" + id, "canonical-" + id,
                NOW, NOW + 30_000L, server, "global", UUID.randomUUID(), "Player", "test");
    }

    private static ChatBridgeArtifactBundle artifact(String server) {
        return new ChatBridgeArtifactBundle(UUID.randomUUID(), NOW, NOW + 30_000L,
                server, "global", List.of(new ChatBridgeArtifact(
                        ChatBridgeArtifact.Kind.ITEM, 1, "test.png", "image/png",
                        "Item", new byte[]{1})));
    }

    private static ChatBridgeRenderedMessage rendered(String server) {
        UUID id = UUID.randomUUID();
        return new ChatBridgeRenderedMessage(id, "mc-" + id, "canonical-" + id,
                NOW, NOW + 30_000L, server, "global", UUID.randomUUID(),
                "Player", "test", "test", "test", "{}", "test", "test", "{}");
    }
}
