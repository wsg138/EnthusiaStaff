package net.enthusia.staff.paper.inventory;

final class LiveInventoryRecoveryPolicy {
    enum RetryDecision {
        RETRY,
        STOP_OFFLINE,
        EXHAUSTED
    }

    private LiveInventoryRecoveryPolicy() {
    }

    static RetryDecision metadataRetry(boolean online, int attempt, int maxAttempts) {
        if (!online) {
            return RetryDecision.STOP_OFFLINE;
        }
        if (attempt >= maxAttempts) {
            return RetryDecision.EXHAUSTED;
        }
        return RetryDecision.RETRY;
    }
}
