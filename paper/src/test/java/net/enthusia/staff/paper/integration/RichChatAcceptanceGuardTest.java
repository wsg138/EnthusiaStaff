package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rosewood.rosechat.api.chatbridge.OutboundChatMessage;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatRenderBridge;
import dev.rosewood.rosechat.api.chatbridge.RenderedOutboundChatMessage;
import dev.rosewood.rosechat.api.staff.ChannelClassification;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import net.enthusia.staff.api.chat.RichChatArtifact;
import net.enthusia.staff.protocol.ChatRenderMessages;
import org.junit.jupiter.api.Test;

/** Regression guards for the public rich-artifact boundary; no addon classes required. */
class RichChatAcceptanceGuardTest {
    private static final String SERVER_ID = "SMP";
    private static final long NOW = 1_800_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Test
    void staffChannelCannotInvokeRendererOrSendPublicFrames() {
        AtomicInteger renders = new AtomicInteger();
        AtomicInteger sends = new AtomicInteger();
        RichChatArtifactService artifacts = RichChatArtifactService.forTest(
                () -> request -> {
                    renders.incrementAndGet();
                    return CompletableFuture.completedFuture(List.of());
                }, CLOCK);
        AtomicReference<OutboundChatRenderBridge> bridge = new AtomicReference<>();
        try (var relay = relay(bridge, artifacts)) {
            relay.bindChannelForTest(new Object(), () -> true,
                    (id, type, json, timeout) -> { sends.incrementAndGet(); return true; });
            assertThrows(IllegalStateException.class,
                    () -> bridge.get().publish(rendered(ChannelClassification.STAFF)));
            assertEquals(0, renders.get());
            assertEquals(0, sends.get());
        }
    }

    @Test
    void failedRendererFallsBackToText() throws Exception {
        assertTextOnly(RichChatArtifactService.forTest(
                () -> request -> CompletableFuture.failedFuture(
                        new IllegalStateException("image generation failed")), CLOCK));
    }

    @Test
    void aggregateOversizeFallsBackToText() throws Exception {
        assertTextOnly(RichChatArtifactService.forTest(
                () -> request -> CompletableFuture.completedFuture(List.of(
                        artifact("One.png"), artifact("Two.png"))), CLOCK));
    }

    @Test
    void rendererTimeoutCancelsFutureAndPreservesText() throws Exception {
        CompletableFuture<List<RichChatArtifact>> pending = new CompletableFuture<>();
        assertTextOnly(RichChatArtifactService.forTest(() -> request -> pending, CLOCK));
        assertTrue(pending.isCancelled());
    }

    @Test
    void lookupRenderAndNullStageFailuresAreIsolated() {
        var message = RoseChatOutboundRenderBridgeIntegration.toWire(
                SERVER_ID, rendered(ChannelClassification.PUBLIC));
        assertTrue(RichChatArtifactService.forTest(
                () -> { throw new IllegalStateException("lookup"); }, CLOCK)
                .render(SERVER_ID, message).isEmpty());
        assertTrue(RichChatArtifactService.forTest(
                () -> request -> { throw new IllegalStateException("render"); }, CLOCK)
                .render(SERVER_ID, message).isEmpty());
        assertTrue(RichChatArtifactService.forTest(
                () -> request -> null, CLOCK).render(SERVER_ID, message).isEmpty());
    }

    @Test
    void parallelArtifactRequestsKeepEventIdentitySeparate() {
        RichChatArtifactService service = RichChatArtifactService.forTest(
                () -> request -> CompletableFuture.completedFuture(List.of(
                        new RichChatArtifact(
                                RichChatArtifact.Kind.ITEM, 0,
                                "Event-" + request.eventId().toString().substring(0, 8) + ".png",
                                "image/png", "Shared item", new byte[] {1}))), CLOCK);
        var messages = IntStream.range(0, 12)
                .mapToObj(index -> RoseChatOutboundRenderBridgeIntegration.toWire(
                        SERVER_ID, rendered(ChannelClassification.PUBLIC)))
                .toList();
        var requests = messages.stream()
                .map(message -> CompletableFuture.supplyAsync(() -> service.render(SERVER_ID, message)))
                .toList();

        for (int index = 0; index < messages.size(); index++) {
            var message = messages.get(index);
            var bundle = requests.get(index).join().orElseThrow();
            assertEquals(message.eventId(), bundle.eventId());
            assertEquals(message.logicalChannelId(), bundle.logicalChannelId());
            assertEquals("Event-" + message.eventId().toString().substring(0, 8) + ".png",
                    bundle.artifacts().getFirst().filename());
        }
    }

    private static void assertTextOnly(RichChatArtifactService artifacts) throws Exception {
        AtomicReference<OutboundChatRenderBridge> bridge = new AtomicReference<>();
        CountDownLatch received = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();
        AtomicReference<String> type = new AtomicReference<>();
        try (var relay = relay(bridge, artifacts)) {
            relay.bindChannelForTest(new Object(), () -> true, (id, frameType, json, timeout) -> {
                count.incrementAndGet();
                type.set(frameType);
                received.countDown();
                return true;
            });
            bridge.get().publish(rendered(ChannelClassification.PUBLIC));
            assertTrue(received.await(4, TimeUnit.SECONDS));
            assertEquals(1, count.get());
            assertEquals(ChatRenderMessages.RENDERED, type.get());
        }
    }

    private static RoseChatOutboundRenderBridgeIntegration relay(
            AtomicReference<OutboundChatRenderBridge> bridge, RichChatArtifactService artifacts
    ) {
        return new RoseChatOutboundRenderBridgeIntegration(
                SERVER_ID, CLOCK, installed -> {
                    bridge.set(installed);
                    return () -> { };
                }, 8, artifacts);
    }

    private static RichChatArtifact artifact(String name) {
        return new RichChatArtifact(
                RichChatArtifact.Kind.INVENTORY, 0, name,
                "image/png", "Shared inventory", new byte[230_000]);
    }

    private static RenderedOutboundChatMessage rendered(ChannelClassification classification) {
        UUID id = UUID.randomUUID();
        OutboundChatMessage original = new OutboundChatMessage(
                id, "rosechat-mc-" + id, "rosechat-canonical-" + id,
                NOW, NOW + 30_000, "global", classification,
                OutboundChatMessage.Origin.MINECRAFT, UUID.randomUUID(),
                "Player", "hello [item]");
        return new RenderedOutboundChatMessage(
                original, "hello [item]", "**hello [item]**",
                "{\"text\":\"hello [item]\"}", "Player: hello [item]",
                "**Player:** hello [item]", "{\"text\":\"Player: hello [item]\"}");
    }
}
