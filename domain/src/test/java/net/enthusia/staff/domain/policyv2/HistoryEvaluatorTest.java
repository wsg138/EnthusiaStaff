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

    @Test
    void relationshipWeightsApplyAndUnrelatedHistoryIsExcluded() {
        OffensePolicy current = PolicyV2TestFixtures.offense(
                "chat.spam",
                Map.of("chat.spam", 1.0, "chat.flood", 0.4),
                DecayPolicy.nonDecaying()
        );
        OffensePolicy flood = PolicyV2TestFixtures.offense(
                "chat.flood",
                Map.of("chat.flood", 1.0),
                DecayPolicy.nonDecaying()
        );
        OffensePolicy cheat = PolicyV2TestFixtures.offense(
                "cheat.client",
                Map.of("cheat.client", 1.0),
                DecayPolicy.nonDecaying()
        );
        PolicySnapshot snapshot = new PolicySnapshot("v2.test", List.of(current, flood, cheat));
        List<BehavioralHistoryEntry> history = List.of(
                confirmed("spam", "chat.spam", 3),
                confirmed("flood", "chat.flood", 2),
                confirmed("cheat", "cheat.client", 1)
        );

        HistoryAssessment result = new HistoryEvaluator().assess(current, NOW, history, snapshot);

        assertEquals(1.4, result.totalContribution(), 0.0000001);
        assertEquals(2, result.contributions().size());
    }

    @Test
    void exponentialDecayIsContinuousAndHalfLifeBased() {
        OffensePolicy offense = PolicyV2TestFixtures.offense(
                "chat.spam",
                Map.of("chat.spam", 1.0),
                DecayPolicy.exponential(Duration.ofDays(10), 0.0, 1.0)
        );
        PolicySnapshot snapshot = new PolicySnapshot("v2.test", List.of(offense));
        BehavioralHistoryEntry history = new BehavioralHistoryEntry(
                "case",
                NOW.minus(Duration.ofDays(10)),
                "chat.spam",
                "chat.spam",
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );

        double contribution = new HistoryEvaluator()
                .assess(offense, NOW, List.of(history), snapshot)
                .totalContribution();

        assertEquals(0.5, contribution, 0.0000001);
    }

    @Test
    void recurrenceLengthensLaterHistoryHalfLifeWithinConfiguredBound() {
        OffensePolicy adaptive = PolicyV2TestFixtures.offense(
                "chat.spam",
                Map.of("chat.spam", 1.0),
                DecayPolicy.exponential(Duration.ofDays(10), 0.5, 2.0)
        );
        PolicySnapshot snapshot = new PolicySnapshot("v2.test", List.of(adaptive));
        BehavioralHistoryEntry first = confirmed("first", "chat.spam", 20);
        BehavioralHistoryEntry repeated = confirmed("second", "chat.spam", 10);

        HistoryAssessment result = new HistoryEvaluator()
                .assess(adaptive, NOW, List.of(first, repeated), snapshot);
        HistoryAssessment.Contribution second = result.contributions().get(1);

        assertEquals(1, second.priorRelatedCount());
        assertEquals(1.5, second.halfLifeMultiplier(), 0.0000001);
        assertTrue(second.decayFactor() > 0.5);
    }

    @Test
    void nonDecayingHistorySurvivesLongCleanPeriodWhileDecayingHistoryApproachesZero() {
        OffensePolicy current = PolicyV2TestFixtures.offense(
                "current.offense",
                Map.of("chat.spam", 1.0, "access.vpn", 1.0),
                DecayPolicy.nonDecaying()
        );
        OffensePolicy spam = PolicyV2TestFixtures.offense(
                "chat.spam",
                Map.of("chat.spam", 1.0),
                DecayPolicy.exponential(Duration.ofDays(10), 0.0, 1.0)
        );
        OffensePolicy vpn = PolicyV2TestFixtures.offense(
                "access.vpn",
                Map.of("access.vpn", 1.0),
                DecayPolicy.nonDecaying()
        );
        PolicySnapshot snapshot = new PolicySnapshot("v2.test", List.of(current, spam, vpn));
        List<BehavioralHistoryEntry> history = List.of(
                confirmed("old-spam", "chat.spam", 365),
                confirmed("old-vpn", "access.vpn", 365)
        );

        HistoryAssessment result = new HistoryEvaluator().assess(current, NOW, history, snapshot);

        assertTrue(result.contributions().get(0).contribution() < 0.000000001);
        assertEquals(1.0, result.contributions().get(1).contribution(), 0.0);
    }

    @Test
    void overturnedHistoryIsRemovedAndReclassifiedHistoryUsesNewFinding() {
        OffensePolicy current = PolicyV2TestFixtures.offense(
                "chat.spam",
                Map.of("chat.spam", 1.0, "chat.flood", 0.5),
                DecayPolicy.nonDecaying()
        );
        OffensePolicy flood = PolicyV2TestFixtures.offense(
                "chat.flood",
                Map.of("chat.flood", 1.0),
                DecayPolicy.nonDecaying()
        );
        PolicySnapshot snapshot = new PolicySnapshot("v2.test", List.of(current, flood));
        List<BehavioralHistoryEntry> history = List.of(
                new BehavioralHistoryEntry(
                        "overturned",
                        NOW.minusSeconds(10),
                        "chat.spam",
                        null,
                        BehavioralHistoryEntry.FindingState.OVERTURNED
                ),
                new BehavioralHistoryEntry(
                        "reclassified",
                        NOW.minusSeconds(5),
                        "other.old",
                        "chat.flood",
                        BehavioralHistoryEntry.FindingState.RECLASSIFIED
                )
        );

        HistoryAssessment result = new HistoryEvaluator().assess(current, NOW, history, snapshot);

        assertEquals(0.5, result.totalContribution(), 0.0);
        assertEquals("chat.flood", result.contributions().getFirst().offenseId());
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
