package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import net.enthusia.staff.protocol.ChatArtifactMessages;
import net.enthusia.staff.protocol.ChatBridgeArtifact;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;

class StaffBotChatArtifactIngressTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private static final StaffBotChatBridgeConfiguration.Route ROUTE =
            new StaffBotChatBridgeConfiguration.Route("SMP", "global");

    @Test
    void cachesOnlyResumedExactRouteArtifactFrames() {
        StaffBotChatArtifactStore store = new StaffBotChatArtifactStore();
        StaffBotChatArtifactIngress ingress = new StaffBotChatArtifactIngress(
                Map.of(ROUTE, 1541286004298752091L),
                CLOCK,
                store
        );
        ChatBridgeArtifactBundle bundle = bundle("SMP", "global", NOW + 30_000);
        ProtocolEnvelope envelope = envelope(bundle);

        assertFalse(ingress.accept(envelope));
        ingress.resume();
        assertTrue(ingress.accept(envelope));
        assertEquals(1, store.size());
        assertEquals(1, store.consume(bundle.eventId(), "SMP", "global", NOW).size());

        assertFalse(ingress.accept(envelope(bundle("HUB", "global", NOW + 30_000))));
        ingress.close();
    }

    @Test
    void packetDecodingAcrossPauseAndResumeCannotRepopulateStaleCache() throws Exception {
        StaffBotChatArtifactStore store = new StaffBotChatArtifactStore();
        ChatBridgeArtifactBundle bundle = bundle("SMP", "global", NOW + 30_000);
        CountDownLatch decodingStarted = new CountDownLatch(1);
        CountDownLatch finishDecode = new CountDownLatch(1);
        StaffBotChatArtifactIngress ingress = new StaffBotChatArtifactIngress(
                Map.of(ROUTE, 1541286004298752091L), CLOCK, store,
                payload -> {
                    decodingStarted.countDown();
                    try {
                        if (!finishDecode.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test decoder release was not signaled");
                        }
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                    return Optional.of(bundle);
                }
        );
        ingress.resume();
        CompletableFuture<Boolean> pending = CompletableFuture.supplyAsync(
                () -> ingress.accept(envelope(bundle)));
        try {
            assertTrue(decodingStarted.await(5, TimeUnit.SECONDS));
            // The packet began in the old session and finishes in the new
            // session. It must not survive the previous session's cache clear.
            ingress.pause();
            ingress.resume();
        } finally {
            finishDecode.countDown();
        }
        assertFalse(pending.get(5, TimeUnit.SECONDS));
        assertEquals(0, store.size());
        assertEquals(0L, store.storedBytes());
        ingress.close();
    }

    @Test
    void rejectsWrongTransportIdentityPeerMalformedAndExpired() {
        StaffBotChatArtifactStore store = new StaffBotChatArtifactStore();
        StaffBotChatArtifactIngress ingress = new StaffBotChatArtifactIngress(
                Map.of(ROUTE, 1541286004298752091L),
                CLOCK,
                store
        );
        ingress.resume();
        ChatBridgeArtifactBundle valid = bundle("SMP", "global", NOW + 30_000);

        assertFalse(ingress.accept(new ProtocolEnvelope(
                1,
                valid.eventId(),
                StaffBotChatBridgeConfiguration.PROXY_ID,
                ChatArtifactMessages.ARTIFACTS,
                NOW,
                "nonce",
                ChatArtifactMessages.encode(valid),
                "mac"
        )));
        assertFalse(ingress.accept(new ProtocolEnvelope(
                1,
                ChatArtifactMessages.transportMessageId(valid.eventId()),
                "OTHER",
                ChatArtifactMessages.ARTIFACTS,
                NOW,
                "nonce",
                ChatArtifactMessages.encode(valid),
                "mac"
        )));
        assertFalse(ingress.accept(new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                StaffBotChatBridgeConfiguration.PROXY_ID,
                ChatArtifactMessages.ARTIFACTS,
                NOW,
                "nonce",
                "{}",
                "mac"
        )));

        StaffBotChatArtifactIngress expired = new StaffBotChatArtifactIngress(
                Map.of(ROUTE, 1541286004298752091L),
                Clock.fixed(Instant.ofEpochMilli(NOW + 30_001), ZoneOffset.UTC),
                new StaffBotChatArtifactStore()
        );
        expired.resume();
        assertFalse(expired.accept(envelope(bundle("SMP", "global", NOW + 30_000))));

        ingress.close();
        expired.close();
    }

    private static ProtocolEnvelope envelope(ChatBridgeArtifactBundle bundle) {
        return new ProtocolEnvelope(
                1,
                ChatArtifactMessages.transportMessageId(bundle.eventId()),
                StaffBotChatBridgeConfiguration.PROXY_ID,
                ChatArtifactMessages.ARTIFACTS,
                NOW,
                "nonce",
                ChatArtifactMessages.encode(bundle),
                "mac"
        );
    }

    private static ChatBridgeArtifactBundle bundle(
            String sourceServer,
            String logicalChannel,
            long expiresAt
    ) {
        return new ChatBridgeArtifactBundle(
                UUID.randomUUID(),
                NOW,
                expiresAt,
                sourceServer,
                logicalChannel,
                List.of(new ChatBridgeArtifact(
                        ChatBridgeArtifact.Kind.ITEM,
                        2,
                        "Item.png",
                        "image/png",
                        "Shared item",
                        new byte[] {1, 2, 3}
                ))
        );
    }
}
