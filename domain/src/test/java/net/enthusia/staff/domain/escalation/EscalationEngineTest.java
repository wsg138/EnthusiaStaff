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
        PriorOffense prior = new PriorOffense("chat.hate", 80, 0, NOW.minus(Duration.ofDays(10)), true, false);

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(3, decision.effectiveOrdinal());
        assertEquals(1, decision.recencyBonus());
        assertEquals(3, decision.selectedStep().ordinal());
    }

    @Test
    void minorContributionDecaysEveryNinetyCleanDays() {
        ReasonPolicy policy = policy(true);
        PriorOffense prior = new PriorOffense("chat.hate", 20, 0, NOW.minus(Duration.ofDays(180)), true, false);

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(1, decision.rawOrdinal());
        assertEquals(0, decision.effectiveOrdinal());
        assertEquals(2, decision.contributions().getFirst().decayedBy());
    }

    @Test
    void contributionDoesNotDecayBeforeNinetyCleanDays() {
        ReasonPolicy policy = policy(true);
        PriorOffense prior = new PriorOffense("chat.hate", 20, 0, NOW.minus(Duration.ofDays(89)), true, false);

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(0, decision.contributions().getFirst().decayedBy());
        assertEquals(1, decision.effectiveOrdinal());
    }

    @Test
    void contributionDecaysAtNinetyCleanDays() {
        ReasonPolicy policy = policy(true);
        PriorOffense prior = new PriorOffense("chat.hate", 20, 0, NOW.minus(Duration.ofDays(90)), true, false);

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(1, decision.contributions().getFirst().decayedBy());
        assertEquals(0, decision.effectiveOrdinal());
    }

    @Test
    void recentRelatedOffenseResetsCleanPeriodForOlderHistory() {
        ReasonPolicy policy = policy(true);
        List<PriorOffense> history = List.of(
                new PriorOffense("chat.hate", 20, 0, NOW.minus(Duration.ofDays(180)), true, false),
                new PriorOffense("chat.hate", 20, 1, NOW.minus(Duration.ofDays(10)), true, false)
        );

        EscalationDecision decision = engine.decide(policy, history, NOW);

        assertEquals(3, decision.rawOrdinal());
        assertEquals(3, decision.effectiveOrdinal());
        assertEquals(1, decision.recencyBonus());
        assertEquals(0, decision.contributions().get(0).decayedBy());
        assertEquals(0, decision.contributions().get(1).decayedBy());
    }

    @Test
    void sharedCleanPeriodDecayAppliesToAllRelatedContributions() {
        ReasonPolicy policy = policy(true);
        List<PriorOffense> history = List.of(
                new PriorOffense("chat.hate", 20, 0, NOW.minus(Duration.ofDays(365)), true, false),
                new PriorOffense("chat.hate", 20, 1, NOW.minus(Duration.ofDays(180)), true, false)
        );

        EscalationDecision decision = engine.decide(policy, history, NOW);

        assertEquals(2, decision.rawOrdinal());
        assertEquals(0, decision.effectiveOrdinal());
        assertEquals(2, decision.contributions().get(0).decayedBy());
        assertEquals(2, decision.contributions().get(1).decayedBy());
    }

    @Test
    void nonDecayingPolicyPreservesOldContribution() {
        ReasonPolicy policy = policy(false);
        PriorOffense prior = new PriorOffense("chat.hate", 20, 0, NOW.minus(Duration.ofDays(365)), true, false);

        EscalationDecision decision = engine.decide(policy, List.of(prior), NOW);

        assertEquals(0, decision.contributions().getFirst().decayedBy());
        assertEquals(1, decision.effectiveOrdinal());
    }

    @Test
    void overturnedAndUnrelatedHistoryNeverContribute() {
        ReasonPolicy policy = policy(false);
        List<PriorOffense> history = List.of(
                new PriorOffense("chat.hate", 90, 4, NOW.minus(Duration.ofDays(1)), true, true),
                new PriorOffense("market", 90, 4, NOW.minus(Duration.ofDays(1)), true, false)
        );

        assertEquals(0, engine.decide(policy, history, NOW).effectiveOrdinal());
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
