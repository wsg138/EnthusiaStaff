package net.enthusia.staff.domain.application;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.Checks;
import net.enthusia.staff.common.IdempotencyKey;

public record PunishmentApprovalRequest(
        UUID requestId,
        IdempotencyKey submissionKey,
        PunishmentProposal proposal,
        Instant createdAt,
        Instant expiresAt,
        PunishmentRequestStatus status,
        long revision,
        UUID resolvedBy,
        String resolutionNote,
        CaseId resultingCaseId,
        Instant resolvedAt
) {
    public PunishmentApprovalRequest {
        if (requestId == null || submissionKey == null || proposal == null || createdAt == null
                || expiresAt == null || status == null || revision < 0) {
            throw new IllegalArgumentException("punishment approval request fields must be present");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("punishment approval request expiration must follow creation");
        }
        if (status == PunishmentRequestStatus.PENDING) {
            if (resolvedBy != null || resolutionNote != null || resultingCaseId != null || resolvedAt != null) {
                throw new IllegalArgumentException("pending punishment request cannot contain resolution fields");
            }
        } else {
            if (resolvedAt == null) {
                throw new IllegalArgumentException("resolved punishment request requires a resolution time");
            }
            resolutionNote = Checks.nonBlank(resolutionNote, "resolutionNote", 1_000);
            if ((status == PunishmentRequestStatus.APPROVED
                    || status == PunishmentRequestStatus.FULFILLED_EXTERNALLY)
                    && resultingCaseId == null) {
                throw new IllegalArgumentException("fulfilled punishment request requires a resulting case");
            }
            if ((status == PunishmentRequestStatus.APPROVED || status == PunishmentRequestStatus.DENIED)
                    && resolvedBy == null) {
                throw new IllegalArgumentException("staff-resolved punishment request requires a resolver");
            }
        }
    }

    public static PunishmentApprovalRequest pending(
            UUID requestId,
            IdempotencyKey submissionKey,
            PunishmentProposal proposal,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new PunishmentApprovalRequest(
                requestId,
                submissionKey,
                proposal,
                createdAt,
                expiresAt,
                PunishmentRequestStatus.PENDING,
                0,
                null,
                null,
                null,
                null
        );
    }

    public boolean pendingAt(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("current time must be present");
        }
        return status == PunishmentRequestStatus.PENDING && expiresAt.isAfter(now);
    }
}
