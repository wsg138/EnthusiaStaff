package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.discord.DiscordChannelStatus;
import net.enthusia.staff.domain.discord.DiscordFailureOutcome;
import net.enthusia.staff.domain.discord.DiscordOutboxMessage;
import net.enthusia.staff.domain.ports.DiscordOutboxStore;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

final class DiscordOutboxWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    void successfulMessageIsRenderedSentAndCompletedByLeaseOwner() {
        DiscordOutboxMessage message = message(
                "reports",
                "REPORT_CREATED",
                "{\"reportId\":\"r-1\",\"reporterId\":\"private\",\"targetId\":\"target\"}"
        );
        FakeStore store = new FakeStore(List.of(message));
        AtomicReference<String> sent = new AtomicReference<>();
        DiscordWebhookTransport transport = (route, content) -> {
            assertEquals("reports", route.destination());
            sent.set(content);
            return DiscordWebhookTransport.Delivery.delivered();
        };

        worker(store, transport).runOnce();

        assertEquals(message.messageId(), store.deliveredId);
        assertEquals(store.claimOwner, store.deliveryOwner);
        assertNull(store.failedId);
        assertTrue(sent.get().contains("reportId=r-1"));
        assertFalse(sent.get().contains("private"));
    }

    @Test
    void poisonPayloadFailsWithoutCallingTransport() {
        DiscordOutboxMessage message = message("reports", "REPORT_CREATED", "not-json");
        FakeStore store = new FakeStore(List.of(message));
        AtomicInteger calls = new AtomicInteger();
        DiscordWebhookTransport transport = (route, content) -> {
            calls.incrementAndGet();
            return DiscordWebhookTransport.Delivery.delivered();
        };

        worker(store, transport).runOnce();

        assertEquals(0, calls.get());
        assertEquals(message.messageId(), store.failedId);
        assertEquals("PAYLOAD_REJECTED", store.failedCode);
        assertNull(store.deliveredId);
    }

    @Test
    void redirectFailureUsesDurableRetryPath() {
        DiscordOutboxMessage message = message("alerts", "DISCORD_CHANNEL_UNHEALTHY", "{\"destination\":\"reports\"}");
        FakeStore store = new FakeStore(List.of(message));
        DiscordWebhookTransport transport = (route, content) ->
                DiscordWebhookTransport.Delivery.failed("HTTP_REDIRECT_REJECTED");

        worker(store, transport).runOnce();

        assertEquals(message.messageId(), store.failedId);
        assertEquals("HTTP_REDIRECT_REJECTED", store.failedCode);
        assertTrue(store.failedAvailableAt.isAfter(NOW));
    }

    @Test
    void circuitOpenedByFirstFailureDefersLaterSameDestinationWithoutAnotherAttempt() {
        DiscordOutboxMessage first = message("reports", "REPORT_CREATED", "{\"reportId\":\"r-1\"}");
        DiscordOutboxMessage second = message("reports", "REPORT_CREATED", "{\"reportId\":\"r-2\"}");
        FakeStore store = new FakeStore(List.of(first, second));
        Instant openUntil = NOW.plusSeconds(300);
        store.failureOutcome = new DiscordFailureOutcome(false, true, Optional.of(openUntil));
        AtomicInteger calls = new AtomicInteger();
        DiscordWebhookTransport transport = (route, content) -> {
            calls.incrementAndGet();
            return DiscordWebhookTransport.Delivery.failed("HTTP_5XX");
        };

        worker(store, transport).runOnce();

        assertEquals(1, calls.get());
        assertEquals(List.of(second.messageId()), store.deferredIds);
        assertEquals(openUntil, store.deferredUntil);
    }

    private static DiscordOutboxWorker worker(FakeStore store, DiscordWebhookTransport transport) {
        return new DiscordOutboxWorker(
                null,
                null,
                NOPLogger.NOP_LOGGER,
                Clock.fixed(NOW, ZoneOffset.UTC),
                null,
                store,
                routes(),
                3,
                2,
                Duration.ofMinutes(5),
                Duration.ofSeconds(2),
                new DiscordEventRenderer(),
                transport
        );
    }

    private static Map<String, DiscordWebhookRoute> routes() {
        Set<String> hosts = Set.of("discord-staging.example.test");
        return Map.of(
                "punishments", DiscordWebhookRoute.approvedStaging(
                        "punishments", URI.create("https://discord-staging.example.test/punishments"), hosts),
                "reports", DiscordWebhookRoute.approvedStaging(
                        "reports", URI.create("https://discord-staging.example.test/reports"), hosts),
                "logs-staffmode", DiscordWebhookRoute.approvedStaging(
                        "logs-staffmode", URI.create("https://discord-staging.example.test/staffmode"), hosts),
                "alerts", DiscordWebhookRoute.approvedStaging(
                        "alerts", URI.create("https://discord-staging.example.test/alerts"), hosts)
        );
    }

    private static DiscordOutboxMessage message(String destination, String eventType, String payload) {
        return new DiscordOutboxMessage(UUID.randomUUID(), destination, eventType, payload, 0, NOW.minusSeconds(1));
    }

    private static final class FakeStore implements DiscordOutboxStore {
        private final List<DiscordOutboxMessage> messages;
        private final List<UUID> deferredIds = new ArrayList<>();
        private DiscordFailureOutcome failureOutcome = new DiscordFailureOutcome(false, false, Optional.empty());
        private String claimOwner;
        private UUID deliveredId;
        private String deliveryOwner;
        private UUID failedId;
        private String failedCode;
        private Instant failedAvailableAt;
        private Instant deferredUntil;

        private FakeStore(List<DiscordOutboxMessage> messages) {
            this.messages = List.copyOf(messages);
        }

        @Override
        public List<DiscordOutboxMessage> claimDue(String owner, int limit, Duration lease, Instant now) {
            claimOwner = owner;
            return messages;
        }

        @Override
        public boolean delivered(UUID messageId, String owner, Instant now) {
            deliveredId = messageId;
            deliveryOwner = owner;
            return true;
        }

        @Override
        public DiscordFailureOutcome failed(
                UUID messageId,
                String owner,
                String errorCode,
                Instant availableAt,
                Instant now,
                int maximumAttempts,
                int failureThreshold,
                Duration circuitDuration
        ) {
            failedId = messageId;
            failedCode = errorCode;
            failedAvailableAt = availableAt;
            return failureOutcome;
        }

        @Override
        public void deferWithoutAttempt(UUID messageId, String owner, Instant availableAt) {
            deferredIds.add(messageId);
            deferredUntil = availableAt;
        }

        @Override
        public List<DiscordChannelStatus> channelStatuses() {
            return List.of();
        }

        @Override
        public int retryDestination(String destination, Instant now, int maximumMessages) {
            return 0;
        }
    }
}
