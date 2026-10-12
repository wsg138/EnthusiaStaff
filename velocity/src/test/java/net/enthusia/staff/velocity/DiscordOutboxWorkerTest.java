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
    private static final String REPORTS = "reports";
    private static final String ALERTS = "alerts";
    private static final String STAFF_LOGS = "logs-staffmode";
    private static final String REPORT_CREATED = "REPORT_CREATED";
    private static final String STAGING_HOST = "discord-staging.example.test";

    @Test
    void successfulMessageIsRenderedSentAndCompletedByLeaseOwner() {
        DiscordOutboxMessage message = message(
                REPORTS,
                REPORT_CREATED,
                "{\"reportId\":\"r-1\",\"reporterId\":\"private\",\"targetId\":\"target\"}"
        );
        FakeStore store = new FakeStore(List.of(message));
        AtomicReference<String> sent = new AtomicReference<>();
        DiscordWebhookTransport transport = (route, content) -> {
            assertEquals(REPORTS, route.destination());
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
    void retriesAreDeliveredWithoutAnotherRolePing() {
        DiscordOutboxMessage initial = message(ALERTS, "DISCORD_CHANNEL_UNHEALTHY",
                "{\"destination\":\"reports\"}");
        DiscordOutboxMessage retried = new DiscordOutboxMessage(UUID.randomUUID(), ALERTS,
                "DISCORD_CHANNEL_UNHEALTHY", "{\"destination\":\"reports\"}",
                2, NOW.minusSeconds(1));
        List<Boolean> pingFlags = new ArrayList<>();
        DiscordWebhookTransport transport = new DiscordWebhookTransport() {
            @Override
            public Delivery send(DiscordWebhookRoute route, String content) {
                throw new AssertionError("worker must pass the durable delivery attempt");
            }

            @Override
            public Delivery send(DiscordWebhookRoute route, String content, boolean allowRolePing) {
                assertEquals(ALERTS, route.destination());
                pingFlags.add(allowRolePing);
                return Delivery.delivered();
            }
        };

        worker(new FakeStore(List.of(initial, retried)), transport).runOnce();
        assertEquals(List.of(true, false), pingFlags);
    }

    @Test
    void staffAuditDeliveryPreservesEventIdAndSuppressesRetryRolePing() {
        UUID messageId = UUID.fromString("35de41db-4b90-4a4c-b972-94a16b53e301");
        UUID eventId = UUID.fromString("580d2f0a-dc03-4818-9663-57f4513e7f50");
        String payload = "{\"eventId\":\"" + eventId + "\","
                + "\"action\":\"staff-mode-exited\","
                + "\"detail\":\"session=example reason=manual\","
                + "\"token\":\"do-not-render\","
                + "\"privateCommandArgs\":\"do-not-render-args\"}";
        DiscordOutboxMessage initial = new DiscordOutboxMessage(
                messageId, STAFF_LOGS, "STAFF_ACTION", payload, 0, NOW.minusSeconds(1));
        DiscordOutboxMessage retry = new DiscordOutboxMessage(
                messageId, STAFF_LOGS, "STAFF_ACTION", payload, 1, NOW.minusSeconds(1));

        List<String> deliveredContents = new ArrayList<>();
        List<Boolean> rolePingFlags = new ArrayList<>();
        DiscordWebhookTransport transport = new DiscordWebhookTransport() {
            @Override
            public Delivery send(DiscordWebhookRoute route, String content) {
                throw new AssertionError("worker must pass the durable attempt");
            }

            @Override
            public Delivery send(DiscordWebhookRoute route, String content, boolean allowRolePing) {
                assertEquals(STAFF_LOGS, route.destination());
                deliveredContents.add(content);
                rolePingFlags.add(allowRolePing);
                return Delivery.delivered();
            }
        };
        FakeStore firstStore = new FakeStore(List.of(initial));
        FakeStore retryStore = new FakeStore(List.of(retry));
        worker(firstStore, transport).runOnce();
        worker(retryStore, transport).runOnce();

        assertEquals(messageId, firstStore.deliveredId);
        assertEquals(messageId, retryStore.deliveredId);
        assertEquals(List.of(true, false), rolePingFlags);
        assertEquals(2, deliveredContents.size());
        for (String content : deliveredContents) {
            assertTrue(content.contains("eventId=" + eventId));
            assertTrue(content.contains("action=staff-mode-exited"));
            assertFalse(content.contains("do-not-render"));
            assertFalse(content.contains("do-not-render-args"));
            assertFalse(content.contains("privateCommandArgs"));
        }
    }

    @Test
    void poisonPayloadFailsWithoutCallingTransport() {
        DiscordOutboxMessage message = message(REPORTS, REPORT_CREATED, "not-json");
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
        DiscordOutboxMessage message = message(ALERTS, "DISCORD_CHANNEL_UNHEALTHY", "{\"destination\":\"reports\"}");
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
        DiscordOutboxMessage first = message(REPORTS, REPORT_CREATED, "{\"reportId\":\"r-1\"}");
        DiscordOutboxMessage second = message(REPORTS, REPORT_CREATED, "{\"reportId\":\"r-2\"}");
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

    @Test
    void closeClosesOwnedTransportEvenWithoutScheduledTask() {
        CloseTrackingTransport transport = new CloseTrackingTransport();
        DiscordOutboxWorker worker = worker(new FakeStore(List.of()), transport);

        worker.close();

        assertTrue(transport.closed);
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
        Set<String> hosts = Set.of(STAGING_HOST);
        return Map.of(
                "punishments", DiscordWebhookRoute.approvedStaging(
                        "punishments", URI.create("https://" + STAGING_HOST + "/punishments"), hosts),
                REPORTS, DiscordWebhookRoute.approvedStaging(
                        REPORTS, URI.create("https://" + STAGING_HOST + "/reports"), hosts),
                STAFF_LOGS, DiscordWebhookRoute.approvedStaging(
                        STAFF_LOGS, URI.create("https://" + STAGING_HOST + "/staffmode"), hosts),
                ALERTS, DiscordWebhookRoute.approvedStaging(
                        ALERTS, URI.create("https://" + STAGING_HOST + "/alerts"), hosts)
        );
    }

    private static DiscordOutboxMessage message(String destination, String eventType, String payload) {
        return new DiscordOutboxMessage(UUID.randomUUID(), destination, eventType, payload, 0, NOW.minusSeconds(1));
    }

    private static final class CloseTrackingTransport implements DiscordWebhookTransport, AutoCloseable {
        private boolean closed;

        @Override
        public DiscordWebhookTransport.Delivery send(DiscordWebhookRoute route, String content) {
            return DiscordWebhookTransport.Delivery.delivered();
        }

        @Override
        public void close() {
            closed = true;
        }
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
