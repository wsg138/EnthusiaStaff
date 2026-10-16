package net.enthusia.staff.discordbot.retractionstage2;

import static net.enthusia.staff.discordbot.retraction.ExactRetractionPlanner.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Unregistered offline-only prototype. Ports accept ONLY synthetic fake transports.
 * No JDA, Paper, REST or server adapter is provided. Eligibility is not deletion.
 */
public final class FakeRetractionEngine {
    static final Duration MAX_AGE = Duration.ofMinutes(60);
    static final Duration RETRY_INTERVAL = Duration.ofSeconds(5);
    static final int MAX_ATTEMPTS = 3;
    static final int MAX_RECORDS = 64;

    /** Distinguish a proven no-effect send from a possibly committed unknown ACK. */
    public enum SendResult { CONFIRMED, PROVEN_NO_EFFECT, UNKNOWN, UNSUPPORTED, FAILED }

    public interface FakeTransport {
        /** PROVEN_NO_EFFECT requires evidence that no delete happened, not merely a timeout. */
        SendResult deleteSynthetic(ReceiptRecord record);
        /** RETRYABLE here always means the prior send is still ambiguous. */
        DeliveryStatus reconcileSynthetic(ReceiptRecord record);
    }

    /** Must independently revalidate a CURRENT case and permissions before every fake call. */
    public interface CaseAuthority {
        Authorization fetchCurrent(ReceiptRecord record);
    }

    /** All evidence is expected from an independent trusted authority, not AI output. */
    public record Authorization(
            String caseId, long revision, String currentEventId, String originalEventId,
            String operationId, boolean open, boolean blocked, boolean ownerVerified,
            boolean destinationAllowed, boolean senderVerified, boolean deletePermission,
            boolean exactSignatureSupported) {}

    /** No raw message text, nickname, content hash, or credential is persisted. */
    public record ReceiptRecord(
            String caseId, long revision, SourceEvent current, SourceEvent original,
            PublicationReceipt publication, Target target, Instant publishedAt, Instant receivedAt) {
        public ReceiptRecord {
            if (caseId == null || caseId.isBlank() || caseId.length() > 256 || revision < 0) {
                throw new IllegalArgumentException("invalid case identity");
            }
            Objects.requireNonNull(current);
            Objects.requireNonNull(original);
            Objects.requireNonNull(publication);
            Objects.requireNonNull(target);
            Objects.requireNonNull(publishedAt);
            Objects.requireNonNull(receivedAt);
        }
    }

    /** An ambiguous send is NEVER retried as a fresh deletion; it is only probed. */
    public record Entry(ReceiptRecord record, DeliveryStatus status, int attempts,
                        boolean uncertain, Instant nextAttempt) {
        public Entry {
            Objects.requireNonNull(record);
            Objects.requireNonNull(status);
            Objects.requireNonNull(nextAttempt);
            if (attempts < 0 || attempts > MAX_ATTEMPTS) {
                throw new IllegalArgumentException("invalid attempt count");
            }
        }
        public boolean terminal() {
            return status != DeliveryStatus.RETRYABLE;
        }
    }

    private final EncryptedReceiptJournal journal;
    private final FakeTransport discord;
    private final FakeTransport paper;
    private final CaseAuthority authority;
    private final Clock clock;
    private final boolean enabled;

    public FakeRetractionEngine(EncryptedReceiptJournal journal, FakeTransport discord,
                                FakeTransport paper, CaseAuthority authority,
                                Clock clock, boolean enabled) {
        this.journal = Objects.requireNonNull(journal);
        this.discord = Objects.requireNonNull(discord);
        this.paper = Objects.requireNonNull(paper);
        this.authority = Objects.requireNonNull(authority);
        this.clock = Objects.requireNonNull(clock);
        this.enabled = enabled;
    }

