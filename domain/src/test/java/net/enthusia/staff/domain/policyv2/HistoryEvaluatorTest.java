package net.enthusia.staff.domain.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HistoryEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-10-06T20:00:00Z");
    private static final String POLICY_VERSION = "v2.test";
    private static final String SPAM = "chat.spam";
    private static final String FLOOD = "chat.flood";
    private static final String VPN = "access.vpn";

    @Test
    void relationshipWeightsApplyAndUnrelatedHistoryIsExcluded() {
        OffensePolicy current = PolicyV2TestFixtures.offense(
                SPAM,
                Map.of(SPAM, 1.0, FLOOD, 0.4),
                DecayPolicy.nonDecaying()
        );
        OffensePolicy flood = PolicyV2TestFixtures.offense(
                FLOOD,
                Map.of(FLOOD, 1.0),
                DecayPolicy.nonDecaying()
        );
        OffensePolicy cheat = PolicyV2TestFixtures.offense(
                "cheat.client",
                Map.of("cheat.client", 1.0),
                DecayPolicy.nonDecaying()
        );
        PolicySnapshot snapshot = new PolicySnapshot(POLICY_VERSION, List.of(current, flood, cheat));
        List<BehavioralHistoryEntry> history = List.of(
                confirmed("spam", SPAM, 3),
                confirmed("flood", FLOOD, 2),
                confirmed("cheat", "cheat.client", 1)
        );

        HistoryAssessment result = new HistoryEvaluator().assess(current, NOW, history, snapshot);

        assertEquals(1.4, result.totalContribution(), 0.0000001);
        assertEquals(2, result.contributions().size());
    }

    @Test
    void exponentialDecayIsContinuousAndHalfLifeBased() {
        OffensePolicy offense = PolicyV2TestFixtures.offense(
                SPAM,
                Map.of(SPAM, 1.0),
                DecayPolicy.exponential(Duration.ofDays(10), 0.0, 1.0)
        );
        PolicySnapshot snapshot = new PolicySnapshot(POLICY_VERSION, List.of(offense));
        BehavioralHistoryEntry history = new BehavioralHistoryEntry(
                "case",
                NOW.minus(Duration.ofDays(10)),
                SPAM,
                SPAM,
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );

        double contribution = new HistoryEvaluator()
                .assess(offense, NOW, List.of(history), snapshot)
                .totalContribution();

        assertEquals(0.5, contribution, 0.0000001);
    }

    @Test
    void recentRecurrenceBuildsFadingPatternMemoryAndLengthensLaterHalfLife() {
        OffensePolicy adaptive = PolicyV2TestFixtures.offense(
                SPAM,
                Map.of(SPAM, 1.0),
                DecayPolicy.exponential(
                        Duration.ofDays(10),
                        Duration.ofDays(40),
                        0.5,
                        2.0
                )
        );
        PolicySnapshot snapshot = new PolicySnapshot(POLICY_VERSION, List.of(adaptive));
        BehavioralHistoryEntry first = confirmed("first", SPAM, 20);
        BehavioralHistoryEntry repeated = confirmed("second", SPAM, 10);

        HistoryAssessment result = new HistoryEvaluator()
                .assess(adaptive, NOW, List.of(first, repeated), snapshot);
        HistoryAssessment.Contribution second = result.contributions().get(1);

        assertEquals(1, second.priorRelatedCount());
        assertTrue(second.patternPersistence() > 0.8);
        assertTrue(second.patternPersistence() < 0.9);
        assertTrue(second.halfLifeMultiplier() > 1.4);
        assertTrue(second.halfLifeMultiplier() < 1.5);
        assertTrue(second.decayFactor() > 0.5);
    }

    @Test
    void repeatedRecentFindingsReinforcePatternMemoryButRespectConfiguredCap() {
        OffensePolicy adaptive = PolicyV2TestFixtures.offense(
                SPAM,
                Map.of(SPAM, 1.0),
                DecayPolicy.exponential(
                        Duration.ofDays(10),
                        Duration.ofDays(90),
                        1.0,
                        2.0
                )
        );
        PolicySnapshot snapshot = new PolicySnapshot(POLICY_VERSION, List.of(adaptive));
        List<BehavioralHistoryEntry> history = List.of(
                confirmed("first", SPAM, 3),
                confirmed("second", SPAM, 2),
                confirmed("third", SPAM, 1)
        );

        HistoryAssessment.Contribution third = new HistoryEvaluator()
                .assess(adaptive, NOW, history, snapshot)
                .contributions()
                .get(2);

        assertEquals(2, third.priorRelatedCount());
        assertTrue(third.patternPersistence() > 1.9);
        assertEquals(2.0, third.halfLifeMultiplier(), 0.0000001);
    }

    @Test
    void ancientRelatedHistoryNoLongerSlowsLaterDecayAfterLongCleanPeriod() {
        OffensePolicy adaptive = PolicyV2TestFixtures.offense(
                SPAM,
                Map.of(SPAM, 1.0),
                DecayPolicy.exponential(
                        Duration.ofDays(30),
                        Duration.ofDays(30),
                        0.5,
                        3.0
                )
        );
        PolicySnapshot snapshot = new PolicySnapshot(POLICY_VERSION, List.of(adaptive));
        BehavioralHistoryEntry ancient = confirmed("ancient", SPAM, 400);
        BehavioralHistoryEntry recent = confirmed("recent", SPAM, 10);

        HistoryAssessment.Contribution recentContribution = new HistoryEvaluator()
                .assess(adaptive, NOW, List.of(ancient, recent), snapshot)
                .contributions()
                .get(1);

        assertEquals(1, recentContribution.priorRelatedCount());
        assertTrue(recentContribution.patternPersistence() < 0.001);
        assertTrue(recentContribution.halfLifeMultiplier() < 1.001);
    }

    @Test
    void nonDecayingHistorySurvivesLongCleanPeriodWhileDecayingHistoryApproachesZero() {
        OffensePolicy current = PolicyV2TestFixtures.offense(
                "current.offense",
                Map.of(SPAM, 1.0, VPN, 1.0),
                DecayPolicy.nonDecaying()
        );
        OffensePolicy spam = PolicyV2TestFixtures.offense(
                SPAM,
                Map.of(SPAM, 1.0),
                DecayPolicy.exponential(Duration.ofDays(10), 0.0, 1.0)
        );
        OffensePolicy vpn = PolicyV2TestFixtures.offense(
                VPN,
                Map.of(VPN, 1.0),
                DecayPolicy.nonDecaying()
        );
        PolicySnapshot snapshot = new PolicySnapshot(POLICY_VERSION, List.of(current, spam, vpn));
        List<BehavioralHistoryEntry> history = List.of(
                confirmed("old-spam", SPAM, 365),
                confirmed("old-vpn", VPN, 365)
        );

        HistoryAssessment result = new HistoryEvaluator().assess(current, NOW, history, snapshot);

        assertTrue(result.contributions().get(0).contribution() < 0.000000001);
        assertEquals(1.0, result.contributions().get(1).contribution(), 0.0);
        assertEquals(1.0, result.contributions().get(1).halfLifeMultiplier(), 0.0);
    }

    @Test
    void overturnedHistoryIsRemovedAndReclassifiedHistoryUsesNewFinding() {
        OffensePolicy current = PolicyV2TestFixtures.offense(
                SPAM,
                Map.of(SPAM, 1.0, FLOOD, 0.5),
                DecayPolicy.nonDecaying()
        );
        OffensePolicy flood = PolicyV2TestFixtures.offense(
                FLOOD,
                Map.of(FLOOD, 1.0),
                DecayPolicy.nonDecaying()
        );
        PolicySnapshot snapshot = new PolicySnapshot(POLICY_VERSION, List.of(current, flood));
        List<BehavioralHistoryEntry> history = List.of(
                new BehavioralHistoryEntry(
                        "overturned",
                        NOW.minusSeconds(10),
                        SPAM,
                        null,
                        BehavioralHistoryEntry.FindingState.OVERTURNED
                ),
                new BehavioralHistoryEntry(
                        "reclassified",
                        NOW.minusSeconds(5),
                        "other.old",
                        FLOOD,
                        BehavioralHistoryEntry.FindingState.RECLASSIFIED
                )
        );

        HistoryAssessment result = new HistoryEvaluator().assess(current, NOW, history, snapshot);

        assertEquals(0.5, result.totalContribution(), 0.0);
        assertEquals(FLOOD, result.contributions().getFirst().offenseId());
    }

    @Test
    void assessmentIsDeterministicRegardlessOfInputOrdering() {
        OffensePolicy adaptive = PolicyV2TestFixtures.offense(
                SPAM,
                Map.of(SPAM, 1.0),
                DecayPolicy.exponential(
                        Duration.ofDays(30),
                        Duration.ofDays(120),
                        0.25,
                        2.0
                )
        );
        PolicySnapshot snapshot = new PolicySnapshot(POLICY_VERSION, List.of(adaptive));
        BehavioralHistoryEntry first = confirmed("a", SPAM, 12);
        BehavioralHistoryEntry second = confirmed("b", SPAM, 4);

        HistoryEvaluator evaluator = new HistoryEvaluator();
        HistoryAssessment chronological = evaluator.assess(adaptive, NOW, List.of(first, second), snapshot);
        HistoryAssessment reversed = evaluator.assess(adaptive, NOW, List.of(second, first), snapshot);

        assertEquals(chronological, reversed);
    }

    private static BehavioralHistoryEntry confirmed(String caseId, String offenseId, long ageDays) {
        return new BehavioralHistoryEntry(
                caseId,
                NOW.minus(Duration.ofDays(ageDays)),
                offenseId,
                offenseId,
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );
    }
}
