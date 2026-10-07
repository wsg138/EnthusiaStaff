package net.enthusia.staff.domain.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PolicyV2AdversarialScenarioTest {
    private static final Instant NOW = Instant.parse("2026-10-07T03:20:00Z");
    private static final String SPAM = "chat.spam";
    private static final String FLOOD = "chat.flood";
    private static final String SEVERE = "safety.credible-threat";
    private static final String UNRELATED = "market.unrelated";

    @Test
    void firstImmediatePartialAndLongCleanRecurrenceDecaySmoothly() {
        OffensePolicy spam = offense(SPAM, Map.of(SPAM, 1.0), adaptiveDecay());
        PolicySnapshot snapshot = new PolicySnapshot("w4.decay", List.of(spam));
        HistoryEvaluator evaluator = new HistoryEvaluator();

        assertEquals(0.0, evaluator.assess(spam, NOW, List.of(), snapshot).totalContribution(), 0.0);
        assertTrue(contribution(evaluator, spam, snapshot, entry("immediate", SPAM, Duration.ofMinutes(1))) > 0.99);
        assertEquals(
                0.5,
                contribution(evaluator, spam, snapshot, entry("half-life", SPAM, Duration.ofDays(10))),
                0.0000001
        );
        assertTrue(contribution(evaluator, spam, snapshot, entry("clean", SPAM, Duration.ofDays(365))) < 0.000000001);
    }

    @Test
    void severalRapidOffensesSlowLaterDecayWithinTheConfiguredCap() {
        OffensePolicy spam = offense(SPAM, Map.of(SPAM, 1.0), adaptiveDecay());
        PolicySnapshot snapshot = new PolicySnapshot("w4.rapid", List.of(spam));
        List<BehavioralHistoryEntry> history = List.of(
                entry("one", SPAM, Duration.ofDays(3)),
                entry("two", SPAM, Duration.ofDays(2)),
                entry("three", SPAM, Duration.ofDays(1))
        );

        HistoryAssessment result = new HistoryEvaluator().assess(spam, NOW, history, snapshot);

        assertEquals(List.of(0, 1, 2), result.contributions().stream()
                .map(HistoryAssessment.Contribution::priorRelatedCount).toList());
        assertEquals(2.0, result.contributions().get(2).halfLifeMultiplier(), 0.0);
    }

    @Test
    void decayedPriorStillCountsTowardLaterRepeatSlowdownUnderCurrentContract() {
        OffensePolicy spam = offense(SPAM, Map.of(SPAM, 1.0), adaptiveDecay());
        PolicySnapshot snapshot = new PolicySnapshot("w4.repeat-after-decay", List.of(spam));
        List<BehavioralHistoryEntry> history = List.of(
                entry("ancient", SPAM, Duration.ofDays(365)),
                entry("recent", SPAM, Duration.ofDays(10))
        );

        HistoryAssessment result = new HistoryEvaluator().assess(spam, NOW, history, snapshot);
        HistoryAssessment.Contribution recent = result.contributions().get(1);

        assertTrue(result.contributions().get(0).decayFactor() < 0.000000001);
        assertEquals(1, recent.priorRelatedCount());
        assertEquals(1.5, recent.halfLifeMultiplier(), 0.0);
        assertTrue(recent.decayFactor() > 0.5);
    }

    @Test
    void alternatingRelatedOffensesContributeByExplicitRelationshipWeights() {
        OffensePolicy spam = offense(SPAM, Map.of(SPAM, 1.0, FLOOD, 0.4), DecayPolicy.nonDecaying());
        OffensePolicy flood = offense(FLOOD, Map.of(FLOOD, 1.0, SPAM, 0.4), DecayPolicy.nonDecaying());
        PolicySnapshot snapshot = new PolicySnapshot("w4.alternating", List.of(spam, flood));
        List<BehavioralHistoryEntry> history = List.of(
                entry("spam", SPAM, Duration.ofDays(2)),
                entry("flood", FLOOD, Duration.ofDays(1))
        );

        HistoryAssessment result = new HistoryEvaluator().assess(spam, NOW, history, snapshot);

        assertEquals(1.4, result.totalContribution(), 0.0000001);
        assertEquals(List.of(SPAM, FLOOD), result.contributions().stream()
                .map(HistoryAssessment.Contribution::offenseId).toList());
    }

    @Test
    void unrelatedHistoryIsExcludedEvenWhenItIsRecent() {
        OffensePolicy spam = offense(SPAM, Map.of(SPAM, 1.0), DecayPolicy.nonDecaying());
        OffensePolicy unrelated = offense(UNRELATED, Map.of(UNRELATED, 1.0), DecayPolicy.nonDecaying());
        PolicySnapshot snapshot = new PolicySnapshot("w4.unrelated", List.of(spam, unrelated));

        HistoryAssessment result = new HistoryEvaluator().assess(
                spam,
                NOW,
                List.of(entry("other", UNRELATED, Duration.ofMinutes(1))),
                snapshot
        );

        assertTrue(result.contributions().isEmpty());
        assertEquals(0.0, result.totalContribution(), 0.0);
    }


    @Test
    void moreSeriousHistoricalOffenseCanOutweighOrdinaryRelatedHistory() {
        OffensePolicy spam = offense(
                SPAM,
                Map.of(SPAM, 0.25, SEVERE, 1.0),
                DecayPolicy.nonDecaying()
        );
        OffensePolicy severe = offense(SEVERE, Map.of(SEVERE, 1.0), DecayPolicy.nonDecaying());
        PolicySnapshot snapshot = new PolicySnapshot("w4.more-serious-history", List.of(spam, severe));

        HistoryAssessment result = new HistoryEvaluator().assess(
                spam,
                NOW,
                List.of(
                        entry("ordinary", SPAM, Duration.ofDays(1)),
                        entry("severe", SEVERE, Duration.ofDays(1))
                ),
                snapshot
        );

        assertEquals(1.25, result.totalContribution(), 0.0000001);
        assertTrue(result.contributions().stream()
                .filter(contribution -> contribution.offenseId().equals(SEVERE))
                .findFirst().orElseThrow().contribution()
                > result.contributions().stream()
                        .filter(contribution -> contribution.offenseId().equals(SPAM))
                        .findFirst().orElseThrow().contribution());
    }

    @Test
    void severeNonDecayingHistorySurvivesALongCleanPeriod() {
        OffensePolicy spam = offense(SPAM, Map.of(SEVERE, 1.0), adaptiveDecay());
        OffensePolicy severe = offense(SEVERE, Map.of(SEVERE, 1.0), DecayPolicy.nonDecaying());
        PolicySnapshot snapshot = new PolicySnapshot("w4.severe", List.of(spam, severe));

        HistoryAssessment result = new HistoryEvaluator().assess(
                spam,
                NOW,
                List.of(entry("severe-old", SEVERE, Duration.ofDays(3650))),
                snapshot
        );

        assertEquals(1.0, result.totalContribution(), 0.0);
        assertEquals(1.0, result.contributions().getFirst().decayFactor(), 0.0);
    }

    @Test
    void deterministicReplayIgnoresCallerHistoryOrdering() {
        OffensePolicy spam = offense(SPAM, Map.of(SPAM, 1.0), adaptiveDecay());
        PolicySnapshot snapshot = new PolicySnapshot("w4.replay", List.of(spam));
        IncidentFinding finding = new IncidentFinding(SPAM, Map.of());
        BehavioralHistoryEntry older = entry("a", SPAM, Duration.ofDays(5));
        BehavioralHistoryEntry newer = entry("b", SPAM, Duration.ofDays(1));
        PolicyResolver resolver = new PolicyResolver();

        PolicyResolution ordered = resolver.resolve(snapshot, finding, NOW, List.of(older, newer));
        PolicyResolution shuffled = resolver.resolve(snapshot, finding, NOW, List.of(newer, older));

        assertEquals(ordered, shuffled);
    }

    @Test
    void changedPolicyVersionProducesVersionSpecificDeterministicReplay() {
        BehavioralHistoryEntry prior = entry("prior", SPAM, Duration.ofDays(10));
        PolicySnapshot tenDay = new PolicySnapshot(
                "w4.policy.1",
                List.of(offense(SPAM, Map.of(SPAM, 1.0), DecayPolicy.exponential(Duration.ofDays(10), 0.0, 1.0)))
        );
        PolicySnapshot twentyDay = new PolicySnapshot(
                "w4.policy.2",
                List.of(offense(SPAM, Map.of(SPAM, 1.0), DecayPolicy.exponential(Duration.ofDays(20), 0.0, 1.0)))
        );
        PolicyResolver resolver = new PolicyResolver();

        PolicyResolution oldResult = resolver.resolve(tenDay, new IncidentFinding(SPAM, Map.of()), NOW, List.of(prior));
        PolicyResolution newResult = resolver.resolve(twentyDay, new IncidentFinding(SPAM, Map.of()), NOW, List.of(prior));

        assertEquals("w4.policy.1", oldResult.policyVersion());
        assertEquals("w4.policy.2", newResult.policyVersion());
        assertEquals(0.5, oldResult.history().totalContribution(), 0.0000001);
        assertTrue(newResult.history().totalContribution() > 0.5);
        assertNotEquals(oldResult, newResult);
    }

    @Test
    void brandNewUnclassifiedConductFailsClosedToPolicyGapReview() {
        PolicySnapshot snapshot = new PolicySnapshot(
                "w4.policy-gap",
                List.of(offense(SPAM, Map.of(SPAM, 1.0), DecayPolicy.nonDecaying()))
        );

        PolicyResolution result = new PolicyResolver().resolve(
                snapshot,
                new IncidentFinding("novel.unclassified-conduct", Map.of()),
                NOW,
                List.of()
        );

        assertTrue(result.requiresReview());
        assertEquals(
                "policy-gap.unknown-offense",
                assertInstanceOf(PolicyAction.RequiresReview.class, result.action()).reasonCode()
        );
    }

    private static OffensePolicy offense(String id, Map<String, Double> relationships, DecayPolicy decay) {
        return PolicyV2TestFixtures.offense(id, relationships, decay);
    }

    private static DecayPolicy adaptiveDecay() {
        return DecayPolicy.exponential(Duration.ofDays(10), 0.5, 2.0);
    }

    private static BehavioralHistoryEntry entry(String caseId, String offenseId, Duration age) {
        return new BehavioralHistoryEntry(
                caseId,
                NOW.minus(age),
                offenseId,
                offenseId,
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );
    }

    private static double contribution(
            HistoryEvaluator evaluator,
            OffensePolicy offense,
            PolicySnapshot snapshot,
            BehavioralHistoryEntry entry
    ) {
        return evaluator.assess(offense, NOW, List.of(entry), snapshot).totalContribution();
    }
}
