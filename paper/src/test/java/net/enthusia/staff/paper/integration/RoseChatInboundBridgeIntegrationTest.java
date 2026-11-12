package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rosewood.rosechat.api.chatbridge.InboundChatMessage;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeInboundMessage;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class RoseChatInboundBridgeIntegrationTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Test
    void serverWideInboundUsesGlobalRegionSchedulerRatherThanLegacyBukkitScheduler() {
        AtomicReference<Runnable> captured = new AtomicReference<>();
        AtomicInteger scheduled = new AtomicInteger();
        Plugin plugin = (Plugin) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Plugin.class},
                (self, method, args) -> {
                    throw new AssertionError("Unexpected Plugin method: " + method.getName());
                });
        GlobalRegionScheduler global = (GlobalRegionScheduler) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{GlobalRegionScheduler.class},
                (self, method, args) -> {
                    assertEquals("execute", method.getName());
                    assertSame(plugin, args[0]);
                    captured.set((Runnable) args[1]);
                    scheduled.incrementAndGet();
                    return null;
                });
        Server server = (Server) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Server.class},
                (self, method, args) -> {
                    assertEquals("getGlobalRegionScheduler", method.getName(),
                            "Legacy Bukkit scheduler must never be used");
                    return global;
                });
        RoseChatInboundBridgeIntegration.Scheduler ingress =
                RoseChatInboundBridgeIntegration.globalIngressScheduler(server, plugin);
        AtomicInteger deliveries = new AtomicInteger();

        ingress.execute(deliveries::incrementAndGet);

        assertEquals(1, scheduled.get());
        assertEquals(0, deliveries.get(), "Ingress must not execute inline");
        assertNotNull(captured.get());
        captured.get().run();
        assertEquals(1, deliveries.get());
    }

    @Test
    void schedulingFailureReleasesQueueReservationForRetry() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<Runnable> deferred = new AtomicReference<>();
        RoseChatInboundBridgeIntegration bridge = new RoseChatInboundBridgeIntegration(
                "SMP", CLOCK,
                callback -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalStateException("scheduler rejected");
                    }
                    deferred.set(callback);
                },
                ignored -> true,
                1, 32
        );
        ChatBridgeInboundMessage message = message("SMP", NOW, NOW + 30_000);
        ProtocolEnvelope envelope = envelope("VELOCITY", message, message.eventId());

        assertFalse(bridge.accept("VELOCITY", envelope));
        assertTrue(bridge.accept("VELOCITY", envelope),
                "Rejected scheduler must not consume dedupe key or queue slot");
        assertNotNull(deferred.get());
        deferred.get().run();
        bridge.close();
    }

    @Test
    void admitsAuthenticatedTargetedMessageThenDispatchesOnScheduledPath() {
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        AtomicReference<InboundChatMessage> delivered = new AtomicReference<>();
        RoseChatInboundBridgeIntegration bridge = new RoseChatInboundBridgeIntegration(
                "SMP",
                CLOCK,
                scheduled::set,
                message -> {
                    delivered.set(message);
                    return true;
                },
                8,
                32
        );
        ChatBridgeInboundMessage message = message("SMP", NOW, NOW + 30_000);

        assertTrue(bridge.accept("VELOCITY", envelope("VELOCITY", message, message.eventId())));
        assertNotNull(scheduled.get());
        scheduled.get().run();

        assertEquals(message.eventId(), delivered.get().eventId());
        assertEquals(message.externalMessageId(), delivered.get().externalMessageId());
        assertEquals(message.canonicalMessageId(), delivered.get().canonicalMessageId());
        assertEquals(message.logicalChannelId(), delivered.get().logicalChannelId());
        assertEquals(message.displayName(), delivered.get().displayName());
        assertEquals(message.plainText(), delivered.get().plainText());

        bridge.close();
    }

    @Test
    void rejectsWrongProxyTargetExpiryAndEnvelopeIdentity() {
        RoseChatInboundBridgeIntegration bridge = new RoseChatInboundBridgeIntegration(
                "SMP",
                CLOCK,
                ignored -> { },
                ignored -> true,
                8,
                32
        );
        ChatBridgeInboundMessage valid = message("SMP", NOW, NOW + 30_000);

        assertFalse(bridge.accept("VELOCITY", envelope("OTHER", valid, valid.eventId())));
        assertFalse(bridge.accept("VELOCITY", envelope(
                "VELOCITY", message("HUB", NOW, NOW + 30_000), UUID.randomUUID())));
        assertFalse(bridge.accept("VELOCITY", envelope(
                "VELOCITY", message("SMP", NOW - 30_000, NOW - 1), UUID.randomUUID())));
        assertFalse(bridge.accept("VELOCITY", envelope(
                "VELOCITY", valid, UUID.randomUUID())));

        bridge.close();
    }

    @Test
    void duplicateIsAckedOnceAndQueueCapacityIsBounded() {
        AtomicInteger scheduled = new AtomicInteger();
        AtomicReference<Runnable> first = new AtomicReference<>();
        RoseChatInboundBridgeIntegration bridge = new RoseChatInboundBridgeIntegration(
                "SMP",
                CLOCK,
                task -> {
                    scheduled.incrementAndGet();
                    first.compareAndSet(null, task);
                },
                ignored -> true,
                1,
                32
        );
        ChatBridgeInboundMessage one = message("SMP", NOW, NOW + 30_000);
        ChatBridgeInboundMessage two = message("SMP", NOW, NOW + 30_000);
        ProtocolEnvelope firstEnvelope = envelope("VELOCITY", one, one.eventId());

        assertTrue(bridge.accept("VELOCITY", firstEnvelope));
        assertTrue(bridge.accept("VELOCITY", firstEnvelope));
        assertFalse(bridge.accept("VELOCITY", envelope("VELOCITY", two, two.eventId())));
        assertEquals(1, scheduled.get());

        first.get().run();
        assertTrue(bridge.accept("VELOCITY", envelope("VELOCITY", two, two.eventId())));

        bridge.close();
    }

    private static ChatBridgeInboundMessage message(
            String targetServer,
            long createdAt,
            long expiresAt
    ) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeInboundMessage(
                eventId,
                "discord-123456789",
                "discord-canonical-123456789",
                createdAt,
                expiresAt,
                1541286004298752091L,
                "123456789012345678",
                "DiscordUser",
                targetServer,
                "global",
                "hello"
        );
    }

    private static ProtocolEnvelope envelope(
            String serverId,
            ChatBridgeInboundMessage message,
            UUID messageId
    ) {
        return new ProtocolEnvelope(
                1,
                messageId,
                serverId,
                ChatBridgeMessages.INBOUND,
                NOW,
                "nonce",
                ChatBridgeMessages.encodeInbound(message),
                "mac"
        );
    }
}
