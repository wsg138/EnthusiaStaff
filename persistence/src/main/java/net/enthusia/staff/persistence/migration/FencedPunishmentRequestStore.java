package net.enthusia.staff.persistence.migration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;

public final class FencedPunishmentRequestStore implements PunishmentRequestStore {
    private static final String BLOCKED_MESSAGE =
            "Punishment request changes are disabled by the operational mode";

    private final PunishmentRequestStore delegate;
    private final AuthoritativeWriteFence fence;

    public FencedPunishmentRequestStore(DataSource dataSource, PunishmentRequestStore delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("punishment request store delegate must be present");
        }
        this.delegate = delegate;
        this.fence = new AuthoritativeWriteFence(dataSource);
    }

    @Override
    public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
        return fence.execute(
                () -> delegate.submit(request),
                FencedPunishmentRequestStore::blocked
        );
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
        return fence.execute(
                () -> delegate.acquire(requestId, ownerId, now, leaseExpiresAt),
                Optional::empty
        );
    }

    @Override
    public PunishmentRequestResult approve(
            PunishmentApprovalLease lease,
            Actor approver,
            CaseId caseId,
            Instant now
    ) {
        return fence.execute(
                () -> delegate.approve(lease, approver, caseId, now),
                FencedPunishmentRequestStore::blocked
        );
    }

    @Override
    public PunishmentRequestResult deny(
            PunishmentApprovalLease lease,
            Actor approver,
            String note,
            Instant now
    ) {
        return fence.execute(
                () -> delegate.deny(lease, approver, note, now),
                FencedPunishmentRequestStore::blocked
        );
    }

    @Override
    public int expire(Instant now) {
        return fence.execute(() -> delegate.expire(now), () -> 0);
    }

    @Override
    public int expire(Instant now, int limit) {
        return fence.execute(() -> delegate.expire(now, limit), () -> 0);
    }

    private static PunishmentRequestResult.Rejected blocked() {
        return new PunishmentRequestResult.Rejected("MODE_BLOCKED", BLOCKED_MESSAGE);
    }
}
