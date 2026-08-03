package net.enthusia.staff.domain.escalation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class EscalationEngineTest {
    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");
    private final EscalationEngine engine = new EscalationEngine();

    @Test
    void moreSeriousHistoryAndRecencyAdvanceThreeSteps() {
        ReasonPolicy policy = policy(false);
        PriorOffense prior = prior(80, 10, DecayEligibility.INELIGIBLE);

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(3, decision.effectiveOrdinal());
        assertEquals(1, decision.recencyBonus());
        assertEquals(3, decision.selectedStep().ordinal());
    }

    @Test
    void eligibleMinorContributionDecaysEveryNinetyCleanDays() {
        ReasonPolicy policy = policy(true);
        PriorOffense prior = prior(20, 180, DecayEligibility.ELIGIBLE);

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(1, decision.rawOrdinal());
        assertEquals(0, decision.effectiveOrdinal());
        assertEquals(DecayEligibility.ELIGIBLE, decision.contributions().getFirst().decayEligibility());
        assertEquals(2, decision.contributions().getFirst().decayedBy());
    }

    @Test
    void contributionDoesNotDecayBeforeNinetyCleanDays() {
        ReasonPolicy policy = policy(true);
        PriorOffense prior = prior(20, 89, DecayEligibility.ELIGIBLE);

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(0, decision.contributions().getFirst().decayedBy());
        assertEquals(1, decision.effectiveOrdinal());
    }

    @Test
    void contributionDecaysAtNinetyCleanDays() {
        ReasonPolicy policy = policy(true);
        PriorOffense prior = prior(20, 90, DecayEligibility.ELIGIBLE);

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(1, decision.contributions().getFirst().decayedBy());
        assertEquals(0, decision.effectiveOrdinal());
    }

    @Test
    void recentRelatedOffenseResetsCleanPeriodForOlderHistory() {
        ReasonPolicy policy = policy(true);
        List<PriorOffense> history = List.of(
                prior(20, 180, DecayEligibility.ELIGIBLE),
                prior(20, 10, DecayEligibility.ELIGIBLE)
        );

        EscalationDecision decision = engine.decide(policy, history, NOW);

        assertEquals(3, decision.rawOrdinal());
        assertEquals(3, decision.effectiveOrdinal());
        assertEquals(1, decision.recencyBonus());
        assertEquals(0, decision.contributions().get(0).decayedBy());
        assertEquals(0, decision.contributions().get(1).decayedBy());
    }

    @Test
    void sharedCleanPeriodOnlyReducesEligibleContributions() {
        ReasonPolicy policy = policy(true);
        List<PriorOffense> history = List.of(
                prior(20, 365, DecayEligibility.ELIGIBLE),
                prior(20, 180, DecayEligibility.INELIGIBLE)
        );

        EscalationDecision decision = engine.decide(policy, history, NOW);

        assertEquals(2, decision.rawOrdinal());
        assertEquals(1, decision.effectiveOrdinal());
        assertEquals(2, decision.contributions().get(0).decayedBy());
        assertEquals(0, decision.contributions().get(1).decayedBy());
    }

    @Test
    void seriousNonDecayingHistoryDoesNotDecayUnderLaterEligiblePolicy() {
        ReasonPolicy policy = policy(true);
        PriorOffense prior = prior(80, 365, DecayEligibility.INELIGIBLE);

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(2, decision.rawOrdinal());
        assertEquals(2, decision.effectiveOrdinal());
        assertEquals(DecayEligibility.INELIGIBLE, decision.contributions().getFirst().decayEligibility());
        assertEquals(0, decision.contributions().getFirst().decayedBy());
    }

    @Test
    void eligibleMinorHistoryStillDecaysUnderLaterNonDecayingPolicy() {
        ReasonPolicy policy = policy(false);
        PriorOffense prior = prior(20, 180, DecayEligibility.ELIGIBLE);

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(1, decision.rawOrdinal());
        assertEquals(0, decision.effectiveOrdinal());
        assertEquals(2, decision.contributions().getFirst().decayedBy());
    }

    @Test
    void legacyUnknownEligibilityNeverInventsDecay() {
        ReasonPolicy policy = policy(true);
        PriorOffense prior = new PriorOffense(
                "chat.hate",
                20,
                0,
                NOW.minus(Duration.ofDays(365)),
                true,
                false
        );

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(DecayEligibility.UNKNOWN, decision.contributions().getFirst().decayEligibility());
        assertEquals(0, decision.contributions().getFirst().decayedBy());
        assertEquals(1, decision.effectiveOrdinal());
    }

    @Test
    void overturnedAndUnrelatedHistoryNeverContribute() {
        ReasonPolicy policy = policy(false);
        List<PriorOffense> history = List.of(
                new PriorOffense(
                        "chat.hate",
                        90,
                        4,
                        NOW.minus(Duration.ofDays(1)),
                        true,
                        true,
                        DecayEligibility.INELIGIBLE
                ),
                new PriorOffense(
                        "market",
                        90,
                        4,
                        NOW.minus(Duration.ofDays(1)),
                        true,
                        false,
                        DecayEligibility.INELIGIBLE
                )
        );

        assertEquals(0, engine.decide(policy, history, NOW).effectiveOrdinal());
    }

    private static PriorOffense prior(int severity, long cleanDays, DecayEligibility eligibility) {
        return new PriorOffense(
                "chat.hate",
                severity,
                0,
                NOW.minus(Duration.ofDays(cleanDays)),
                true,
                false,
                eligibility
        );
    }

    private static ReasonPolicy policy(boolean decay) {
        List<PunishmentStep> steps = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> new PunishmentStep(
                        index,
                        "Step " + index,
                        List.of(new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofDays(index + 1L))))
                ))
                .toList();
        return new ReasonPolicy("hate.full-slur-untargeted", "chat.hate", "Hate speech", 50, decay, steps);
    }
}
