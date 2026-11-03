package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime.PendingReport;
import net.enthusia.staff.persistence.JdbcStaffBotReportIntentStore.ClaimedIntent;
import org.junit.jupiter.api.Test;

final class PrivateReportNotificationDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String WORKER = "staging-private-report-worker";
    private static final String GUILD = "123456789012345678";
    private static final String MESSAGE_ID = "423456789012345678";
    private static final UUID REPORT_ID =
            UUID.fromString("12345678-1234-1234-1234-123456789abc");

    private static final class FakeJournal implements PrivateReportNotificationDispatcher.Journal {
        int claims;
        int acknowledgements;
        int quarantines;
        boolean available = true;
        boolean acknowledge = true;
        String storedMessageId;
        ClaimedIntent intent =
                new ClaimedIntent(UUID.randomUUID(), REPORT_ID, "REPORT_CREATED", 1);

        @Override
        public List<ClaimedIntent> claimArmed(
                String owner, int limit, Duration lease, Instant now
        ) {
            claims++;
            assertEquals(WORKER, owner);
            assertEquals(1, limit);
            assertEquals(NOW, now);
            assertTrue(lease.compareTo(Duration.ZERO) > 0);
            if (!available) {
                return List.of();
            }
            available = false;
            return List.of(intent);
        }

        @Override
        public boolean delivered(UUID intentId, String owner, Instant now, String messageId) {
            acknowledgements++;
            assertEquals(intent.intentId(), intentId);
            assertEquals(WORKER, owner);
            assertEquals(MESSAGE_ID, messageId);
            storedMessageId = messageId;
            return acknowledge;
        }

        @Override
        public boolean reviewRequired(UUID intentId, String owner) {
            quarantines++;
            assertEquals(intent.intentId(), intentId);
            assertEquals(WORKER, owner);
            return true;
        }
    }

    private static PrivateReportNotificationTemplate.DestinationProof destination(
            boolean enabled) {
        return new PrivateReportNotificationTemplate.DestinationProof(
                enabled, GUILD, GUILD, true, true, false, true, false, false);
    }

    private static PendingReport report() {
        return new PendingReport(REPORT_ID, UUID.randomUUID(),
                "Target", "Reporter", "chat.spam", "OPEN", 1,
                "Reporter text @everyone [fake](https://example.invalid)");
    }

    private static PrivateReportNotificationDispatcher dispatcher(
            FakeJournal journal, boolean approved,
            PrivateReportNotificationDispatcher.DestinationVerifier verifier,
            PrivateReportNotificationDispatcher.ReportReader reader,
            PrivateReportNotificationDispatcher.Transport transport
    ) {
        return new PrivateReportNotificationDispatcher(
                journal, reader, verifier, transport, CLOCK, WORKER, approved);
    }

    @Test
    void onlyValidatedDiscordMessageIdsCanBecomeConfirmedReceipts() {
        assertThrows(IllegalArgumentException.class,
                () -> PrivateReportNotificationDispatcher.DeliveryReceipt.confirmed("bad"));
        assertThrows(IllegalArgumentException.class,
                () -> PrivateReportNotificationDispatcher.DeliveryReceipt.confirmed(""));
        assertThrows(NullPointerException.class,
                () -> PrivateReportNotificationDispatcher.DeliveryReceipt.confirmed(null));
        assertFalse(PrivateReportNotificationDispatcher.DeliveryReceipt.uncertain().isConfirmed());
        assertTrue(PrivateReportNotificationDispatcher.DeliveryReceipt
                .confirmed(MESSAGE_ID).isConfirmed());
    }

    @Test
    void defaultOffNeverTouchesDestinationDatabaseOrTransport() {
        var journal = new FakeJournal();
        var execution = dispatcher(journal, false,
                () -> { throw new AssertionError("Destination unexpectedly inspected"); },
                id -> { throw new AssertionError("Report unexpectedly read"); },
                text -> { throw new AssertionError("Discord unexpectedly contacted"); });
        assertEquals(0, execution.runOnce());
        assertEquals(0, journal.claims);
    }

    @Test
    void disabledOrUnapprovedDestinationNeverLeasesWork() {
        var journal = new FakeJournal();
        var execution = dispatcher(journal, true, () -> destination(false),
                id -> Optional.of(report()), text -> { throw new AssertionError("No send"); });
        assertEquals(0, execution.runOnce());
        assertEquals(0, journal.claims);
    }

    @Test
    void freshAuthorizationAndConfirmedSendAreAcknowledgedOnce() {
        var journal = new FakeJournal();
        AtomicInteger sent = new AtomicInteger();
        var execution = dispatcher(journal, true, () -> destination(true),
                id -> Optional.of(report()), body -> {
                    assertTrue(body.contains("Reported player: Target"));
                    assertTrue(body.contains("/reports view " + REPORT_ID));
                    assertFalse(body.contains("@everyone"));
                    assertFalse(body.contains("https://"));
                    sent.incrementAndGet();
                    return PrivateReportNotificationDispatcher.DeliveryReceipt.confirmed(MESSAGE_ID);
                });
        assertEquals(1, execution.runOnce());
        assertEquals(1, sent.get());
        assertEquals(1, journal.acknowledgements);
        assertEquals(MESSAGE_ID, journal.storedMessageId);
        assertEquals(0, journal.quarantines);
        assertEquals(0, execution.runOnce());
        assertEquals(1, sent.get());
    }

    @Test
    void uncertainDiscordResponseNeverRetriesAndRequiresHumanReview() {
        var journal = new FakeJournal();
        AtomicInteger sends = new AtomicInteger();
        var execution = dispatcher(journal, true, () -> destination(true),
                id -> Optional.of(report()), text -> {
                    sends.incrementAndGet();
                    return PrivateReportNotificationDispatcher.DeliveryReceipt.uncertain();
                });
        assertEquals(1, execution.runOnce());
        assertEquals(0, journal.acknowledgements);
        assertEquals(1, journal.quarantines);
        assertEquals(0, execution.runOnce());
        assertEquals(1, sends.get());
    }

    @Test
    void rejectedAckAfterConfirmedDiscordSendQuarantinesWithoutRetrying() {
        var journal = new FakeJournal();
        journal.acknowledge = false;
        AtomicInteger sent = new AtomicInteger();
        var execution = dispatcher(journal, true, () -> destination(true),
                id -> Optional.of(report()), body -> {
                    sent.incrementAndGet();
                    return PrivateReportNotificationDispatcher.DeliveryReceipt.confirmed(MESSAGE_ID);
                });
        assertEquals(1, execution.runOnce());
        assertEquals(1, sent.get());
        assertEquals(1, journal.acknowledgements);
        assertEquals(1, journal.quarantines);
        assertEquals(0, execution.runOnce());
        assertEquals(1, sent.get());
    }

    @Test
    void mergedFollowupCannotBeMisrepresentedAsOriginalReportBody() {
        var journal = new FakeJournal();
        journal.intent = new ClaimedIntent(UUID.randomUUID(), REPORT_ID, "REPORT_MERGED", 1);
        var execution = dispatcher(journal, true, () -> destination(true),
                id -> { throw new AssertionError("Merged body should not be read"); },
                text -> { throw new AssertionError("No stale original-body notification"); });
        assertEquals(1, execution.runOnce());
        assertEquals(0, journal.acknowledgements);
        assertEquals(1, journal.quarantines);
    }

    @Test
    void wrongReportIdentityNeverLeaksDescriptionIntoDiscord() {
        var journal = new FakeJournal();
        var wrongReport = new PendingReport(UUID.randomUUID(), UUID.randomUUID(),
                "Target", "Reporter", "chat.spam", "OPEN", 1,
                "Private description from another case");
        var execution = dispatcher(journal, true, () -> destination(true),
                id -> Optional.of(wrongReport),
                text -> { throw new AssertionError("Mismatched report data must not be sent"); });
        assertEquals(1, execution.runOnce());
        assertEquals(0, journal.acknowledgements);
        assertEquals(1, journal.quarantines);
    }

    @Test
    void permissionRevocationBetweenPreflightAndSendFailsClosed() {
        var journal = new FakeJournal();
        AtomicInteger checks = new AtomicInteger();
        var execution = dispatcher(journal, true,
                () -> destination(checks.incrementAndGet() == 1),
                id -> { throw new AssertionError("No text should be read"); },
                text -> { throw new AssertionError("No send should occur"); });
        assertEquals(1, execution.runOnce());
        assertEquals(2, checks.get());
        assertEquals(1, journal.quarantines);
    }

    @Test
    void missingReportOrTransportFailureRequiresReview() {
        var journal = new FakeJournal();
        var missing = dispatcher(journal, true, () -> destination(true),
                id -> Optional.empty(),
                text -> { throw new AssertionError("No send should occur"); });
        assertEquals(1, missing.runOnce());
        assertEquals(1, journal.quarantines);

        var other = new FakeJournal();
        var failure = dispatcher(other, true, () -> destination(true),
                id -> Optional.of(report()),
                text -> { throw new IllegalStateException("Synthetic Discord timeout"); });
        assertEquals(1, failure.runOnce());
        assertEquals(1, other.quarantines);
        assertEquals(0, other.acknowledgements);
    }
}
