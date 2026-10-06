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

    @Test
    void sameInputsProduceEqualResolution() {
        PolicySnapshot snapshot = new PolicySnapshot("v2.test.1", List.of(
                PolicyV2TestFixtures.offense(
                        "chat.spam",
                        Map.of("chat.spam", 1.0),
                        PolicyV2TestFixtures.tenDayDecay()
                )
        ));
        IncidentFinding finding = new IncidentFinding("chat.spam", Map.of());
        List<BehavioralHistoryEntry> history = List.of(confirmed("case-1", "chat.spam"));
        PolicyResolver resolver = new PolicyResolver();

        assertEquals(
                resolver.resolve(snapshot, finding, INCIDENT_AT, history),
                resolver.resolve(snapshot, finding, INCIDENT_AT, history)
        );
    }

    @Test
    void unknownAndUncoveredFindingsFailClosedToReview() {
        OffensePolicy known = new OffensePolicy(
                "chat.spam",
                "Spam",
                "chat.spam",
                List.of(),
                new HistoryPolicy(Map.of("chat.spam", 1.0), DecayPolicy.nonDecaying()),
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
                new IncidentFinding("chat.spam", Map.of()),
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
    void malformedOffenseSpecificAttributesRequireReview() {
        IncidentAttributeDefinition severity = IncidentAttributeDefinition.enumValue(
                "severity",
                true,
                Set.of("low", "high")
        );
        OffensePolicy offense = new OffensePolicy(
                "chat.spam",
                "Spam",
                "chat.spam",
                List.of(severity),
                new HistoryPolicy(Map.of("chat.spam", 1.0), DecayPolicy.nonDecaying()),
                List.of(PolicyV2TestFixtures.catchAll("all", PolicyV2TestFixtures.exactWarning()))
        );
        PolicySnapshot snapshot = new PolicySnapshot("v2.test.1", List.of(offense));

        PolicyResolution result = new PolicyResolver().resolve(
                snapshot,
                new IncidentFinding("chat.spam", Map.of()),
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
