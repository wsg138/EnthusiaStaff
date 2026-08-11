package net.enthusia.staff.paper.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.enthusia.staff.domain.economy.EconomyAmountMode;
import net.enthusia.staff.domain.economy.EconomyOperation;
import net.enthusia.staff.domain.economy.EconomyOperationState;
import net.enthusia.staff.domain.economy.EconomyTerminalOutcome;
import org.junit.jupiter.api.Test;

final class EconomyRecoveryAssessmentTest {
    private static final UUID TARGET_ID = UUID.fromString("24a139cb-238a-488e-b931-da5ef2dd6a4d");
    private static final UUID OTHER_ID = UUID.fromString("02cfe04d-023b-4a30-bcbb-d0697134fb4d");
    private static final String BEFORE_CHECKSUM = "a".repeat(64);
    private static final String REPLACEMENT_CHECKSUM = "b".repeat(64);
    private static final String RESULT_CHECKSUM = "c".repeat(64);
    private static final String DIVERGED_CHECKSUM = "d".repeat(64);
    private static final long BEFORE_TOTAL = 100L;
    private static final long REPLACEMENT_TOTAL = 75L;
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void rejectsSnapshotsForAnotherPlayerBeforeConsideringJournalState() {
        EconomyRecoveryAssessment assessment = EconomyRecoveryAssessment.assess(
                operation(EconomyOperationState.APPLYING, Evidence.COMPLETE),
                account(OTHER_ID, BEFORE_TOTAL, BEFORE_CHECKSUM)
        );

        EconomyRecoveryAssessment.Quarantine quarantine = assertInstanceOf(
                EconomyRecoveryAssessment.Quarantine.class,
                assessment
        );
        assertEquals("RECOVERY_SNAPSHOT_IDENTITY_MISMATCH", quarantine.failureCode());
    }

    @Test
    void releasesOnlyCommittedStateWithMatchingResultEvidence() {
        EconomyOperation operation = operationWithResult(
                EconomyOperationState.COMMITTED,
                REPLACEMENT_TOTAL,
                RESULT_CHECKSUM
        );

        assertInstanceOf(
                EconomyRecoveryAssessment.Release.class,
                EconomyRecoveryAssessment.assess(
                        operation,
                        account(TARGET_ID, REPLACEMENT_TOTAL, RESULT_CHECKSUM)
                )
        );
        EconomyRecoveryAssessment.Quarantine conflict = assertInstanceOf(
                EconomyRecoveryAssessment.Quarantine.class,
                EconomyRecoveryAssessment.assess(
                        operation,
                        account(TARGET_ID, REPLACEMENT_TOTAL, REPLACEMENT_CHECKSUM)
                )
        );
        assertEquals("COMMITTED_RECOVERY_CONFLICT", conflict.failureCode());
    }

    @Test
    void rolledBackStateUsesRecordedEvidenceWhenItExists() {
        EconomyOperation operation = operationWithResult(
                EconomyOperationState.ROLLED_BACK,
                BEFORE_TOTAL,
                RESULT_CHECKSUM
        );

        assertInstanceOf(
                EconomyRecoveryAssessment.Release.class,
                EconomyRecoveryAssessment.assess(
                        operation,
                        account(TARGET_ID, BEFORE_TOTAL, RESULT_CHECKSUM)
                )
        );
        EconomyRecoveryAssessment.Quarantine conflict = assertInstanceOf(
                EconomyRecoveryAssessment.Quarantine.class,
                EconomyRecoveryAssessment.assess(
                        operation,
                        account(TARGET_ID, REPLACEMENT_TOTAL, RESULT_CHECKSUM)
                )
        );
        assertEquals("ROLLED_BACK_RECOVERY_CONFLICT", conflict.failureCode());
    }

    @Test
    void lockedOperationWithoutPlanRollsBackAsUnapplied() {
        EconomyRecoveryAssessment.RollBack rollback = assertInstanceOf(
                EconomyRecoveryAssessment.RollBack.class,
                EconomyRecoveryAssessment.assess(
                        operation(EconomyOperationState.LOCKED, Evidence.NONE),
                        account(TARGET_ID, BEFORE_TOTAL, BEFORE_CHECKSUM)
                )
        );

        assertFalse(rollback.verified());
        assertEquals("RECOVERY_UNAPPLIED", rollback.failureCode());
    }

    @Test
    void partialPlanEvidenceIsQuarantined() {
        EconomyRecoveryAssessment.Quarantine quarantine = assertInstanceOf(
                EconomyRecoveryAssessment.Quarantine.class,
                EconomyRecoveryAssessment.assess(
                        operation(EconomyOperationState.LOCKED, Evidence.PARTIAL),
                        account(TARGET_ID, BEFORE_TOTAL, BEFORE_CHECKSUM)
                )
        );

        assertEquals("RECOVERY_PLAN_INCOMPLETE", quarantine.failureCode());
    }

