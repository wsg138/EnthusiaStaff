package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.auth.Actor;

public interface PunishmentRequestStore {
    PunishmentRequestResult submit(PunishmentApprovalRequest request);

    Optional<PunishmentApprovalRequest> find(UUID requestId);

    List<PunishmentApprovalRequest> pending(Instant now, int limit);

    Optional<PunishmentApprovalLease> acquire(
            UUID requestId,
            UUID ownerId,
            Instant now,
            Instant leaseExpiresAt
    );

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
