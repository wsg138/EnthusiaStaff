package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.protocol.ChatArtifactMessages;
import net.enthusia.staff.protocol.ChatBridgeArtifact;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import net.enthusia.staff.protocol.ChatBridgeHealthMessage;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import net.enthusia.staff.protocol.ChatRenderMessages;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;

class VelocityChannelMessageRouterTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final String PAPER_SERVER = "SMP";
    private static final String ISOLATED_BACKEND = "TEST";
    private static final String STAFF_BOT_PEER = "STAFFBOT";
    private static final String LOGICAL_CHANNEL = "global";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Test
    void authenticatedAuxiliaryPeerCannotReachPaperApplicationHandler() {
        AtomicInteger delegated = new AtomicInteger();
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        VelocityDiscordChatIngressRelay inbound = new VelocityDiscordChatIngressRelay(
                Set.of(PAPER_SERVER), CLOCK, 8, 32);
        VelocityChatArtifactRelay artifactRelay =
                new VelocityChatArtifactRelay(CLOCK, 32);
        VelocityRenderedChatBridgeRelay renderedRelay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        VelocityChannelMessageRouter router = new VelocityChannelMessageRouter(
                Set.of(PAPER_SERVER),
                relay,
                artifactRelay,
                renderedRelay,
                inbound,
                healthRelay(),
                envelope -> {
                    delegated.incrementAndGet();
                    return true;
                }
        );

        assertFalse(router.handle(envelope(
                STAFF_BOT_PEER,
                UUID.randomUUID(),
                "PUNISHMENT_CREATED",
                "{}"
        )));
        assertFalse(router.handle(envelope(
                STAFF_BOT_PEER,
                UUID.randomUUID(),
                "STAFF_MODE_READY",
                "{}"
        )));
        assertFalse(router.handle(envelope(
                STAFF_BOT_PEER,
                UUID.randomUUID(),
                "TRANSFER_SNAPSHOT",
                "{}"
        )));
        assertEquals(0, delegated.get());

        relay.close();
        artifactRelay.close();
        renderedRelay.close();
        inbound.close();
    }

    @Test
    void staffBotHealthIsForwardedOnlyThroughTheEphemeralHealthRelay() {
        AtomicInteger delegated = new AtomicInteger();
        AtomicInteger forwarded = new AtomicInteger();
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        VelocityDiscordChatIngressRelay inbound = new VelocityDiscordChatIngressRelay(
                Set.of(PAPER_SERVER), CLOCK, 8, 32);
        VelocityChatArtifactRelay artifactRelay = new VelocityChatArtifactRelay(CLOCK, 32);
        VelocityRenderedChatBridgeRelay renderedRelay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        VelocityStaffBotChatHealthRelay healthRelay = new VelocityStaffBotChatHealthRelay(
                Set.of(PAPER_SERVER),
                CLOCK,
                (peerId, messageId, messageType, payload, timeout) -> {
                    assertEquals(PAPER_SERVER, peerId);
                    assertEquals(ChatBridgeMessages.HEALTH, messageType);
                    forwarded.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.completedFuture(
                            net.enthusia.staff.protocol.PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED);
                }
        );
        VelocityChannelMessageRouter router = new VelocityChannelMessageRouter(
                Set.of(PAPER_SERVER),
                relay,
                artifactRelay,
                renderedRelay,
                inbound,
                healthRelay,
                envelope -> {
                    delegated.incrementAndGet();
                    return true;
                }
        );
        ChatBridgeHealthMessage health = new ChatBridgeHealthMessage(true, NOW, NOW + 15_000L);

        assertTrue(router.handle(envelope(
                STAFF_BOT_PEER,
                UUID.randomUUID(),
                ChatBridgeMessages.HEALTH,
                ChatBridgeMessages.encodeHealth(health)
        )));
        assertEquals(1, forwarded.get());
        assertEquals(0, delegated.get());

        relay.close();
        artifactRelay.close();
        renderedRelay.close();
        inbound.close();
    }

    @Test
    void isolatedStaffBotHealthOnlyTargetsTestBackend() {
        java.util.Set<String> delivered = new java.util.HashSet<>();
        VelocityStaffBotChatHealthRelay healthRelay = new VelocityStaffBotChatHealthRelay(
                Set.of(ISOLATED_BACKEND, PAPER_SERVER), CLOCK,
                (peerId, id, type, body, timeout) -> {
                    delivered.add(peerId);
                    return java.util.concurrent.CompletableFuture.completedFuture(
                            net.enthusia.staff.protocol.PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED);
                });
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        VelocityChatArtifactRelay artifacts = new VelocityChatArtifactRelay(CLOCK, 32);
        VelocityRenderedChatBridgeRelay rendered =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        VelocityDiscordChatIngressRelay inbound =
                new VelocityDiscordChatIngressRelay(Set.of(ISOLATED_BACKEND, PAPER_SERVER), CLOCK, 8, 32);
        VelocityChannelMessageRouter router = new VelocityChannelMessageRouter(
                Set.of(ISOLATED_BACKEND, PAPER_SERVER), relay, artifacts, rendered, inbound,
                healthRelay, message -> true);
        try {
            var signal = new ChatBridgeHealthMessage(true, NOW, NOW + 15_000L);
            assertTrue(router.handle(envelope(
                    VelocityStaffBotPeerRoute.TEST_PEER,
                    UUID.randomUUID(), ChatBridgeMessages.HEALTH,
                    ChatBridgeMessages.encodeHealth(signal))));
            assertEquals(Set.of(ISOLATED_BACKEND), delivered);
            delivered.clear();
            assertTrue(router.handle(envelope(
                    STAFF_BOT_PEER,
                    UUID.randomUUID(), ChatBridgeMessages.HEALTH,
                    ChatBridgeMessages.encodeHealth(signal))));
            assertEquals(Set.of(PAPER_SERVER), delivered);
            assertFalse(router.handle(envelope(
                    VelocityStaffBotPeerRoute.TEST_PEER,
                    UUID.randomUUID(), "UNAUTHORIZED_FRAME", "{}")));
        } finally {
            relay.close();
            artifacts.close();
            rendered.close();
            inbound.close();
        }
    }

    @Test
    void staffBotCannotInjectChatFrameEvenWhenRelaySinkExists() {
        AtomicInteger deliveries = new AtomicInteger();
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        relay.installSink(message -> {
            deliveries.incrementAndGet();
            return true;
        });
        VelocityDiscordChatIngressRelay inbound = new VelocityDiscordChatIngressRelay(
                Set.of(PAPER_SERVER), CLOCK, 8, 32);
        VelocityChatArtifactRelay artifactRelay =
                new VelocityChatArtifactRelay(CLOCK, 32);
        VelocityRenderedChatBridgeRelay renderedRelay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        VelocityChannelMessageRouter router = new VelocityChannelMessageRouter(
                Set.of(PAPER_SERVER),
                relay,
                artifactRelay,
                renderedRelay,
                inbound,
                healthRelay(),
                envelope -> true
        );
        ChatBridgeOutboundMessage message = message(STAFF_BOT_PEER);

        assertFalse(router.handle(envelope(
                STAFF_BOT_PEER,
                message.eventId(),
                ChatBridgeMessages.OUTBOUND,
                ChatBridgeMessages.encodeOutbound(message)
        )));
        assertEquals(0, deliveries.get());

        relay.close();
        artifactRelay.close();
        renderedRelay.close();
        inbound.close();
    }

    @Test
    void paperChatUsesEphemeralRelayAndOtherFramesUseExistingHandler() throws Exception {
        AtomicInteger delegated = new AtomicInteger();
        CountDownLatch chatDelivered = new CountDownLatch(1);
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        relay.installSink(message -> {
            chatDelivered.countDown();
            return true;
        });
        VelocityDiscordChatIngressRelay inbound = new VelocityDiscordChatIngressRelay(
                Set.of(PAPER_SERVER), CLOCK, 8, 32);
        VelocityChatArtifactRelay artifactRelay =
                new VelocityChatArtifactRelay(CLOCK, 32);
        VelocityRenderedChatBridgeRelay renderedRelay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        VelocityChannelMessageRouter router = new VelocityChannelMessageRouter(
                Set.of(PAPER_SERVER),
                relay,
                artifactRelay,
                renderedRelay,
                inbound,
                healthRelay(),
                envelope -> {
                    delegated.incrementAndGet();
                    return true;
                }
        );
        ChatBridgeOutboundMessage chat = message(PAPER_SERVER);

        assertTrue(router.handle(envelope(
                PAPER_SERVER,
                chat.eventId(),
                ChatBridgeMessages.OUTBOUND,
                ChatBridgeMessages.encodeOutbound(chat)
        )));
        assertTrue(chatDelivered.await(2, TimeUnit.SECONDS));
        assertEquals(0, delegated.get());

        assertTrue(router.handle(envelope(
                PAPER_SERVER,
                UUID.randomUUID(),
                "PUNISHMENT_CREATED",
                "{}"
        )));
        assertEquals(1, delegated.get());

        relay.close();
        artifactRelay.close();
        renderedRelay.close();
        inbound.close();
    }

    @Test
    void artifactFramesArePaperOnlyAndAckOnlyAfterStaffBotSink() {
        AtomicInteger delegated = new AtomicInteger();
        AtomicInteger artifacts = new AtomicInteger();
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        VelocityChatArtifactRelay artifactRelay = new VelocityChatArtifactRelay(CLOCK, 32);
        artifactRelay.installSink(bundle -> {
            artifacts.incrementAndGet();
            return true;
        });
        VelocityRenderedChatBridgeRelay renderedRelay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        VelocityDiscordChatIngressRelay inbound = new VelocityDiscordChatIngressRelay(
                Set.of(PAPER_SERVER), CLOCK, 8, 32);
        VelocityChannelMessageRouter router = new VelocityChannelMessageRouter(
                Set.of(PAPER_SERVER),
                relay,
                artifactRelay,
                renderedRelay,
                inbound,
                healthRelay(),
                envelope -> {
                    delegated.incrementAndGet();
                    return true;
                }
        );
        ChatBridgeArtifactBundle bundle = artifactBundle(PAPER_SERVER);

        assertTrue(router.handle(new ProtocolEnvelope(
                1,
                ChatArtifactMessages.transportMessageId(bundle.eventId()),
                PAPER_SERVER,
                ChatArtifactMessages.ARTIFACTS,
                NOW,
                "nonce",
                ChatArtifactMessages.encode(bundle),
                "mac"
        )));
        assertEquals(1, artifacts.get());
        assertEquals(0, delegated.get());

        assertFalse(router.handle(new ProtocolEnvelope(
                1,
                ChatArtifactMessages.transportMessageId(bundle.eventId()),
                STAFF_BOT_PEER,
                ChatArtifactMessages.ARTIFACTS,
                NOW,
                "nonce",
                ChatArtifactMessages.encode(bundle),
                "mac"
        )));
        assertEquals(1, artifacts.get());
        assertEquals(0, delegated.get());

        relay.close();
        artifactRelay.close();
        renderedRelay.close();
        inbound.close();
    }

    @Test
    void renderedChatIsPaperOnlyAndNeverDelegatesToDurableHandler() throws Exception {
        AtomicInteger delegated = new AtomicInteger();
        CountDownLatch delivered = new CountDownLatch(1);
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        VelocityChatArtifactRelay artifactRelay =
                new VelocityChatArtifactRelay(CLOCK, 32);
        VelocityRenderedChatBridgeRelay renderedRelay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        renderedRelay.installSink(message -> {
            delivered.countDown();
            return true;
        });
        VelocityDiscordChatIngressRelay inbound = new VelocityDiscordChatIngressRelay(
                Set.of(PAPER_SERVER), CLOCK, 8, 32);
        VelocityChannelMessageRouter router = new VelocityChannelMessageRouter(
                Set.of(PAPER_SERVER),
                relay,
                artifactRelay,
                renderedRelay,
                inbound,
                healthRelay(),
                envelope -> {
                    delegated.incrementAndGet();
                    return true;
                }
        );
        ChatBridgeRenderedMessage rendered = renderedMessage(PAPER_SERVER);

        assertTrue(router.handle(new ProtocolEnvelope(
                1,
                rendered.eventId(),
                PAPER_SERVER,
                ChatRenderMessages.RENDERED,
                NOW,
                "nonce",
                ChatRenderMessages.encode(rendered),
                "mac"
        )));
        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals(0, delegated.get());

        assertFalse(router.handle(new ProtocolEnvelope(
                1,
                rendered.eventId(),
                STAFF_BOT_PEER,
                ChatRenderMessages.RENDERED,
                NOW,
                "nonce",
                ChatRenderMessages.encode(rendered),
                "mac"
        )));
        assertEquals(0, delegated.get());

        relay.close();
        artifactRelay.close();
        renderedRelay.close();
        inbound.close();
    }

    private static VelocityStaffBotChatHealthRelay healthRelay() {
        return new VelocityStaffBotChatHealthRelay(
                Set.of(PAPER_SERVER),
                CLOCK,
                (peerId, messageId, messageType, payload, timeout) ->
                        java.util.concurrent.CompletableFuture.completedFuture(
                                net.enthusia.staff.protocol.PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED)
        );
    }

    private static ChatBridgeArtifactBundle artifactBundle(String sourceServerId) {
        return new ChatBridgeArtifactBundle(
                UUID.randomUUID(),
                NOW,
                NOW + 30_000L,
                sourceServerId,
                LOGICAL_CHANNEL,
                List.of(new ChatBridgeArtifact(
                        ChatBridgeArtifact.Kind.ITEM,
                        3,
                        "Item.png",
                        "image/png",
                        "Shared item",
                        new byte[] {1, 2, 3}
                ))
        );
    }

    private static ChatBridgeRenderedMessage renderedMessage(String sourceServerId) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeRenderedMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                NOW,
                NOW + 30_000L,
                sourceServerId,
                LOGICAL_CHANNEL,
                UUID.randomUUID(),
                "Player",
                "hello",
                "hello",
                "**hello**",
                "{\"text\":\"hello\",\"color\":\"#12ABEF\"}",
                "[VIP] Player: hello",
                "**[VIP] Player:** hello",
                "{\"text\":\"[VIP] Player: hello\",\"color\":\"#12ABEF\"}"
        );
    }

    private static ChatBridgeOutboundMessage message(String sourceServerId) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeOutboundMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                NOW,
                NOW + 30_000L,
                sourceServerId,
                LOGICAL_CHANNEL,
                UUID.randomUUID(),
                "Player",
                "hello"
        );
    }

    private static ProtocolEnvelope envelope(
            String serverId,
            UUID messageId,
            String messageType,
            String payload
    ) {
        return new ProtocolEnvelope(
                1,
                messageId,
                serverId,
                messageType,
                NOW,
                "nonce",
                payload,
                "mac"
        );
    }
}
