package net.enthusia.staff.domain.report;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.common.IdempotencyKey;

public record ReportStateChangeRequest(
        UUID reportId,
        UUID actorId,
        ReportAction action,
        long expectedRevision,
        String note,
        IdempotencyKey idempotencyKey,
        Instant changedAt
) {
    public ReportStateChangeRequest {
        if (reportId == null || actorId == null || action == null || expectedRevision < 0
                || note == null || note.isBlank() || note.length() > 2_000
                || idempotencyKey == null || changedAt == null) {
            throw new IllegalArgumentException("report state change fields must be present");
        }
    }
}
