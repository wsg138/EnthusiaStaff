package net.enthusia.staff.domain.application;

import java.time.Instant;
import java.util.UUID;

public record PunishmentApprovalLease(
        PunishmentApprovalRequest request,
        UUID ownerId,
        long fenceToken,
        Instant expiresAt
) {
    public PunishmentApprovalLease {
        if (request == null || ownerId == null || fenceToken <= 0 || expiresAt == null) {
            throw new IllegalArgumentException("punishment approval lease fields must be present");
        }
        if (request.status() != PunishmentRequestStatus.PENDING) {
            throw new IllegalArgumentException("only a pending punishment request may be leased");
        }
    }

    public boolean validAt(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("current time must be present");
        }
        return expiresAt.isAfter(now) && request.expiresAt().isAfter(now);
    }
}
