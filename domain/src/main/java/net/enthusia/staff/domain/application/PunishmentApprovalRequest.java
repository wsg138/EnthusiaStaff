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
        validateRequiredFields(
                requestId,
                submissionKey,
                proposal,
                createdAt,
                expiresAt,
                status,
                revision
        );
        if (status == PunishmentRequestStatus.PENDING) {
            validatePendingState(resolvedBy, resolutionNote, resultingCaseId, resolvedAt);
        } else {
            validateResolvedState(
                    status,
                    revision,
                    createdAt,
                    resolvedBy,
                    resolutionNote,
                    resultingCaseId,
                    resolvedAt
            );
            resolutionNote = Checks.nonBlank(resolutionNote, "resolutionNote", 1_000);
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

    private static void validateRequiredFields(
            UUID requestId,
            IdempotencyKey submissionKey,
            PunishmentProposal proposal,
            Instant createdAt,
            Instant expiresAt,
            PunishmentRequestStatus status,
            long revision
    ) {
        if (requestId == null || submissionKey == null || proposal == null || createdAt == null
                || expiresAt == null || status == null || revision < 0) {
            throw new IllegalArgumentException("punishment approval request fields must be present");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("punishment approval request expiration must follow creation");
        }
    }

    private static void validatePendingState(
            UUID resolvedBy,
            String resolutionNote,
            CaseId resultingCaseId,
            Instant resolvedAt
    ) {
        if (resolvedBy != null || resolutionNote != null || resultingCaseId != null || resolvedAt != null) {
            throw new IllegalArgumentException("pending punishment request cannot contain resolution fields");
        }
    }

    private static void validateResolvedState(
            PunishmentRequestStatus status,
            long revision,
            Instant createdAt,
            UUID resolvedBy,
            String resolutionNote,
            CaseId resultingCaseId,
            Instant resolvedAt
    ) {
        if (revision == 0 || resolvedAt == null || resolvedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("resolved punishment request requires a valid revision and time");
        }
        Checks.nonBlank(resolutionNote, "resolutionNote", 1_000);
        switch (status) {
            case APPROVED, FULFILLED_EXTERNALLY -> validateFulfilledState(resolvedBy, resultingCaseId);
            case DENIED -> validateDeniedState(resolvedBy, resultingCaseId);
            case EXPIRED -> validateExpiredState(resolvedBy, resultingCaseId);
            case PENDING -> throw new IllegalStateException("pending state was handled separately");
            default -> throw new IllegalStateException("unsupported punishment request state: " + status);
        }
    }

    private static void validateFulfilledState(UUID resolvedBy, CaseId resultingCaseId) {
        if (resolvedBy == null || resultingCaseId == null) {
            throw new IllegalArgumentException("fulfilled punishment request requires resolver and case");
        }
    }

    private static void validateDeniedState(UUID resolvedBy, CaseId resultingCaseId) {
        if (resolvedBy == null || resultingCaseId != null) {
            throw new IllegalArgumentException("denied punishment request requires resolver without case");
        }
    }

    private static void validateExpiredState(UUID resolvedBy, CaseId resultingCaseId) {
        if (resolvedBy != null || resultingCaseId != null) {
            throw new IllegalArgumentException("expired punishment request cannot contain resolver or case");
        }
    }
}
