package net.enthusia.staff.discordbot.retractionstage2;

import static net.enthusia.staff.discordbot.retraction.ExactRetractionPlanner.*;
import static net.enthusia.staff.discordbot.retractionstage2.FakeRetractionEngine.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure admission/authorization checks; does not mutate a journal or call any transport. */
final class RetractionPreflight {
    private RetractionPreflight() {}

    static void verifyReceipt(ReceiptRecord record, Instant now) {
        Objects.requireNonNull(record);
        if (invalidTimeline(record, now) || !record.publication().actualPostSendReceipt()) {
            throw new IllegalArgumentException("not a valid timely post-send receipt");
        }
        Plan plan = plan(record.current(), record.original(), "BLOCK",
                List.of(record.original().eventId()), List.of(record.publication()),
                Set.of(record.publication().destination()));
        if (plan.refusal() != Refusal.NONE || !plan.targets().equals(List.of(record.target()))) {
            throw new IllegalArgumentException("receipt is not an exact public planner target");
        }
    }

    private static boolean invalidTimeline(ReceiptRecord record, Instant now) {
        return record.receivedAt().isAfter(now) || record.publishedAt().isAfter(now)
                || record.publishedAt().isBefore(record.original().occurredAt())
                || now.isBefore(record.original().occurredAt())
                || !now.isBefore(record.original().occurredAt().plus(MAX_AGE))
                || !now.isBefore(record.publishedAt().plus(MAX_AGE));
    }

    static boolean permits(ReceiptRecord record, Authorization a) {
        return a != null && hasCurrentPermission(a) && matchesCase(record, a)
                && (record.target().destination().surface() != Surface.PAPER_SIGNED
                        || a.exactSignatureSupported());
    }

    private static boolean hasCurrentPermission(Authorization a) {
        return a.open() && a.blocked() && a.ownerVerified()
                && a.destinationAllowed() && a.senderVerified() && a.deletePermission();
    }

    private static boolean matchesCase(ReceiptRecord record, Authorization a) {
        return record.caseId().equals(a.caseId()) && record.revision() == a.revision()
                && record.current().eventId().equals(a.currentEventId())
                && record.original().eventId().equals(a.originalEventId())
                && record.target().operationId().equals(a.operationId());
    }
}
