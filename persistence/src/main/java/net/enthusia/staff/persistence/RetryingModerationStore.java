package net.enthusia.staff.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntConsumer;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.ports.ModerationStore;

final class RetryingModerationStore implements ModerationStore {
    private static final int MAXIMUM_ATTEMPTS = 3;
    private static final long RETRY_BASE_MILLIS = 25L;

    private final ModerationStore delegate;
    private final IntConsumer retryPause;

    RetryingModerationStore(ModerationStore delegate) {
        this(delegate, RetryingModerationStore::pauseBeforeRetry);
    }

    RetryingModerationStore(ModerationStore delegate, IntConsumer retryPause) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.retryPause = Objects.requireNonNull(retryPause, "retryPause");
    }

    @Override
    public List<PriorOffense> relatedHistory(UUID targetId, String family) {
        return delegate.relatedHistory(targetId, family);
    }

    @Override
    public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
        ModerationPersistenceException latest = null;
        for (int attempt = 1; attempt <= MAXIMUM_ATTEMPTS; attempt++) {
            try {
                return delegate.createPunishment(plan);
            } catch (ModerationPersistenceException exception) {
                latest = exception;
                if (!JdbcSqlErrors.isDeadlock(exception) || attempt == MAXIMUM_ATTEMPTS) {
                    throw exception;
                }
                retryPause.accept(attempt);
                if (Thread.currentThread().isInterrupted()) {
                    throw new ModerationPersistenceException(
                            "Punishment transaction retry was interrupted",
                            exception
                    );
                }
            }
        }
        throw latest == null
                ? new IllegalStateException("punishment retry loop completed without an outcome")
                : latest;
    }

    private static void pauseBeforeRetry(int attempt) {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(RETRY_BASE_MILLIS * attempt));
    }
}
