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
    private static final String REAL_WORLD = "real-world";
    private static final String GAME_ONLY = "game-only";
    private static final String UNGATED = "blackmail.ungated-punitive-rule";

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
                UNGATED
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
                        CONTEXT, Set.of(new IncidentAttributeValue.EnumValue(REAL_WORLD))
                ), HistoryWindow.atLeast(0)), approvedBan(), List.of())));
        assertEquals(UNGATED,
                audit(unverified).findings().getFirst().code());

        OffensePolicy broad = offense(BLACKMAIL, required,
                List.of(new ResolutionRule("ambiguous", new RuleCondition(Map.of(
                        CONTEXT, Set.of(new IncidentAttributeValue.EnumValue(REAL_WORLD),
                                new IncidentAttributeValue.EnumValue(GAME_ONLY)),
                        VERIFIED, Set.of(new IncidentAttributeValue.BooleanValue(true))
                ), HistoryWindow.atLeast(0)), approvedBan(), List.of())));
        assertEquals(UNGATED,
                audit(broad).findings().getFirst().code());
    }

    @Test
    void realWorldPermanentNetworkBanAlwaysRequiresAdministratorApproval() {
        var permanentNetworkBan = new SanctionSpec(
                SanctionType.NETWORK_BAN, SanctionLength.permanent());
        var shortMute = new SanctionSpec(SanctionType.MUTE,
                SanctionLength.temporary(java.time.Duration.ofDays(1)));
        var scope = scope(true);
        var unsafeExact = offense(BLACKMAIL, requiredBlackmailFields(),
                List.of(new ResolutionRule("exact", scope,
                        new PolicyAction.Exact(List.of(permanentNetworkBan)), List.of())));
        var modApproved = offense(BLACKMAIL, requiredBlackmailFields(),
                List.of(new ResolutionRule("mod", scope,
                        new PolicyAction.ExactWithApproval(
                                List.of(permanentNetworkBan), StaffRank.MOD), List.of())));
        var modBounded = offense(BLACKMAIL, requiredBlackmailFields(),
                List.of(new ResolutionRule("bounded", scope,
                        new PolicyAction.Bounded(
                                List.of(List.of(shortMute), List.of(permanentNetworkBan)),
                                StaffRank.MOD), List.of())));

        for (OffensePolicy unsafe : List.of(unsafeExact, modApproved, modBounded)) {
            assertEquals(List.of("blackmail.terminal-requires-admin-approval"),
                    audit(unsafe).findings().stream()
                            .map(PolicyV2StaticSafetyAudit.Finding::code).toList());
        }

        var adminBounded = offense(BLACKMAIL, requiredBlackmailFields(),
                List.of(new ResolutionRule("bounded", scope,
                        new PolicyAction.Bounded(
                                List.of(List.of(shortMute), List.of(permanentNetworkBan)),
                                StaffRank.ADMIN), List.of())));
        var founderApproved = offense(BLACKMAIL, requiredBlackmailFields(),
                List.of(new ResolutionRule("founder", scope,
                        new PolicyAction.ExactWithApproval(
                                List.of(permanentNetworkBan), StaffRank.FOUNDER), List.of())));
        assertTrue(audit(adminBounded).passesStaticChecks());
        assertTrue(audit(founderApproved).passesStaticChecks());
        assertTrue(audit(offense(BLACKMAIL, requiredBlackmailFields(),
                List.of(new ResolutionRule("mute-only", scope,
                        new PolicyAction.Exact(List.of(shortMute)), List.of())))).passesStaticChecks());
    }

    @Test
    void optionalEvidenceFieldsAreNotSufficientToAuthorizeSevereSanction() {
        OffensePolicy offense = offense(BLACKMAIL, List.of(
                IncidentAttributeDefinition.enumValue(CONTEXT, false,
                        Set.of(GAME_ONLY, REAL_WORLD, "uncertain")),
                IncidentAttributeDefinition.booleanValue(VERIFIED, false)
        ), List.of(new ResolutionRule("terminal", scope(true), approvedBan(), List.of())));
        assertEquals(List.of(
                "blackmail.missing-real-world-evidence-fields", UNGATED
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
    void languageViolationStaysWithinSevenDayChatOnlyLimit() {
        for (SanctionSpec safe : List.of(
                new SanctionSpec(SanctionType.WARNING, SanctionLength.instant()),
                new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(
                        java.time.Duration.ofDays(7))),
                new SanctionSpec(SanctionType.PUBLIC_MUTE, SanctionLength.temporary(
                        java.time.Duration.ofDays(7)))
        )) {
            assertTrue(audit(languageOffense(safe)).passesStaticChecks());
        }

        for (SanctionSpec unsafe : List.of(
                new SanctionSpec(SanctionType.MUTE, SanctionLength.permanent()),
                new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(
                        java.time.Duration.ofDays(8))),
                new SanctionSpec(SanctionType.PUBLIC_MUTE, SanctionLength.temporary(
                        java.time.Duration.ofDays(8))),
                new SanctionSpec(SanctionType.KICK, SanctionLength.instant())
        )) {
            assertEquals("language.invalid-chat-only-sanction",
                    audit(languageOffense(unsafe)).findings().getFirst().code());
        }
    }

    private static OffensePolicy languageOffense(SanctionSpec sanction) {
        return offense(LANGUAGE, List.of(), List.of(new ResolutionRule(
                "single",
                new RuleCondition(Map.of(), HistoryWindow.atLeast(0)),
                new PolicyAction.Exact(List.of(sanction)),
                List.of()
        )));
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
                CONTEXT, Set.of(new IncidentAttributeValue.EnumValue(REAL_WORLD)),
                VERIFIED, Set.of(new IncidentAttributeValue.BooleanValue(verified))
        ), HistoryWindow.atLeast(0));
    }

    private static List<IncidentAttributeDefinition> requiredBlackmailFields() {
        return List.of(
                IncidentAttributeDefinition.enumValue(CONTEXT, true,
                        Set.of(GAME_ONLY, REAL_WORLD, "uncertain")),
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
