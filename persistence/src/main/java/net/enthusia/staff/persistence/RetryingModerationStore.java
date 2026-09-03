package net.enthusia.staff.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.ports.ModerationStore;

final class RetryingModerationStore implements ModerationStore {
    private final ModerationStore delegate;
    private final JdbcTransactionRetry retry;

    RetryingModerationStore(ModerationStore delegate) {
        this(delegate, new JdbcTransactionRetry());
    }

    RetryingModerationStore(ModerationStore delegate, IntConsumer retryPause) {
        this(delegate, new JdbcTransactionRetry(3, retryPause));
    }

    private RetryingModerationStore(ModerationStore delegate, JdbcTransactionRetry retry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.retry = Objects.requireNonNull(retry, "retry");
    }

    @Override
    public List<PriorOffense> relatedHistory(UUID targetId, String family) {
        return delegate.relatedHistory(targetId, family);
    }

    @Override
    public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
        return retry.execute(
                "Punishment transaction retry was interrupted",
                () -> delegate.createPunishment(plan)
        );
    }
}
