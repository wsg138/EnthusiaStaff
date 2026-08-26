package net.enthusia.staff.persistence;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

final class JdbcTransactionRetry {
    private static final int DEFAULT_MAXIMUM_ATTEMPTS = 3;
    private static final long RETRY_BASE_MILLIS = 25L;

    private final int maximumAttempts;
    private final IntConsumer retryPause;

    JdbcTransactionRetry() {
        this(DEFAULT_MAXIMUM_ATTEMPTS, JdbcTransactionRetry::pauseBeforeRetry);
    }

    JdbcTransactionRetry(int maximumAttempts, IntConsumer retryPause) {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("maximum transaction retry attempts must be positive");
        }
        this.maximumAttempts = maximumAttempts;
        this.retryPause = Objects.requireNonNull(retryPause, "retryPause");
    }

    <T> T execute(String interruptedMessage, Supplier<T> operation) {
        Objects.requireNonNull(interruptedMessage, "interruptedMessage");
        Objects.requireNonNull(operation, "operation");
        ModerationPersistenceException latest = null;
        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            try {
                return operation.get();
            } catch (ModerationPersistenceException exception) {
                latest = exception;
                if (!JdbcSqlErrors.isRetryableTransactionConflict(exception)
                        || attempt == maximumAttempts) {
                    throw exception;
                }
                retryPause.accept(attempt);
                if (Thread.currentThread().isInterrupted()) {
                    throw new ModerationPersistenceException(interruptedMessage, exception);
                }
            }
        }
        throw latest == null
                ? new IllegalStateException("transaction retry loop completed without an outcome")
                : latest;
    }

    private static void pauseBeforeRetry(int attempt) {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(RETRY_BASE_MILLIS * attempt));
    }
}
