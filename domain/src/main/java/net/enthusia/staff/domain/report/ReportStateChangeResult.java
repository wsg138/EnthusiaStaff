package net.enthusia.staff.domain.report;

public sealed interface ReportStateChangeResult {
    record Applied(ReportState state, long revision, boolean replayed) implements ReportStateChangeResult {
        public Applied {
            if (state == null || revision < 0) {
                throw new IllegalArgumentException("applied report change fields are invalid");
            }
        }
    }

    record Rejected(String code, String message) implements ReportStateChangeResult {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("rejected report change fields are invalid");
            }
        }
    }
}
