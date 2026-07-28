package net.enthusia.staff.domain.report;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record ReportSummary(
        UUID reportId,
        UUID reporterId,
        UUID targetId,
        String reasonId,
        ReportState state,
        Optional<UUID> assignedTo,
        String serverId,
        Instant createdAt,
        Instant updatedAt,
        long revision
) {
    public ReportSummary {
        if (reportId == null || reporterId == null || targetId == null || reasonId == null || reasonId.isBlank()
                || state == null || assignedTo == null || serverId == null || serverId.isBlank()
                || createdAt == null || updatedAt == null || revision < 0) {
            throw new IllegalArgumentException("report summary fields must be present");
        }
    }
}
