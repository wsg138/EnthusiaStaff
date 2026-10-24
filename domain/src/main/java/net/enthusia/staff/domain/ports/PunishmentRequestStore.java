package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.auth.Actor;

public interface PunishmentRequestStore {
    PunishmentRequestResult submit(PunishmentApprovalRequest request);

    Optional<PunishmentApprovalRequest> find(UUID requestId);

    /**
     * Read-only exact-key recovery after a submission committed but draft cleanup failed.
     * Every store used for punishment draft confirmation must implement this.
     * Unsupported stores fail closed rather than risk applying a punishment
     * after an approval request for the same draft was already submitted.
     */
    default Optional<PunishmentApprovalRequest> findBySubmissionKey(IdempotencyKey key) {
        throw new UnsupportedOperationException(
                "Exact submission-key lookup is required for punishment draft confirmation");
    }

    List<PunishmentApprovalRequest> pending(Instant now, int limit);

    Optional<PunishmentApprovalLease> acquire(
            UUID requestId,
            UUID ownerId,
            Instant now,
            Instant leaseExpiresAt
    );

    /** Best-effort fenced lease release for a review that did not commit a decision. */
    default boolean abandon(PunishmentApprovalLease lease) {
        return false;
    }

    PunishmentRequestResult approve(
            PunishmentApprovalLease lease,
            Actor approver,
            CaseId caseId,
            Instant now
    );

    PunishmentRequestResult deny(
            PunishmentApprovalLease lease,
            Actor approver,
            String note,
            Instant now
    );

    int expire(Instant now);

    default int expire(Instant now, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("expiration limit must be positive");
        }
        return expire(now);
    }
}
