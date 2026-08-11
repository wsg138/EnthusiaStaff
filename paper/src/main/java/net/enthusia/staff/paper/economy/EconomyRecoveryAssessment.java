package net.enthusia.staff.paper.economy;

import java.util.Objects;
import net.enthusia.staff.domain.economy.EconomyOperation;
import net.enthusia.staff.domain.economy.EconomyOperationState;
import net.enthusia.staff.domain.economy.EconomyReconciliationDecision;

sealed interface EconomyRecoveryAssessment {
    static EconomyRecoveryAssessment assess(
            EconomyOperation operation,
            CurrencyAccountState current
    ) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(current, "current");
        if (!current.playerId().equals(operation.targetId())) {
            return quarantine(
                    "RECOVERY_SNAPSHOT_IDENTITY_MISMATCH",
                    "Currency recovery returned an account snapshot for another player"
            );
        }
        return switch (operation.state()) {
            case COMMITTED -> assessCommitted(operation, current);
            case ROLLED_BACK -> assessRolledBack(operation, current);
            default -> assessIncomplete(operation, current);
        };
    }

    private static EconomyRecoveryAssessment assessCommitted(
            EconomyOperation operation,
            CurrencyAccountState current
    ) {
        String expected = operation.resultChecksum()
                .or(() -> operation.replacementChecksum())
                .orElse("");
        if (expected.equals(current.checksum()) && resultTotalMatches(operation, current)) {
            return new Release("Economy recovery verified the committed state.");
        }
        return quarantine(
                "COMMITTED_RECOVERY_CONFLICT",
                "Current Currency state does not match the committed result"
        );
    }

    private static EconomyRecoveryAssessment assessRolledBack(
            EconomyOperation operation,
            CurrencyAccountState current
    ) {
        boolean checksumMatches = operation.resultChecksum().isEmpty()
                || operation.resultChecksum().orElseThrow().equals(current.checksum());
        if (checksumMatches && resultTotalMatches(operation, current)) {
            return new Release("Economy recovery verified the rolled-back state.");
        }
        return quarantine(
                "ROLLED_BACK_RECOVERY_CONFLICT",
                "Current Currency state does not match the recorded rollback"
        );
    }

    private static EconomyRecoveryAssessment assessIncomplete(
            EconomyOperation operation,
            CurrencyAccountState current
    ) {
        if (operation.state() == EconomyOperationState.LOCKED
                && !operation.hasAnyDurablePlanEvidence()) {
            return new RollBack(
                    false,
                    "RECOVERY_UNAPPLIED",
                    "Economy recovery found no saved apply plan"
            );
        }
        if (!operation.hasCompleteDurablePlanEvidence()) {
            return quarantine(
                    "RECOVERY_PLAN_INCOMPLETE",
                    "Economy recovery found incomplete durable apply-plan evidence"
            );
        }
        EconomyReconciliationDecision decision = EconomyReconciliationDecision.decide(
                current.checksum(),
                operation.beforeChecksum().orElseThrow(),
                operation.replacementChecksum().orElseThrow()
        );
        return assessDecision(operation.state(), decision);
    }

    private static EconomyRecoveryAssessment assessDecision(
            EconomyOperationState state,
            EconomyReconciliationDecision decision
    ) {
        return switch (decision) {
            case ROLL_BACK_UNAPPLIED -> new RollBack(
                    true,
                    "RECOVERY_UNAPPLIED",
                    "Economy recovery verified that the plan was not applied"
            );
            case COMMIT_ALREADY_APPLIED -> state == EconomyOperationState.APPLYING
                    ? new Commit("Economy recovery finalized the already-applied exact removal.")
                    : quarantine(
                            "REPLACEMENT_IN_INVALID_PHASE",
                            "Replacement state exists without an APPLYING journal phase"
                    );
            case RESTORE_BEFORE_STATE -> state == EconomyOperationState.APPLYING
                    ? new Restore()
                    : quarantine(
                            "RECOVERY_STATE_CONFLICT",
                            "Currency state changed before the operation entered APPLYING"
                    );
        };
    }

    private static boolean resultTotalMatches(
            EconomyOperation operation,
            CurrencyAccountState current
    ) {
        return operation.resultTotal().isEmpty()
                || current.authoritativeTotal() == operation.resultTotal().orElseThrow();
    }

    private static Quarantine quarantine(String failureCode, String detail) {
        return new Quarantine(failureCode, detail);
    }

    record Release(String successMessage) implements EconomyRecoveryAssessment {
        public Release {
            Objects.requireNonNull(successMessage, "successMessage");
        }
    }

    record RollBack(
            boolean verified,
            String failureCode,
            String detail
    ) implements EconomyRecoveryAssessment {
        public RollBack {
            Objects.requireNonNull(failureCode, "failureCode");
            Objects.requireNonNull(detail, "detail");
        }
    }

    record Commit(String successMessage) implements EconomyRecoveryAssessment {
        public Commit {
            Objects.requireNonNull(successMessage, "successMessage");
        }
    }

    record Restore() implements EconomyRecoveryAssessment {
    }

    record Quarantine(
            String failureCode,
            String detail
    ) implements EconomyRecoveryAssessment {
        public Quarantine {
            Objects.requireNonNull(failureCode, "failureCode");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
