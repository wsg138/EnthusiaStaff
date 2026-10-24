package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime.PendingReport;
import net.enthusia.staff.persistence.JdbcStaffBotReportIntentStore;
import net.enthusia.staff.persistence.JdbcStaffBotReportIntentStore.ClaimedIntent;

/**
 * Unscheduled, opt-in test harness for a future private StaffBot report delivery.
 * No JDA sender or config is registered; production cannot invoke this class
 * until a separately approved integration supplies a trusted private destination.
 */
final class PrivateReportNotificationDispatcher {
    private static final Duration LEASE = Duration.ofSeconds(45);
    private static final int CLAIM_LIMIT = 1;
    private static final String CREATED_EVENT_TYPE = "REPORT_CREATED";

    interface Journal {
        List<ClaimedIntent> claimArmed(String owner, int limit, Duration lease, java.time.Instant now);
        boolean delivered(UUID intentId, String owner, java.time.Instant now, String messageId);
        boolean reviewRequired(UUID intentId, String owner);
    }

    @FunctionalInterface
    interface ReportReader {
        Optional<PendingReport> read(UUID reportId);
    }

    @FunctionalInterface
    interface DestinationVerifier {
        PrivateReportNotificationTemplate.DestinationProof verify();
    }

    @FunctionalInterface
    interface Transport {
        /**
         * Only a returned Discord message ID counts as confirmed delivery.
         * Any timeout, uncertain response, or rejection has no message ID.
         * Real JDA transports MUST independently reverify channel permissions
         * at send time and set allowed mentions to an empty list.
         */
        DeliveryReceipt send(String message);
    }

    record DeliveryReceipt(String messageId) {
        private static final String SNOWFLAKE = "[1-9][0-9]{5,19}";

        DeliveryReceipt {
            if (messageId != null && !messageId.matches(SNOWFLAKE)) {
                throw new IllegalArgumentException("Invalid Discord message ID");
            }
        }

        static DeliveryReceipt uncertain() {
            return new DeliveryReceipt(null);
        }

        static DeliveryReceipt confirmed(String messageId) {
            return new DeliveryReceipt(Objects.requireNonNull(messageId, "messageId"));
        }

        boolean isConfirmed() {
            return messageId != null;
        }
    }

    private final Journal journal;
    private final ReportReader reader;
    private final DestinationVerifier verifier;
    private final Transport transport;
    private final Clock clock;
    private final String owner;
    private final boolean ownerApproved;

    PrivateReportNotificationDispatcher(
            Journal journal, ReportReader reader, DestinationVerifier verifier,
            Transport transport, Clock clock, String owner, boolean ownerApproved
    ) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (owner == null || owner.isBlank() || owner.length() > 128) {
            throw new IllegalArgumentException("Bounded worker ID is required");
        }
        this.owner = owner;
        this.ownerApproved = ownerApproved;
    }

    static Journal jdbcJournal(JdbcStaffBotReportIntentStore store) {
        Objects.requireNonNull(store, "store");
        return new Journal() {
            @Override
            public List<ClaimedIntent> claimArmed(
                    String owner, int limit, Duration lease, java.time.Instant now) {
                return store.claimArmed(owner, limit, lease, now);
            }

            @Override
            public boolean delivered(UUID intentId, String owner, java.time.Instant now,
                    String messageId) {
                return store.delivered(intentId, owner, now, messageId);
            }

            @Override
            public boolean reviewRequired(UUID intentId, String owner) {
                return store.reviewRequired(intentId, owner);
            }
        };
    }

    /**
     * Deliberately not scheduled or called by StaffBotRuntime.
     * An approved sender must revalidate private JDA access immediately before send.
     */
    int runOnce() {
        if (!ownerApproved || !verifier.verify().eligible()) {
            return 0;
        }
        List<ClaimedIntent> claimed = journal.claimArmed(
                owner, CLAIM_LIMIT, LEASE, clock.instant());
        for (ClaimedIntent intent : claimed) {
            handleClaim(intent);
        }
        return claimed.size();
    }

    private void handleClaim(ClaimedIntent intent) {
        try {
            // A merge intent means there was a new reporter follow-up. The
            // original description is NOT that follow-up. Do not send stale
            // body text until owner-approved follow-up sourcing is implemented.
            if (!CREATED_EVENT_TYPE.equals(intent.eventType())) {
                journal.reviewRequired(intent.intentId(), owner);
                return;
            }
            Optional<String> content = prepareMessage(intent.reportId());
            if (content.isEmpty()) {
                journal.reviewRequired(intent.intentId(), owner);
                return;
            }
            DeliveryReceipt receipt = transport.send(content.orElseThrow());
            if (receipt == null || !receipt.isConfirmed()
                    || !journal.delivered(intent.intentId(), owner, clock.instant(),
                            receipt.messageId())) {
                journal.reviewRequired(intent.intentId(), owner);
            }
        } catch (RuntimeException failure) {
            // Discord may have accepted the message before a timeout/error.
            // Never automatically retry or print report details on any exception.
            journal.reviewRequired(intent.intentId(), owner);
        }
    }

    private Optional<String> prepareMessage(UUID reportId) {
        var destination = verifier.verify();
        if (!destination.eligible()) {
            return Optional.empty();
        }
        return reader.read(reportId)
                .filter(report -> reportId.equals(report.reportId()))
                .flatMap(report -> PrivateReportNotificationTemplate.compose(report, destination));
    }
}
