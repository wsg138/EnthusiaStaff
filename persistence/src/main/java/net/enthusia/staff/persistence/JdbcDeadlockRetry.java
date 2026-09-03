package net.enthusia.staff.persistence;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Package-local compatibility wrapper for ES-D04 stores that were authored before the shared
 * retry helper was generalized from deadlock-only handling to transaction-conflict handling.
 */
final class JdbcDeadlockRetry {
    private final JdbcTransactionRetry delegate;

    JdbcDeadlockRetry() {
        this.delegate = new JdbcTransactionRetry();
    }

    JdbcDeadlockRetry(int maximumAttempts, IntConsumer retryPause) {
        this.delegate = new JdbcTransactionRetry(maximumAttempts, retryPause);
    }

    <T> T execute(String interruptedMessage, Supplier<T> operation) {
        return delegate.execute(interruptedMessage, operation);
    }
}