    /** Fake-only intake with post-send provenance and complete planner proof. */
    public synchronized boolean admit(ReceiptRecord record) {
        requireEnabled();
        RetractionPreflight.verifyReceipt(record, clock.instant());
        if (journal.get(record.target().operationId()) != null) {
            return false;
        }
        if (journal.size() >= MAX_RECORDS) {
            throw new IllegalStateException("synthetic receipt capacity exhausted");
        }
        journal.put(new Entry(record, DeliveryStatus.RETRYABLE, 0, false, clock.instant()));
        return true;
    }

    /** Status is an exact fake transport result, never recipient-side erasure. */
    public synchronized DeliveryStatus tick(String operationId) {
        requireEnabled();
        Entry row = journal.get(operationId);
        if (row == null) {
            throw new IllegalArgumentException("unknown synthetic operation");
        }
        if (row.terminal()) {
            return row.status();
        }
        Instant now = clock.instant();
        if (!now.isBefore(row.record().original().occurredAt().plus(MAX_AGE))
                || !now.isBefore(row.record().publishedAt().plus(MAX_AGE))) {
            return save(row, DeliveryStatus.EXPIRED, row.uncertain(), now).status();
        }
        if (now.isBefore(row.nextAttempt())) {
            return row.status();
        }
        Authorization grant = authority.fetchCurrent(row.record());
        if (!RetractionPreflight.permits(row.record(), grant)) {
            return save(row, DeliveryStatus.UNSUPPORTED, row.uncertain(), now).status();
        }
        return row.uncertain() ? reconcile(row, now) : send(row, now);
    }

    private DeliveryStatus send(Entry row, Instant now) {
        if (row.attempts() >= MAX_ATTEMPTS) {
            return save(row, DeliveryStatus.FAILED, false, now).status();
        }
        Entry prepared = new Entry(row.record(), DeliveryStatus.RETRYABLE,
                row.attempts() + 1, true, now.plus(RETRY_INTERVAL));
        journal.put(prepared); // Atomic encrypted intent BEFORE fake effect.
        SendResult result = Objects.requireNonNull(
                transport(prepared).deleteSynthetic(prepared.record()),
                "synthetic send result");
        // Only a positively proven no-effect outcome authorizes a future new attempt.
        return switch (result) {
            case CONFIRMED -> save(prepared, DeliveryStatus.CONFIRMED, false, now).status();
            case PROVEN_NO_EFFECT -> save(prepared, DeliveryStatus.RETRYABLE, false,
                    now.plus(RETRY_INTERVAL)).status();
            case UNKNOWN -> save(prepared, DeliveryStatus.RETRYABLE, true,
                    now.plus(RETRY_INTERVAL)).status();
            case UNSUPPORTED -> save(prepared, DeliveryStatus.UNSUPPORTED, false, now).status();
            case FAILED -> save(prepared, DeliveryStatus.FAILED, false, now).status();
        };
    }

    private DeliveryStatus reconcile(Entry row, Instant now) {
        DeliveryStatus result = Objects.requireNonNull(
                transport(row).reconcileSynthetic(row.record()),
                "synthetic reconciliation result");
        // RETRYABLE does NOT mean the first delete failed. Preserve uncertainty on restart.
        DeliveryStatus status = result == DeliveryStatus.EXPIRED
                ? DeliveryStatus.FAILED : result;
        return save(row, status, status == DeliveryStatus.RETRYABLE,
                now.plus(RETRY_INTERVAL)).status();
    }

    private Entry save(Entry row, DeliveryStatus status, boolean uncertain, Instant due) {
        Entry updated = new Entry(row.record(), status, row.attempts(), uncertain, due);
        journal.put(updated);
        return updated;
    }

    private FakeTransport transport(Entry row) {
        return switch (row.record().target().destination().surface()) {
            case DISCORD -> discord;
            case PAPER_SIGNED -> paper;
            case MINECRAFT_SYSTEM -> throw new IllegalStateException("unsupported surface");
        };
    }

    public synchronized Entry inspect(String operationId) {
        return journal.get(operationId);
    }

    public synchronized int size() {
        return journal.size();
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("synthetic retraction engine disabled");
        }
    }
}
