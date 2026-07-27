package net.enthusia.staff.domain.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CutoverGateTest {
    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private final CutoverGate gate = new CutoverGate();

    @Test
    void blocksBeforeFullWeekEvenWhenAllComparisonsMatch() {
        CutoverAssessment assessment = gate.assess(evidence(START.plus(Duration.ofHours(167)), true), Optional.empty());
        assertFalse(assessment.allowed());
        assertTrue(assessment.blockers().contains("SHADOW_WINDOW_INCOMPLETE"));
    }

    @Test
    void blocksAnyUnexplainedDecisionMismatch() {
        CutoverAssessment assessment = gate.assess(evidence(START.plus(Duration.ofHours(168)), false), Optional.empty());
        assertFalse(assessment.allowed());
        assertTrue(assessment.blockers().contains("LOGIN_DECISION_MISMATCH"));
    }

    @Test
    void blocksAWeekWithoutDailyShadowCoverage() {
        CutoverEvidence sparse = evidence(
                START.plus(Duration.ofHours(168)),
                true,
                List.of(START, START.plus(Duration.ofHours(168))),
                true
        );

        CutoverAssessment assessment = gate.assess(sparse, Optional.empty());

        assertFalse(assessment.allowed());
        assertTrue(assessment.blockers().contains("SHADOW_DAILY_COVERAGE_INCOMPLETE"));
    }

    @Test
    void maintenanceTimeDoesNotExtendTheShadowWindow() {
        CutoverEvidence stoppedEarly = new CutoverEvidence(
                START,
                START.plus(Duration.ofHours(160)),
                START.plus(Duration.ofHours(200)),
                dailySummaries(START.plus(Duration.ofHours(160))),
                true,
                true,
                true,
                true,
                true,
                new DecisionComparison(10, 0),
                new DecisionComparison(10, 0),
                new DecisionComparison(10, 0),
                0,
                true,
                true,
                true
        );

        CutoverAssessment assessment = gate.assess(stoppedEarly, Optional.empty());

        assertFalse(assessment.allowed());
        assertTrue(assessment.blockers().contains("SHADOW_WINDOW_INCOMPLETE"));
    }

    @Test
    void blocksWhileAnyMigrationRunIsStillInProgress() {
        CutoverAssessment assessment = gate.assess(
                evidence(START.plus(Duration.ofHours(168)), true, dailySummaries(
                        START.plus(Duration.ofHours(168))
                ), false),
                Optional.empty()
        );

        assertFalse(assessment.allowed());
        assertTrue(assessment.blockers().contains("MIGRATION_OPERATION_IN_PROGRESS"));
    }

    @Test
    void allowsOnlyCompleteEvidenceOrExplicitFounderOverride() {
        assertTrue(gate.assess(evidence(START.plus(Duration.ofHours(168)), true), Optional.empty()).allowed());

        FounderOverride override = new FounderOverride(
                UUID.randomUUID(),
                FounderOverride.REQUIRED_ACKNOWLEDGEMENT,
                "Emergency recovery during a documented incident"
        );
        CutoverAssessment overridden = gate.assess(evidence(START.plus(Duration.ofHours(24)), false), Optional.of(override));
        assertFalse(overridden.allowed());
        assertFalse(overridden.founderOverrideUsed());

        CutoverAssessment timeOnlyOverride = gate.assess(
                evidence(START.plus(Duration.ofHours(24)), true), Optional.of(override)
        );
        assertTrue(timeOnlyOverride.allowed());
        assertTrue(timeOnlyOverride.founderOverrideUsed());
        assertFalse(timeOnlyOverride.blockers().isEmpty());
    }

    @Test
    void rejectsAnOverrideWithoutTheExactWarningAcknowledgement() {
        assertThrows(IllegalArgumentException.class, () -> new FounderOverride(
                UUID.randomUUID(),
                "I understand",
                "Emergency recovery during a documented incident"
        ));
    }

    private static CutoverEvidence evidence(Instant assessedAt, boolean decisionsMatch) {
        return evidence(assessedAt, decisionsMatch, dailySummaries(assessedAt), true);
    }

    private static CutoverEvidence evidence(
            Instant assessedAt,
            boolean decisionsMatch,
            List<Instant> summaries,
            boolean migrationIdle
    ) {
        return new CutoverEvidence(
                START,
                assessedAt,
                assessedAt,
                summaries,
                true,
                true,
                true,
                true,
                true,
                new DecisionComparison(10, decisionsMatch ? 0 : 1),
                new DecisionComparison(10, 0),
                new DecisionComparison(10, 0),
                0,
                migrationIdle,
                true,
                true
        );
    }

    private static List<Instant> dailySummaries(Instant end) {
        List<Instant> summaries = new ArrayList<>();
        Instant current = START;
        while (!current.isAfter(end)) {
            summaries.add(current);
            current = current.plus(Duration.ofHours(24));
        }
        return List.copyOf(summaries);
    }
}
