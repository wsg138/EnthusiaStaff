package net.enthusia.staff.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;

final class RetryingPunishmentRequestStore implements PunishmentRequestStore {
    private static final String INTERRUPTED = "Punishment request transaction retry was interrupted";

    private final PunishmentRequestStore delegate;
    private final JdbcTransactionRetry retry;

    RetryingPunishmentRequestStore(PunishmentRequestStore delegate) {
        this(delegate, new JdbcTransactionRetry());
    }

    RetryingPunishmentRequestStore(PunishmentRequestStore delegate, IntConsumer retryPause) {
        this(delegate, new JdbcTransactionRetry(3, retryPause));
    }

    private RetryingPunishmentRequestStore(PunishmentRequestStore delegate, JdbcTransactionRetry retry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.retry = Objects.requireNonNull(retry, "retry");
    }

    @Override
    public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
        return retry.execute(INTERRUPTED, () -> delegate.submit(request));
    }

    @Override
    public Optional<PunishmentApprovalRequest> find(UUID requestId) {
        return delegate.find(requestId);
    }

    @Override
    public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
        return delegate.pending(now, limit);
    }

    @Override
    public Optional<PunishmentApprovalLease> acquire(
            UUID requestId,
            UUID ownerId,
            Instant now,
            Instant leaseExpiresAt
    ) {
        return retry.execute(
                INTERRUPTED,
                () -> delegate.acquire(requestId, ownerId, now, leaseExpiresAt)
        );
    }

    @Override
    public PunishmentRequestResult approve(
            PunishmentApprovalLease lease,
            Actor approver,
            CaseId caseId,
            Instant now
    ) {
        return retry.execute(INTERRUPTED, () -> delegate.approve(lease, approver, caseId, now));
    }

    @Override
    public PunishmentRequestResult deny(
            PunishmentApprovalLease lease,
            Actor approver,
            String note,
            Instant now
    ) {
        return retry.execute(INTERRUPTED, () -> delegate.deny(lease, approver, note, now));
    }

    @Override
    public int expire(Instant now) {
        return retry.execute(INTERRUPTED, () -> delegate.expire(now));
    }

    @Override
    public int expire(Instant now, int limit) {
        return retry.execute(INTERRUPTED, () -> delegate.expire(now, limit));
    }
}
