package net.enthusia.staff.domain.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
    void allowsOnlyCompleteEvidenceOrExplicitFounderOverride() {
        assertTrue(gate.assess(evidence(START.plus(Duration.ofHours(168)), true), Optional.empty()).allowed());

        FounderOverride override = new FounderOverride(
                UUID.randomUUID(),
                "I understand that mismatches can incorrectly enforce sanctions.",
                "Emergency recovery during a documented incident"
        );
        CutoverAssessment overridden = gate.assess(evidence(START.plus(Duration.ofHours(24)), false), Optional.of(override));
        assertTrue(overridden.allowed());
        assertTrue(overridden.founderOverrideUsed());
        assertFalse(overridden.blockers().isEmpty());
    }

    private static CutoverEvidence evidence(Instant assessedAt, boolean decisionsMatch) {
        return new CutoverEvidence(
                START,
                assessedAt,
                true,
                true,
                true,
                true,
                true,
                new DecisionComparison(10, decisionsMatch ? 0 : 1),
                new DecisionComparison(10, 0),
                new DecisionComparison(10, 0),
                0,
                true,
                true
        );
    }
}
