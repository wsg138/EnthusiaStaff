package net.enthusia.staff.domain.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2StaticSafetyAuditTest {
    private static final String BLACKMAIL = "safety.blackmail-extortion";
    private static final String LANGUAGE = "chat.language.non-english-public";
    private static final String CONTEXT = "coercion-context";
    private static final String VERIFIED = "real-world-leverage-verified";

    @Test
    void ungatedBlackmailAndUnsupportedRemediesAreReportedWithoutChangingResolution() {
        RemedySpec unknown = new RemedySpec("market-cleanup", RemedySpec.Type.OTHER, "Manual cleanup");
        RemedySpec unbound = new RemedySpec("remove-content", RemedySpec.Type.REMOVE_CONTENT, "Remove");
        OffensePolicy offense = offense(BLACKMAIL, List.of(),
                List.of(new ResolutionRule("terminal",
                        new RuleCondition(Map.of(), HistoryWindow.atLeast(0)),
                        approvedBan(), List.of(unknown, unbound))));
        var report = audit(offense);
        assertFalse(report.passesStaticChecks());
        assertEquals(List.of(
                "blackmail.missing-real-world-evidence-fields",
                "remedy.unsupported-other",
                "remedy.missing-enforcement-binding",
                "blackmail.ungated-punitive-rule"
        ), report.findings().stream().map(PolicyV2StaticSafetyAudit.Finding::code).toList());
        assertEquals("market-cleanup", report.findings().get(1).remedyId());
        assertEquals("owner.test", report.snapshotVersion());
    }

    @Test
    void correctContextAndVerifiedProofCannotBeBroadenedToGameOnly() {
        List<IncidentAttributeDefinition> required = requiredBlackmailFields();
        OffensePolicy approved = offense(BLACKMAIL, required,
                List.of(new ResolutionRule("verified-irl", scope(true),
                        approvedBan(), List.of())));
        assertTrue(audit(approved).passesStaticChecks());

        OffensePolicy unverified = offense(BLACKMAIL, required,
                List.of(new ResolutionRule("missing-proof", new RuleCondition(Map.of(
                        CONTEXT, Set.of(new IncidentAttributeValue.EnumValue("real-world"))
                ), HistoryWindow.atLeast(0)), approvedBan(), List.of())));
        assertEquals("blackmail.ungated-punitive-rule",
                audit(unverified).findings().getFirst().code());

        OffensePolicy broad = offense(BLACKMAIL, required,
                List.of(new ResolutionRule("ambiguous", new RuleCondition(Map.of(
                        CONTEXT, Set.of(new IncidentAttributeValue.EnumValue("real-world"),
                                new IncidentAttributeValue.EnumValue("game-only")),
                        VERIFIED, Set.of(new IncidentAttributeValue.BooleanValue(true))
                ), HistoryWindow.atLeast(0)), approvedBan(), List.of())));
        assertEquals("blackmail.ungated-punitive-rule",
                audit(broad).findings().getFirst().code());
    }

    @Test
    void optionalEvidenceFieldsAreNotSufficientToAuthorizeSevereSanction() {
        OffensePolicy offense = offense(BLACKMAIL, List.of(
                IncidentAttributeDefinition.enumValue(CONTEXT, false,
                        Set.of("game-only", "real-world", "uncertain")),
                IncidentAttributeDefinition.booleanValue(VERIFIED, false)
        ), List.of(new ResolutionRule("terminal", scope(true), approvedBan(), List.of())));
        assertEquals(List.of(
                "blackmail.missing-real-world-evidence-fields", "blackmail.ungated-punitive-rule"
        ), audit(offense).findings().stream().map(PolicyV2StaticSafetyAudit.Finding::code).toList());
    }

    @Test
    void nonEnglishChatCannotBecomeBanEvenThroughBoundedOptions() {
        OffensePolicy offense = offense(LANGUAGE, List.of(), List.of(
                new ResolutionRule("baseline",
                        new RuleCondition(Map.of(), HistoryWindow.atLeast(0)),
                        new PolicyAction.Bounded(List.of(
                                List.of(new SanctionSpec(SanctionType.MUTE,
                                        SanctionLength.permanent())),
                                List.of(new SanctionSpec(SanctionType.NETWORK_BAN,
                                        SanctionLength.temporary(java.time.Duration.ofDays(1))))
                        ), StaffRank.MOD),
                        List.of())));
        assertEquals("language.network-ban-prohibited", audit(offense).findings().getFirst().code());
    }

    @Test
    void remedyOnlyAndUnknownOffensesDoNotInventPunitiveFailures() {
        OffensePolicy unknown = offense("content.custom", List.of(), List.of(
                new ResolutionRule("review", new RuleCondition(Map.of(), HistoryWindow.atLeast(0)),
                        new PolicyAction.RequiresReview("policy-gap.unknown"), List.of())));
        assertTrue(audit(unknown).passesStaticChecks());
    }

    private static RuleCondition scope(boolean verified) {
        return new RuleCondition(Map.of(
                CONTEXT, Set.of(new IncidentAttributeValue.EnumValue("real-world")),
                VERIFIED, Set.of(new IncidentAttributeValue.BooleanValue(verified))
        ), HistoryWindow.atLeast(0));
    }

    private static List<IncidentAttributeDefinition> requiredBlackmailFields() {
        return List.of(
                IncidentAttributeDefinition.enumValue(CONTEXT, true,
                        Set.of("game-only", "real-world", "uncertain")),
                IncidentAttributeDefinition.booleanValue(VERIFIED, true)
        );
    }

    private static PolicyAction.ExactWithApproval approvedBan() {
        return new PolicyAction.ExactWithApproval(
                List.of(new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.permanent())),
                StaffRank.ADMIN);
    }

    private static OffensePolicy offense(String id, List<IncidentAttributeDefinition> fields,
            List<ResolutionRule> rules) {
        return new OffensePolicy(id, id, "test", fields,
                new HistoryPolicy(Map.of(), DecayPolicy.nonDecaying()), rules);
    }

    private static PolicyV2StaticSafetyAudit.Report audit(OffensePolicy offense) {
        return PolicyV2StaticSafetyAudit.inspect(new PolicySnapshot("owner.test", List.of(offense)));
    }
}
