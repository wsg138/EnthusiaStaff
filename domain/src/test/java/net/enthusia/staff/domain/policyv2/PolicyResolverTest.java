package net.enthusia.staff.domain.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyResolverTest {
    private static final Instant INCIDENT_AT = Instant.parse("2026-10-06T20:00:00Z");
    private static final String SPAM = "chat.spam";

    @Test
    void sameInputsProduceEqualResolution() {
        PolicySnapshot snapshot = new PolicySnapshot("v2.test.1", List.of(
                PolicyV2TestFixtures.offense(
                        SPAM,
                        Map.of(SPAM, 1.0),
                        PolicyV2TestFixtures.tenDayDecay()
                )
        ));
        IncidentFinding finding = new IncidentFinding(SPAM, Map.of());
        List<BehavioralHistoryEntry> history = List.of(confirmed("case-1", SPAM));
        PolicyResolver resolver = new PolicyResolver();

        assertEquals(
                resolver.resolve(snapshot, finding, INCIDENT_AT, history),
                resolver.resolve(snapshot, finding, INCIDENT_AT, history)
        );
    }

    @Test
    void unknownAndUncoveredFindingsFailClosedToReview() {
        OffensePolicy known = new OffensePolicy(
                SPAM,
                "Spam",
                SPAM,
                List.of(),
                new HistoryPolicy(Map.of(SPAM, 1.0), DecayPolicy.nonDecaying()),
                List.of(new ResolutionRule(
                        "repeat-only",
                        new RuleCondition(Map.of(), HistoryWindow.atLeast(1.0)),
                        PolicyV2TestFixtures.exactWarning(),
                        List.of()
                ))
        );
        PolicySnapshot snapshot = new PolicySnapshot("v2.test.1", List.of(known));
        PolicyResolver resolver = new PolicyResolver();

        PolicyResolution unknown = resolver.resolve(
                snapshot,
                new IncidentFinding("exploit.unknown", Map.of()),
                INCIDENT_AT,
                List.of()
        );
        PolicyResolution uncovered = resolver.resolve(
                snapshot,
                new IncidentFinding(SPAM, Map.of()),
                INCIDENT_AT,
                List.of()
        );

        assertTrue(unknown.requiresReview());
        assertEquals(
                "policy-gap.unknown-offense",
                assertInstanceOf(PolicyAction.RequiresReview.class, unknown.action()).reasonCode()
        );
        assertEquals(
                "policy-gap.no-match",
                assertInstanceOf(PolicyAction.RequiresReview.class, uncovered.action()).reasonCode()
        );
    }

    @Test
    void versionAndBoundedAuthorityRemainExplicitInResult() {
        PolicyAction.Bounded bounded = new PolicyAction.Bounded(
                List.of(
                        List.of(PolicyV2TestFixtures.warning()),
                        List.of(new SanctionSpec(SanctionType.KICK, SanctionLength.instant()))
                ),
                StaffRank.MOD
        );
        OffensePolicy offense = new OffensePolicy(
                "chat.flood",
                "Chat Flood",
                "chat.spam",
                List.of(),
                new HistoryPolicy(Map.of("chat.flood", 1.0), DecayPolicy.nonDecaying()),
                List.of(PolicyV2TestFixtures.catchAll("bounded", bounded))
        );
        PolicySnapshot snapshot = new PolicySnapshot("v2.owner-review-7", List.of(offense));

        PolicyResolution resolution = new PolicyResolver().resolve(
                snapshot,
                new IncidentFinding("chat.flood", Map.of()),
                INCIDENT_AT,
                List.of()
        );

        assertEquals("v2.owner-review-7", resolution.policyVersion());
        assertEquals(bounded, resolution.action());
    }

    @Test
    void remedyOnlyResolutionIsDeterministicWithoutInventingPunitiveSanctions() {
        RemedySpec remedy = new RemedySpec(
                "vpn-compliance",
                RemedySpec.Type.ACCESS_RESTRICTION,
                "Disable the unapproved VPN or obtain approval."
        );
        OffensePolicy offense = new OffensePolicy(
                "access.vpn-compliance",
                "VPN compliance",
                "accounts-vpn-access",
                List.of(),
                new HistoryPolicy(Map.of(), DecayPolicy.nonDecaying()),
                List.of(new ResolutionRule(
                        "compliance-only",
                        new RuleCondition(Map.of(), HistoryWindow.atLeast(0.0)),
                        new PolicyAction.RemedyOnly(),
                        List.of(remedy)
                ))
        );
        PolicySnapshot snapshot = new PolicySnapshot("v2.remedy-only", List.of(offense));

        PolicyResolution resolution = new PolicyResolver().resolve(
                snapshot,
                new IncidentFinding("access.vpn-compliance", Map.of()),
                INCIDENT_AT,
                List.of()
        );

        assertInstanceOf(PolicyAction.RemedyOnly.class, resolution.action());
        assertEquals(List.of(remedy), resolution.remedies());
        assertTrue(!resolution.requiresReview());
        assertEquals(0.0, resolution.history().totalContribution(), 0.0);
    }

    @Test
    void malformedOffenseSpecificAttributesRequireReview() {
        IncidentAttributeDefinition severity = IncidentAttributeDefinition.enumValue(
                "severity",
                true,
                Set.of("low", "high")
        );
        OffensePolicy offense = new OffensePolicy(
                SPAM,
                "Spam",
                SPAM,
                List.of(severity),
                new HistoryPolicy(Map.of(SPAM, 1.0), DecayPolicy.nonDecaying()),
                List.of(PolicyV2TestFixtures.catchAll("all", PolicyV2TestFixtures.exactWarning()))
        );
        PolicySnapshot snapshot = new PolicySnapshot("v2.test.1", List.of(offense));

        PolicyResolution result = new PolicyResolver().resolve(
                snapshot,
                new IncidentFinding(SPAM, Map.of()),
                INCIDENT_AT,
                List.of()
        );

        assertTrue(result.requiresReview());
        assertEquals(
                "policy-gap.invalid-attributes",
                assertInstanceOf(PolicyAction.RequiresReview.class, result.action()).reasonCode()
        );
    }

    private static BehavioralHistoryEntry confirmed(String caseId, String offenseId) {
        return new BehavioralHistoryEntry(
                caseId,
                INCIDENT_AT.minusSeconds(60),
                offenseId,
                offenseId,
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );
    }
}
