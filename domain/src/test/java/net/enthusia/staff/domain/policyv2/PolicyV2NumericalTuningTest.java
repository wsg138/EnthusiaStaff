package net.enthusia.staff.domain.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2NumericalTuningTest {
    private static final Instant NOW = Instant.parse("2026-10-07T20:00:00Z");
    private static final double REPEAT_SCALE = 0.5;
    private static final double RELATED_THRESHOLD = 0.5;
    private static final double PATTERN_THRESHOLD = 1.5;
    private static final double CHRONIC_THRESHOLD = 2.25;
    private static final double REVIEW_THRESHOLD = 3.25;

    @Test
    void approvedDecayClassesProduceExpectedCleanPeriodBands() {
        assertTier(decay(30, 120, 2.0), List.of(7L), "related");
        assertTier(decay(30, 120, 2.0), List.of(30L), "related");
        assertTier(decay(30, 120, 2.0), List.of(90L), "baseline");
        assertTier(decay(30, 120, 2.0), List.of(21L, 14L, 7L), "chronic");
        assertTier(decay(30, 120, 2.0), List.of(201L, 194L, 187L), "baseline");

        assertTier(decay(90, 270, 2.5), List.of(90L), "related");
        assertTier(decay(90, 270, 2.5), List.of(180L), "baseline");
        assertTier(decay(90, 270, 2.5), List.of(21L, 14L, 7L), "chronic");
        assertTier(decay(90, 270, 2.5), List.of(201L, 194L, 187L), "related");
        assertTier(decay(90, 270, 2.5), List.of(386L, 379L, 372L), "baseline");

        assertTier(decay(180, 540, 3.0), List.of(180L), "related");
        assertTier(decay(180, 540, 3.0), List.of(365L), "baseline");
        assertTier(decay(180, 540, 3.0), List.of(21L, 14L, 7L), "chronic");
        assertTier(decay(180, 540, 3.0), List.of(386L, 379L, 372L), "related");
        assertTier(decay(180, 540, 3.0), List.of(180L, 90L, 7L), "chronic");
    }

    @Test
    void strongRelatedConductMattersWhileModerateRelationshipDoesNotOverEscalateSingleCase() {
        DecayPolicy decay = decay(180, 540, 3.0);
        String current = "simulation.current";
        String strong = "simulation.strong";
        String moderate = "simulation.moderate";
        OffensePolicy currentPolicy = tieredOffense(
                current,
                Map.of(current, 1.0, strong, 0.75, moderate, 0.35),
                decay
        );
        OffensePolicy strongPolicy = tieredOffense(strong, Map.of(strong, 1.0), decay);
        OffensePolicy moderatePolicy = tieredOffense(moderate, Map.of(moderate, 1.0), decay);
        PolicySnapshot snapshot = new PolicySnapshot(
                "simulation.relationships",
                List.of(currentPolicy, strongPolicy, moderatePolicy)
        );

        PolicyResolution strongResult = resolve(
                snapshot,
                current,
                List.of(history("strong", strong, 30))
        );
        PolicyResolution moderateResult = resolve(
                snapshot,
                current,
                List.of(history("moderate", moderate, 30))
        );

        assertEquals("related", strongResult.matchedRuleId());
        assertEquals("baseline", moderateResult.matchedRuleId());
        assertTrue(strongResult.history().totalContribution() > RELATED_THRESHOLD);
        assertTrue(moderateResult.history().totalContribution() < RELATED_THRESHOLD);
    }

    @Test
    void unrelatedOverturnedAndAncientFadedHistoryCannotCreateHiddenEscalation() {
        DecayPolicy decay = decay(90, 270, 2.5);
        String current = "simulation.current";
        String unrelated = "simulation.unrelated";
        OffensePolicy currentPolicy = tieredOffense(current, Map.of(current, 1.0), decay);
        OffensePolicy unrelatedPolicy = tieredOffense(unrelated, Map.of(unrelated, 1.0), decay);
        PolicySnapshot snapshot = new PolicySnapshot(
                "simulation.no-hidden-escalation",
                List.of(currentPolicy, unrelatedPolicy)
        );

        PolicyResolution unrelatedResult = resolve(
                snapshot,
                current,
                List.of(history("unrelated", unrelated, 7))
        );
        PolicyResolution overturnedResult = resolve(
                snapshot,
                current,
                List.of(new BehavioralHistoryEntry(
                        "overturned",
                        NOW.minus(Duration.ofDays(7)),
                        current,
                        null,
                        BehavioralHistoryEntry.FindingState.OVERTURNED
                ))
        );
        PolicyResolution ancientPatternResult = resolve(
                snapshot,
                current,
                sameHistory(current, 386L, 379L, 372L)
        );

        assertEquals("baseline", unrelatedResult.matchedRuleId());
        assertEquals("baseline", overturnedResult.matchedRuleId());
        assertEquals("baseline", ancientPatternResult.matchedRuleId());
        assertEquals(0.0, unrelatedResult.history().totalContribution(), 0.0);
        assertEquals(0.0, overturnedResult.history().totalContribution(), 0.0);
        assertTrue(ancientPatternResult.history().totalContribution() < RELATED_THRESHOLD);
    }

    @Test
    void nonDecayingHistoryRemainsRelevantWithoutUsingAdaptivePatternDecay() {
        String current = "simulation.nondecay";
        OffensePolicy offense = tieredOffense(
                current,
                Map.of(current, 1.0),
                DecayPolicy.nonDecaying()
        );
        PolicySnapshot snapshot = new PolicySnapshot("simulation.nondecay", List.of(offense));

        PolicyResolution result = resolve(
                snapshot,
                current,
                List.of(history("old", current, 3_650))
        );

        assertEquals("related", result.matchedRuleId());
        assertEquals(1.0, result.history().totalContribution(), 0.0);
        assertEquals(1.0, result.history().contributions().getFirst().halfLifeMultiplier(), 0.0);
        assertEquals(0.0, result.history().contributions().getFirst().patternPersistence(), 0.0);
    }

    @Test
    void mappedPolicyV1HistoryBehavesLikeEquivalentNativeBehavioralHistory() {
        String current = "simulation.current";
        OffensePolicy offense = tieredOffense(
                current,
                Map.of(current, 1.0),
                decay(180, 540, 3.0)
        );
        PolicySnapshot snapshot = new PolicySnapshot("simulation.v1-compat", List.of(offense));
        BehavioralHistoryEntry mappedV1 = new BehavioralHistoryEntry(
                "v1:LEGACY000000001",
                NOW.minus(Duration.ofDays(30)),
                current,
                current,
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );
        BehavioralHistoryEntry nativeV2 = new BehavioralHistoryEntry(
                "V2CASE000000001",
                NOW.minus(Duration.ofDays(30)),
                current,
                current,
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );

        PolicyResolution v1Result = resolve(snapshot, current, List.of(mappedV1));
        PolicyResolution v2Result = resolve(snapshot, current, List.of(nativeV2));

        assertEquals(v2Result.matchedRuleId(), v1Result.matchedRuleId());
        assertEquals(v2Result.history().totalContribution(), v1Result.history().totalContribution(), 0.0);
    }

    @Test
    void xrayAndFreecamCandidateStartAtTwentyOneDaysAndNeverAutoPermanentFromHistory() {
        String xray = "cheating.xray-esp";
        String freecam = "cheating.freecam";
        DecayPolicy cheatingDecay = decay(180, 540, 3.0);
        PolicySnapshot snapshot = new PolicySnapshot(
                "simulation.cheating.1",
                List.of(
                        cheatingOffense(xray, freecam, cheatingDecay),
                        cheatingOffense(freecam, xray, cheatingDecay)
                )
        );

        assertBanDays(resolve(snapshot, xray, List.of()), 21);
        assertBanDays(resolve(snapshot, freecam, List.of()), 21);
        assertBanDays(resolve(snapshot, xray, List.of(history("prior-xray", xray, 180))), 30);
        assertBanDays(resolve(snapshot, xray, List.of(
                history("x1", xray, 60),
                history("f1", freecam, 15)
        )), 60);
        assertBanDays(resolve(snapshot, xray, sameHistory(xray, 21L, 14L, 7L)), 90);

        List<BehavioralHistoryEntry> extremeHistory = sameHistory(
                xray,
                28L, 24L, 20L, 16L, 12L, 8L, 4L
        );
        PolicyResolution extreme = resolve(snapshot, xray, extremeHistory);
        assertTrue(extreme.requiresReview());
        assertEquals("review", extreme.matchedRuleId());

        for (OffensePolicy offense : snapshot.offenses()) {
            boolean permanent = offense.rules().stream()
                    .map(ResolutionRule::action)
                    .filter(PolicyAction.Exact.class::isInstance)
                    .map(PolicyAction.Exact.class::cast)
                    .flatMap(action -> action.sanctions().stream())
                    .anyMatch(sanction -> sanction.length().isPermanent());
            assertFalse(permanent);
        }
    }

    @Test
    void policyVersionChangeDoesNotRewriteSameHistoricalFacts() {
        String current = "simulation.current";
        OffensePolicy offense = tieredOffense(
                current,
                Map.of(current, 1.0),
                decay(90, 270, 2.5)
        );
        List<BehavioralHistoryEntry> history = sameHistory(current, 30L, 7L);
        PolicyResolution first = resolve(
                new PolicySnapshot("simulation.version.1", List.of(offense)),
                current,
                history
        );
        PolicyResolution second = resolve(
                new PolicySnapshot("simulation.version.2", List.of(offense)),
                current,
                history
        );

        assertEquals(first.matchedRuleId(), second.matchedRuleId());
        assertEquals(first.history(), second.history());
        assertEquals("simulation.version.1", first.policyVersion());
        assertEquals("simulation.version.2", second.policyVersion());
    }

    private static void assertTier(
            DecayPolicy decay,
            List<Long> ages,
            String expectedRule
    ) {
        String offenseId = "simulation.offense";
        OffensePolicy offense = tieredOffense(offenseId, Map.of(offenseId, 1.0), decay);
        PolicySnapshot snapshot = new PolicySnapshot("simulation.tiers", List.of(offense));
        assertEquals(expectedRule, resolve(snapshot, offenseId, sameHistory(offenseId, ages)).matchedRuleId());
    }

    private static PolicyResolution resolve(
            PolicySnapshot snapshot,
            String offenseId,
            List<BehavioralHistoryEntry> history
    ) {
        return new PolicyResolver().resolve(
                snapshot,
                new IncidentFinding(offenseId, Map.of()),
                NOW,
                history
        );
    }

    private static OffensePolicy tieredOffense(
            String id,
            Map<String, Double> relationships,
            DecayPolicy decay
    ) {
        return new OffensePolicy(
                id,
                id,
                "simulation",
                List.of(),
                new HistoryPolicy(relationships, decay),
                tierRules(false)
        );
    }

    private static OffensePolicy cheatingOffense(
            String id,
            String relatedId,
            DecayPolicy decay
    ) {
        return new OffensePolicy(
                id,
                id,
                "cheating",
                List.of(),
                new HistoryPolicy(Map.of(id, 1.0, relatedId, 0.75), decay),
                tierRules(true)
        );
    }

    private static List<ResolutionRule> tierRules(boolean cheating) {
        List<ResolutionRule> rules = new ArrayList<>();
        rules.add(rule("baseline", 0.0, RELATED_THRESHOLD, cheating ? ban(21) : warning()));
        rules.add(rule("related", RELATED_THRESHOLD, PATTERN_THRESHOLD, cheating ? ban(30) : warning()));
        rules.add(rule("pattern", PATTERN_THRESHOLD, CHRONIC_THRESHOLD, cheating ? ban(60) : warning()));
        rules.add(rule("chronic", CHRONIC_THRESHOLD, REVIEW_THRESHOLD, cheating ? ban(90) : warning()));
        rules.add(new ResolutionRule(
                "review",
                new RuleCondition(Map.of(), HistoryWindow.atLeast(REVIEW_THRESHOLD)),
                new PolicyAction.RequiresReview("policy-review.chronic-pattern"),
                List.of()
        ));
        return List.copyOf(rules);
    }

    private static ResolutionRule rule(
            String id,
            double minimum,
            double maximum,
            PolicyAction action
    ) {
        return new ResolutionRule(
                id,
                new RuleCondition(Map.of(), new HistoryWindow(minimum, maximum)),
                action,
                List.of()
        );
    }

    private static PolicyAction warning() {
        return new PolicyAction.Exact(List.of(
                new SanctionSpec(SanctionType.WARNING, SanctionLength.instant())
        ));
    }

    private static PolicyAction ban(long days) {
        return new PolicyAction.Exact(List.of(
                new SanctionSpec(
                        SanctionType.NETWORK_BAN,
                        SanctionLength.temporary(Duration.ofDays(days))
                )
        ));
    }

    private static void assertBanDays(PolicyResolution resolution, long days) {
        PolicyAction.Exact exact = assertInstanceOf(PolicyAction.Exact.class, resolution.action());
        SanctionSpec sanction = exact.sanctions().getFirst();
        assertEquals(SanctionType.NETWORK_BAN, sanction.type());
        assertEquals(Duration.ofDays(days), sanction.length().temporary().orElseThrow());
    }

    private static DecayPolicy decay(long directDays, long patternDays, double cap) {
        return DecayPolicy.exponential(
                Duration.ofDays(directDays),
                Duration.ofDays(patternDays),
                REPEAT_SCALE,
                cap
        );
    }

    private static List<BehavioralHistoryEntry> sameHistory(String offenseId, Long... ages) {
        return sameHistory(offenseId, List.of(ages));
    }

    private static List<BehavioralHistoryEntry> sameHistory(String offenseId, List<Long> ages) {
        List<BehavioralHistoryEntry> history = new ArrayList<>();
        for (int index = 0; index < ages.size(); index++) {
            history.add(history("case-" + index, offenseId, ages.get(index)));
        }
        return List.copyOf(history);
    }

    private static BehavioralHistoryEntry history(
            String caseId,
            String offenseId,
            long ageDays
    ) {
        return new BehavioralHistoryEntry(
                caseId,
                NOW.minus(Duration.ofDays(ageDays)),
                offenseId,
                offenseId,
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );
    }
}