    @Test
    void completePlanMapsExactProviderStateToRecoveryAction() {
        EconomyOperation applying = operation(EconomyOperationState.APPLYING, Evidence.COMPLETE);

        EconomyRecoveryAssessment.RollBack rollback = assertInstanceOf(
                EconomyRecoveryAssessment.RollBack.class,
                EconomyRecoveryAssessment.assess(
                        applying,
                        account(TARGET_ID, BEFORE_TOTAL, BEFORE_CHECKSUM)
                )
        );
        assertTrue(rollback.verified());
        assertInstanceOf(
                EconomyRecoveryAssessment.Commit.class,
                EconomyRecoveryAssessment.assess(
                        applying,
                        account(TARGET_ID, REPLACEMENT_TOTAL, REPLACEMENT_CHECKSUM)
                )
        );
        assertInstanceOf(
                EconomyRecoveryAssessment.Restore.class,
                EconomyRecoveryAssessment.assess(
                        applying,
                        account(TARGET_ID, BEFORE_TOTAL + 1L, DIVERGED_CHECKSUM)
                )
        );
    }

    @Test
    void providerChangesBeforeApplyingAreQuarantined() {
        EconomyOperation validated = operation(EconomyOperationState.VALIDATED, Evidence.COMPLETE);

        EconomyRecoveryAssessment.Quarantine replacement = assertInstanceOf(
                EconomyRecoveryAssessment.Quarantine.class,
                EconomyRecoveryAssessment.assess(
                        validated,
                        account(TARGET_ID, REPLACEMENT_TOTAL, REPLACEMENT_CHECKSUM)
                )
        );
        assertEquals("REPLACEMENT_IN_INVALID_PHASE", replacement.failureCode());
        EconomyRecoveryAssessment.Quarantine diverged = assertInstanceOf(
                EconomyRecoveryAssessment.Quarantine.class,
                EconomyRecoveryAssessment.assess(
                        validated,
                        account(TARGET_ID, BEFORE_TOTAL + 1L, DIVERGED_CHECKSUM)
                )
        );
        assertEquals("RECOVERY_STATE_CONFLICT", diverged.failureCode());
    }

    private static EconomyOperation operation(EconomyOperationState state, Evidence evidence) {
        return operation(state, evidence, OptionalLong.empty(), Optional.empty());
    }

    private static EconomyOperation operationWithResult(
            EconomyOperationState state,
            long resultTotal,
            String resultChecksum
    ) {
        return operation(
                state,
                Evidence.COMPLETE,
                OptionalLong.of(resultTotal),
                Optional.of(resultChecksum)
        );
    }

    private static EconomyOperation operation(
            EconomyOperationState state,
            Evidence evidence,
            OptionalLong resultTotal,
            Optional<String> resultChecksum
    ) {
        Optional<String> beforeChecksum = evidence == Evidence.NONE
                ? Optional.empty()
                : Optional.of(BEFORE_CHECKSUM);
        OptionalLong authoritativeTotal = evidence == Evidence.COMPLETE
                ? OptionalLong.of(BEFORE_TOTAL)
                : OptionalLong.empty();
        Optional<String> replacementChecksum = evidence == Evidence.COMPLETE
                ? Optional.of(REPLACEMENT_CHECKSUM)
                : Optional.empty();
        Optional<String> durableJson = evidence == Evidence.COMPLETE
                ? Optional.of("{}")
                : Optional.empty();
        return new EconomyOperation(
                UUID.randomUUID(),
                "economy:recovery-test",
                "01ARZ3NDEKTSV4RR",
                TARGET_ID,
                Optional.of(UUID.randomUUID()),
                EconomyAmountMode.CUSTOM,
                OptionalLong.of(25L),
                authoritativeTotal,
                Optional.of("paper-1"),
                state,
                terminalOutcome(state),
                1L,
                Optional.of(NOW.plusSeconds(30L)),
                beforeChecksum,
                replacementChecksum,
                durableJson,
                durableJson,
                resultTotal,
                resultChecksum,
                resultChecksum.map(ignored -> "{}"),
                Optional.empty(),
                Optional.empty(),
                NOW,
                NOW
        );
    }

    private static Optional<EconomyTerminalOutcome> terminalOutcome(EconomyOperationState state) {
        return switch (state) {
            case COMMITTED -> Optional.of(EconomyTerminalOutcome.COMMITTED);
            case ROLLED_BACK -> Optional.of(EconomyTerminalOutcome.ROLLED_BACK);
            case QUARANTINED -> Optional.of(EconomyTerminalOutcome.QUARANTINED);
            default -> Optional.empty();
        };
    }

    private static CurrencyAccountState account(UUID playerId, long total, String checksum) {
        return new CurrencyAccountState(
                playerId,
                total,
                1L,
                new byte[0],
                new byte[0],
                0L,
                0L,
                total,
                checksum
        );
    }

    private enum Evidence {
        NONE,
        PARTIAL,
        COMPLETE
    }
}
