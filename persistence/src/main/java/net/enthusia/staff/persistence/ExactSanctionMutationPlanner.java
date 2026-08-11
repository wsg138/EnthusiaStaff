package net.enthusia.staff.persistence;

import java.time.Instant;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import net.enthusia.staff.domain.sanction.SanctionStatus;

final class ExactSanctionMutationPlanner {
    private ExactSanctionMutationPlanner() {
    }

    static ExactSanctionMutationDecision calculate(
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits,
            ExactSanctionRow row,
            Instant now
    ) {
        boolean naturallyExpired = row.expiration().isPresent()
                && !row.expiration().orElseThrow().isAfter(now);
        return switch (request.action()) {
            case REDUCE_DURATION -> reduce(request, limits, row, now, naturallyExpired);
            case END_EARLY -> endEarly(row, now, naturallyExpired);
            case REVOKE -> revoke(row, now);
            case FULL_OVERTURN -> overturn(row, now);
            default -> rejected(
                    "UNSUPPORTED_ACTION",
                    "The requested exact sanction action is unsupported"
            );
        };
    }

    private static ExactSanctionMutationDecision reduce(
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits,
            ExactSanctionRow row,
            Instant now,
            boolean naturallyExpired
    ) {
        Optional<ExactSanctionMutationDecision> stateFailure =
                validateReductionState(row, naturallyExpired);
        if (stateFailure.isPresent()) {
            return stateFailure.orElseThrow();
        }
        Instant replacement = request.replacementExpiration().orElseThrow();
        Optional<ExactSanctionMutationDecision> expirationFailure = validateReplacement(
                limits,
                row,
                now,
                replacement
        );
        if (expirationFailure.isPresent()) {
            return expirationFailure.orElseThrow();
        }
        return new ExactSanctionMutationDecision.Apply(
                row.status(),
                Optional.of(replacement),
                row.endedAt()
        );
    }

    private static Optional<ExactSanctionMutationDecision> validateReductionState(
            ExactSanctionRow row,
            boolean naturallyExpired
    ) {
        if (isDurationActive(row.status()) && !naturallyExpired) {
            return Optional.empty();
        }
        return Optional.of(noChange(
                "ALREADY_INACTIVE",
                "The sanction is already inactive and cannot be reduced",
                row,
                naturallyExpired ? SanctionStatus.EXPIRED : row.status()
        ));
    }

    private static Optional<ExactSanctionMutationDecision> validateReplacement(
            SanctionActionLimits limits,
            ExactSanctionRow row,
            Instant now,
            Instant replacement
    ) {
        if (!replacement.isAfter(now) || replacement.isBefore(row.issuedAt())) {
            return Optional.of(rejected(
                    "INVALID_EXPIRATION",
                    "The reduced expiration must be after now and not before the original issue time"
            ));
        }
        if (row.expiration().isEmpty()) {
            return limits.allowPermanentReduction()
                    ? Optional.empty()
                    : Optional.of(rejected(
                            "PERMANENT_REDUCTION_DENIED",
                            "Current policy does not allow converting a permanent sanction to a finite one"
                    ));
        }
        Instant current = row.expiration().orElseThrow();
        if (replacement.equals(current)) {
            return Optional.of(noChange(
                    "NO_CHANGE",
                    "The sanction already has that expiration",
                    row
            ));
        }
        return replacement.isBefore(current)
                ? Optional.empty()
                : Optional.of(rejected(
                        "NOT_A_REDUCTION",
                        "A reduction must move the expiration earlier"
                ));
    }

    private static ExactSanctionMutationDecision endEarly(
            ExactSanctionRow row,
            Instant now,
            boolean naturallyExpired
    ) {
        if (!isDurationActive(row.status()) || naturallyExpired) {
            return noChange(
                    "ALREADY_INACTIVE",
                    "The sanction is already inactive",
                    row,
                    naturallyExpired ? SanctionStatus.EXPIRED : row.status()
            );
        }
        return new ExactSanctionMutationDecision.Apply(
                SanctionStatus.ENDED_EARLY,
                row.expiration(),
                Optional.of(now)
        );
    }

    private static ExactSanctionMutationDecision revoke(ExactSanctionRow row, Instant now) {
        if (row.status() == SanctionStatus.REVOKED) {
            return noChange("ALREADY_REVOKED", "The sanction is already revoked", row);
        }
        if (row.status() == SanctionStatus.OVERTURNED) {
            return rejected(
                    "TERMINAL_STATE_CONFLICT",
                    "An overturned sanction cannot later be revoked"
            );
        }
        return new ExactSanctionMutationDecision.Apply(
                SanctionStatus.REVOKED,
                row.expiration(),
                Optional.of(now)
        );
    }

    private static ExactSanctionMutationDecision overturn(ExactSanctionRow row, Instant now) {
        if (row.status() == SanctionStatus.OVERTURNED) {
            return noChange("ALREADY_OVERTURNED", "The sanction is already overturned", row);
        }
        if (row.status() == SanctionStatus.REVOKED) {
            return rejected(
                    "TERMINAL_STATE_CONFLICT",
                    "A revoked sanction cannot later be overturned"
            );
        }
        return new ExactSanctionMutationDecision.Apply(
                SanctionStatus.OVERTURNED,
                row.expiration(),
                Optional.of(now)
        );
    }

    private static boolean isDurationActive(SanctionStatus status) {
        return status == SanctionStatus.PENDING || status == SanctionStatus.ACTIVE;
    }

    private static ExactSanctionMutationDecision noChange(
            String code,
            String message,
            ExactSanctionRow row
    ) {
        return noChange(code, message, row, row.status());
    }

    private static ExactSanctionMutationDecision noChange(
            String code,
            String message,
            ExactSanctionRow row,
            SanctionStatus currentStatus
    ) {
        return new ExactSanctionMutationDecision.NoChange(
                new ExactSanctionChangeResult.NoChange(
                        code,
                        message,
                        row.caseId(),
                        row.sanctionId(),
                        currentStatus,
                        row.expiration()
                )
        );
    }

    private static ExactSanctionMutationDecision rejected(String code, String message) {
        return new ExactSanctionMutationDecision.Rejected(
                new ExactSanctionChangeResult.Rejected(code, message)
        );
    }
}
