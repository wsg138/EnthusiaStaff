package net.enthusia.staff.domain.report;

import java.util.UUID;

public sealed interface ReportSubmissionResult {
    record Accepted(UUID reportId, boolean merged, boolean replayed) implements ReportSubmissionResult {
        public Accepted {
            if (reportId == null) {
                throw new IllegalArgumentException("report ID must be present");
            }
        }
    }

    record Rejected(String code, String message) implements ReportSubmissionResult {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("report rejection fields must be present");
            }
        }
    }
}
