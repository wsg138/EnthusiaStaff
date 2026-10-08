package net.enthusia.staff.paper.config.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicyResolver;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

/** Future, inactive candidate: protects allowed in-game leverage from terminal classification. */
class PolicyV2OwnerV3BlackmailScopeTest {
    private static final String FIRST = "owner.2026-10-07.1";
    private static final String SECOND = "owner.2026-10-07.2";
    private static final String THIRD = "owner.2026-10-07.3";
    private static final String OFFENSE = "safety.blackmail-extortion";
    private static final Instant INCIDENT = Instant.parse("2026-10-08T12:00:00Z");

    @Test
    void candidateIsRetainedButNotSelectedOrAuthoritative() {
        var config = load();
        assertEquals(PolicyV2FeatureMode.DISABLED, config.mode());
        assertEquals(SECOND, config.activeVersion());
        assertEquals(3, config.snapshots().size());
        assertEquals(FIRST, config.snapshots().get(FIRST).version());
        assertEquals(SECOND, config.snapshots().get(SECOND).version());
        PolicySnapshot candidate = config.snapshots().get(THIRD);
        assertEquals(THIRD, candidate.version());
        assertEquals(85, candidate.offenses().size());
        assertTrue(config.snapshots().get(FIRST).offense(OFFENSE).orElseThrow().attributes().isEmpty());
        assertTrue(config.snapshots().get(SECOND).offense(OFFENSE).orElseThrow().attributes().isEmpty());
    }

    @Test
    void ordinaryGameOnlyBlackmailNeverMatchesTerminalRule() {
        assertReview(finding("game-only", false), "policy-gap.no-match");
        assertReview(finding("game-only", true), "policy-gap.no-match");
    }

    @Test
    void realWorldClaimsWithoutVerificationAndUncertainContextFailClosed() {
        assertReview(finding("real-world", false), "policy-gap.no-match");
        assertReview(finding("uncertain", true), "policy-gap.no-match");
        assertReview(finding("uncertain", false), "policy-gap.no-match");
        assertReview(new IncidentFinding(OFFENSE, Map.of()), "policy-gap.invalid-attributes");
    }

    @Test
    void onlyVerifiedRealWorldCoercionReachesAdminApprovedPermanentBan() {
        var resolution = resolve(finding("real-world", true));
        var action = assertInstanceOf(PolicyAction.ExactWithApproval.class, resolution.action());
        assertEquals("terminal", resolution.matchedRuleId());
        assertEquals(StaffRank.ADMIN, action.minimumRank());
        assertEquals(1, action.sanctions().size());
        assertEquals(SanctionType.NETWORK_BAN, action.sanctions().getFirst().type());
        assertTrue(action.sanctions().getFirst().length().isPermanent());
        assertEquals(2, resolution.remedies().size());
    }

    private static IncidentFinding finding(String context, boolean verified) {
        return new IncidentFinding(OFFENSE, Map.of(
                "coercion-context", new IncidentAttributeValue.EnumValue(context),
                "real-world-leverage-verified", new IncidentAttributeValue.BooleanValue(verified)
        ));
    }

    private static void assertReview(IncidentFinding finding, String code) {
        var result = resolve(finding);
        assertTrue(result.requiresReview());
        assertEquals(code,
                assertInstanceOf(PolicyAction.RequiresReview.class, result.action()).reasonCode());
        assertTrue(result.remedies().isEmpty());
    }

    private static net.enthusia.staff.domain.policyv2.PolicyResolution resolve(IncidentFinding finding) {
        return new PolicyResolver().resolve(load().snapshots().get(THIRD), finding, INCIDENT, List.of());
    }

    private static PolicyV2Configuration load() {
        return new PolicyV2ConfigurationLoader().load(
                PolicyV2OwnerV3BlackmailScopeTest.class.getResourceAsStream("/policy-v2.yml"),
                "policy-v2.yml"
        );
    }
}
