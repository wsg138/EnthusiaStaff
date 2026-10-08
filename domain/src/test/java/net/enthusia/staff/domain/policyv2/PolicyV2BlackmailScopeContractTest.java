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

/**
 * A contract fixture for the NEXT owner snapshot, not a replacement for an
 * immutable published owner snapshot. Related cutover blocker: #468.
 */
class PolicyV2BlackmailScopeContractTest {
    private static final String OFFENSE = "safety.blackmail-extortion";
    private static final Instant INCIDENT_AT = Instant.parse("2026-10-08T12:00:00Z");
    private static final String CONTEXT = "coercion-context";
    private static final String VERIFIED = "real-world-leverage-verified";
    private static final String REAL_WORLD = "real-world";
    private static final String UNCERTAIN = "uncertain";
    private static final String NO_MATCH = "policy-gap.no-match";

    @Test
    void realWorldCoercionRequiresExplicitVerifiedFindingAndAdminApproval() {
        PolicyResolution outcome = resolve(REAL_WORLD, true);
        PolicyAction.ExactWithApproval punishment =
                assertInstanceOf(PolicyAction.ExactWithApproval.class, outcome.action());
        assertEquals(StaffRank.ADMIN, punishment.minimumRank());
        assertEquals(List.of(new SanctionSpec(
                SanctionType.NETWORK_BAN, SanctionLength.permanent()
        )), punishment.sanctions());
        assertEquals("verified-real-world", outcome.matchedRuleId());
    }

    @Test
    void minecraftOnlyItemAndBaseLeverageCannotReachTerminalPunishment() {
        for (boolean verified : List.of(false, true)) {
            // Even accidentally checking a verification box is insufficient:
            // context must independently identify real-world coercion.
            assertReview(resolve("game-only", verified), NO_MATCH);
        }
    }

    @Test
    void uncertainAndUnverifiedRealWorldClaimsCannotReachTerminalPunishment() {
        assertReview(resolve(UNCERTAIN, false), NO_MATCH);
        assertReview(resolve(UNCERTAIN, true), NO_MATCH);
        assertReview(resolve(REAL_WORLD, false), NO_MATCH);
    }

    @Test
    void absentOrMalformedEvidenceFailsClosedBeforePunishment() {
        PolicySnapshot snapshot = gatedSnapshot();
        PolicyResolver resolver = new PolicyResolver();
        for (Map<String, IncidentAttributeValue> attributes : List.of(
                Map.<String, IncidentAttributeValue>of(),
                Map.<String, IncidentAttributeValue>of(CONTEXT,
                        new IncidentAttributeValue.EnumValue(REAL_WORLD)),
                Map.<String, IncidentAttributeValue>of(VERIFIED,
                        new IncidentAttributeValue.BooleanValue(true))
        )) {
            var outcome = resolver.resolve(
                    snapshot, new IncidentFinding(OFFENSE, attributes), INCIDENT_AT, List.of());
            assertReview(outcome, "policy-gap.invalid-attributes");
        }
    }

    private static PolicyResolution resolve(String context, boolean verified) {
        return new PolicyResolver().resolve(
                gatedSnapshot(),
                new IncidentFinding(OFFENSE, Map.of(
                        CONTEXT, new IncidentAttributeValue.EnumValue(context),
                        VERIFIED, new IncidentAttributeValue.BooleanValue(verified)
                )),
                INCIDENT_AT,
                List.of()
        );
    }

    private static PolicySnapshot gatedSnapshot() {
        OffensePolicy offense = new OffensePolicy(
                OFFENSE,
                "Real-world blackmail / extortion",
                "safety-threats-privacy",
                List.of(
                        IncidentAttributeDefinition.enumValue(CONTEXT, true,
                                Set.of("game-only", REAL_WORLD, UNCERTAIN)),
                        IncidentAttributeDefinition.booleanValue(VERIFIED, true)
                ),
                new HistoryPolicy(Map.of(OFFENSE, 1.0), DecayPolicy.nonDecaying()),
                List.of(new ResolutionRule(
                        "verified-real-world",
                        new RuleCondition(Map.of(
                                CONTEXT, Set.of(new IncidentAttributeValue.EnumValue(REAL_WORLD)),
                                VERIFIED, Set.of(new IncidentAttributeValue.BooleanValue(true))
                        ), HistoryWindow.atLeast(0.0)),
                        new PolicyAction.ExactWithApproval(
                                List.of(new SanctionSpec(SanctionType.NETWORK_BAN,
                                        SanctionLength.permanent())),
                                StaffRank.ADMIN
                        ),
                        List.of()
                ))
        );
        return new PolicySnapshot("v2.proposed.irl-blackmail-scope", List.of(offense));
    }

    private static void assertReview(PolicyResolution outcome, String code) {
        assertTrue(outcome.requiresReview());
        assertEquals(code, assertInstanceOf(PolicyAction.RequiresReview.class,
                outcome.action()).reasonCode());
        assertTrue(outcome.remedies().isEmpty());
    }
}
