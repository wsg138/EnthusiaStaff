package net.enthusia.staff.domain.report;

public record ReportEvidencePurgeResult(
        int publicChat,
        int privateMessages,
        int clientEvidence
) {
    private static final int MINIMUM_BATCH_LIMIT = 1;

    public ReportEvidencePurgeResult {
        if (publicChat < 0 || privateMessages < 0 || clientEvidence < 0) {
            throw new IllegalArgumentException("purge counts cannot be negative");
        }
    }

    public int total() {
        return Math.addExact(Math.addExact(publicChat, privateMessages), clientEvidence);
    }

    public boolean hasBacklogAt(int batchLimit) {
        if (batchLimit < MINIMUM_BATCH_LIMIT) {
            throw new IllegalArgumentException("batchLimit must be positive");
        }
        return publicChat >= batchLimit
                || privateMessages >= batchLimit
                || clientEvidence >= batchLimit;
    }
}
