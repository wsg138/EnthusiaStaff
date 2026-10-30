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
    private static final String BACKEND_ID = "SMP";
    private static final String PROXY_ID = "VELOCITY";
    private static final String EXECUTE = "execute";
    private static final String GLOBAL_REGION_SCHEDULER = "getGlobalRegionScheduler";

    @Test
    void productionIngressUsesGlobalRegionSchedulerNotLegacyBukkitScheduler() {
        AtomicReference<Runnable> globalTask = new AtomicReference<>();
        AtomicInteger globalDispatches = new AtomicInteger();
        Plugin plugin = (Plugin) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(), new Class<?>[]{Plugin.class},
                (self, method, args) -> {
                    throw new AssertionError("Plugin should be passed through without calls: " + method.getName());
                });
        GlobalRegionScheduler global = (GlobalRegionScheduler) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{GlobalRegionScheduler.class},
                (self, method, args) -> {
                    if (!EXECUTE.equals(method.getName())) {
                        throw new AssertionError("Unexpected global scheduler call " + method.getName());
                    }
                    assertSame(plugin, args[0]);
                    globalDispatches.incrementAndGet();
                    globalTask.set((Runnable) args[1]);
                    return null;
                });
        Server server = (Server) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(), new Class<?>[]{Server.class},
                (self, method, args) -> {
                    if (!GLOBAL_REGION_SCHEDULER.equals(method.getName())) {
                        throw new AssertionError("Legacy or unexpected scheduler: " + method.getName());
                    }
                    return global;
                });
        RoseChatInboundBridgeIntegration.Scheduler scheduler =
                RoseChatInboundBridgeIntegration.globalIngressScheduler(server, plugin);
        AtomicInteger executions = new AtomicInteger();

        scheduler.execute(executions::incrementAndGet);

        assertEquals(1, globalDispatches.get());
        assertEquals(0, executions.get(), "Ingress should not run on the caller thread");
        assertNotNull(globalTask.get());
        globalTask.get().run();
        assertEquals(1, executions.get());
    }

    @Test
    void schedulingFailureReleasesQueueReservationForRetry() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<Runnable> deferred = new AtomicReference<>();
        RoseChatInboundBridgeIntegration bridge = new RoseChatInboundBridgeIntegration(
                BACKEND_ID, CLOCK,
                callback -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalStateException("scheduler disabled");
                    }
                    deferred.set(callback);
                },
                ignored -> true,
                1, 32
        );
        ChatBridgeInboundMessage message = message(BACKEND_ID, NOW, NOW + 30_000);
        ProtocolEnvelope envelope = envelope(PROXY_ID, message, message.eventId());

        assertFalse(bridge.accept(PROXY_ID, envelope));
        assertTrue(bridge.accept(PROXY_ID, envelope),
                "A failed scheduling attempt must not consume the event dedupe or queue slot");
        assertNotNull(deferred.get());
        deferred.get().run();
        bridge.close();
    }

    @Test
    void admitsAuthenticatedTargetedMessageThenDispatchesOnScheduledPath() {
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        AtomicReference<InboundChatMessage> delivered = new AtomicReference<>();
        RoseChatInboundBridgeIntegration bridge = new RoseChatInboundBridgeIntegration(
                BACKEND_ID,
                CLOCK,
                scheduled::set,
                message -> {
                    delivered.set(message);
                    return true;
                },
                8,
                32
        );
        ChatBridgeInboundMessage message = message(BACKEND_ID, NOW, NOW + 30_000);

        assertTrue(bridge.accept(PROXY_ID, envelope(PROXY_ID, message, message.eventId())));
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
                BACKEND_ID,
                CLOCK,
                ignored -> { },
                ignored -> true,
                8,
                32
        );
        ChatBridgeInboundMessage valid = message(BACKEND_ID, NOW, NOW + 30_000);

        assertFalse(bridge.accept(PROXY_ID, envelope("OTHER", valid, valid.eventId())));
        assertFalse(bridge.accept(PROXY_ID, envelope(
                PROXY_ID, message("HUB", NOW, NOW + 30_000), UUID.randomUUID())));
        assertFalse(bridge.accept(PROXY_ID, envelope(
                PROXY_ID, message(BACKEND_ID, NOW - 30_000, NOW - 1), UUID.randomUUID())));
        assertFalse(bridge.accept(PROXY_ID, envelope(
                PROXY_ID, valid, UUID.randomUUID())));

        bridge.close();
    }

    @Test
    void duplicateIsAckedOnceAndQueueCapacityIsBounded() {
        AtomicInteger scheduled = new AtomicInteger();
        AtomicReference<Runnable> first = new AtomicReference<>();
        RoseChatInboundBridgeIntegration bridge = new RoseChatInboundBridgeIntegration(
                BACKEND_ID,
                CLOCK,
                task -> {
                    scheduled.incrementAndGet();
                    first.compareAndSet(null, task);
                },
                ignored -> true,
                1,
                32
        );
        ChatBridgeInboundMessage one = message(BACKEND_ID, NOW, NOW + 30_000);
        ChatBridgeInboundMessage two = message(BACKEND_ID, NOW, NOW + 30_000);
        ProtocolEnvelope firstEnvelope = envelope(PROXY_ID, one, one.eventId());

        assertTrue(bridge.accept(PROXY_ID, firstEnvelope));
        assertTrue(bridge.accept(PROXY_ID, firstEnvelope));
        assertFalse(bridge.accept(PROXY_ID, envelope(PROXY_ID, two, two.eventId())));
        assertEquals(1, scheduled.get());

        first.get().run();
        assertTrue(bridge.accept(PROXY_ID, envelope(PROXY_ID, two, two.eventId())));

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
