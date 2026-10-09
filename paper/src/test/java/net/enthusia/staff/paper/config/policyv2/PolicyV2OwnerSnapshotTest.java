package net.enthusia.staff.paper.config.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicyResolver;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyBindingResolver;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement;
import net.enthusia.staff.domain.policyv2.legacy.PolicyV1HistoryCarryForward;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2OwnerSnapshotTest {
    private static final Instant NOW = Instant.parse("2026-10-07T22:00:00Z");
    private static final String OWNER_VERSION = "owner.2026-10-07.2";
    private static final String RULE_SEPARATOR = " / ";
    private static final List<String> HISTORY_TIERS = List.of("related", "pattern", "chronic", "heavy");

    @Test
    void bundledOwnerSnapshotIsCompleteButRuntimeRemainsDisabled() {
        PolicyV2Configuration configuration = load();
        PolicySnapshot snapshot = configuration.activeSnapshot();

        assertEquals(PolicyV2FeatureMode.DISABLED, configuration.mode());
        assertEquals(OWNER_VERSION, configuration.activeVersion());
        assertEquals(OWNER_VERSION, snapshot.version());
        assertEquals(4, configuration.snapshots().size());
        assertTrue(configuration.snapshots().containsKey("owner.2026-10-07.1"));
        assertEquals(85, snapshot.offenses().size());
        assertTrue(snapshot.offenses().stream().noneMatch(
                offense -> offense.id().contains("grief") || offense.displayName().toLowerCase().contains("grief")
        ));
    }

    @Test
    void everyMappedPolicyV1HistoryTargetExistsInOwnerSnapshot() {
        PolicySnapshot snapshot = load().activeSnapshot();
        PolicyV1HistoryCarryForward carryForward = new PolicyV1HistoryCarryForward();

        carryForward.decisions().values().stream()
                .flatMap(decision -> decision.v2OffenseId().stream())
                .forEach(target -> assertTrue(
                        snapshot.offense(target).isPresent(),
                        () -> "missing mapped Policy v1 history target: " + target
                ));
    }

    @Test
    void xrayAndFreecamStartAtTwentyOneDaysAndHeavyHistoryDoesNotAutoPermanentBan() {
        PolicySnapshot snapshot = load().activeSnapshot();
        PolicyResolver resolver = new PolicyResolver();

        for (String offenseId : List.of("cheating.xray-esp", "cheating.freecam")) {
            PolicyAction.Exact baseline = assertInstanceOf(
                    PolicyAction.Exact.class,
                    resolver.resolve(
                            snapshot,
                            new IncidentFinding(offenseId, java.util.Map.of()),
                            NOW,
                            List.of()
                    ).action()
            );
            SanctionSpec sanction = baseline.sanctions().getFirst();
            assertEquals(SanctionType.NETWORK_BAN, sanction.type());
            assertEquals(Duration.ofDays(21), sanction.length().temporary().orElseThrow());

            OffensePolicy offense = snapshot.offense(offenseId).orElseThrow();
            assertFalse(offense.rules().stream()
                    .flatMap(rule -> punitiveSanctions(rule.action()).stream())
                    .anyMatch(spec -> spec.type().isBan() && spec.length().isPermanent()));

            List<BehavioralHistoryEntry> heavy = new ArrayList<>();
            for (int day = 1; day <= 6; day++) {
                heavy.add(new BehavioralHistoryEntry(
                        "heavy-" + day,
                        NOW.minus(Duration.ofDays(day)),
                        offenseId,
                        offenseId,
                        BehavioralHistoryEntry.FindingState.CONFIRMED
                ));
            }
            assertTrue(resolver.resolve(
                    snapshot,
                    new IncidentFinding(offenseId, java.util.Map.of()),
                    NOW,
                    heavy
            ).requiresReview());
        }
    }

    @Test
    void pureNonEnglishChatCanNeverDirectlyBanAndCapsAtSevenDayMute() {
        OffensePolicy offense = load().activeSnapshot()
                .offense("chat.language.non-english-public")
                .orElseThrow();

        for (var rule : offense.rules()) {
            for (SanctionSpec sanction : punitiveSanctions(rule.action())) {
                assertFalse(sanction.type().isBan());
                if (sanction.type() == SanctionType.MUTE) {
                    assertTrue(sanction.length().temporary().orElseThrow().compareTo(Duration.ofDays(7)) <= 0);
                } else {
                    assertEquals(SanctionType.WARNING, sanction.type());
                }
            }
        }
    }

    @Test
    void vpnProfileAndKnowledgeUnprovenDupeCasesAreNonPunitiveComplianceOrRemedyOnly() {
        PolicySnapshot snapshot = load().activeSnapshot();
        PolicyResolver resolver = new PolicyResolver();

        for (String offenseId : List.of(
                "access.vpn-compliance",
                "profile.inappropriate-username",
                "profile.inappropriate-skin",
                "profile.inappropriate-other",
                "exploit.duplicated-item-possession-unknown",
                "market.stall-compliance",
                "disruption.chunk-loading",
                "disruption.laggy-build"
        )) {
            assertComplianceFinding(snapshot, resolver, offenseId);
        }
    }

    private static void assertComplianceFinding(
            PolicySnapshot snapshot, PolicyResolver resolver, String offenseId
    ) {
        var resolution = resolver.resolve(
                snapshot, new IncidentFinding(offenseId, complianceAttributes(offenseId)),
                NOW, List.of());
        assertInstanceOf(PolicyAction.RemedyOnly.class, resolution.action(), offenseId);
        assertFalse(resolution.remedies().isEmpty(), offenseId);
        assertEquals(0.0, resolution.history().totalContribution(), 0.0, offenseId);
    }

    @Test
    void everySupportedRemedyHasVersionedEnforcementMetadataAndResolvesFromPinnedFinding() {
        PolicySnapshot snapshot = load().activeSnapshot();
        PolicyV2RemedyBindingResolver resolver = new PolicyV2RemedyBindingResolver();

        List<RemedySpec> remedies = snapshot.offenses().stream()
                .flatMap(offense -> offense.rules().stream())
                .flatMap(rule -> rule.remedies().stream())
                .toList();
        assertTrue(remedies.stream().anyMatch(remedy -> remedy.type() == RemedySpec.Type.OTHER));
        assertTrue(remedies.stream().anyMatch(remedy -> remedy.type() != RemedySpec.Type.OTHER));

        for (OffensePolicy offense : snapshot.offenses()) {
            assertOffenseRemedyBindings(offense, resolver);
        }
    }

    private static void assertOffenseRemedyBindings(
            OffensePolicy offense, PolicyV2RemedyBindingResolver resolver
    ) {
        IncidentFinding finding = new IncidentFinding(
                offense.id(), complianceAttributes(offense.id()));
        for (var rule : offense.rules()) {
            for (RemedySpec remedy : rule.remedies()) {
                assertBindingMatchesPinnedFinding(remedy, finding, resolver, offense.id(), rule.id());
            }
        }
    }

    private static void assertBindingMatchesPinnedFinding(
            RemedySpec remedy,
            IncidentFinding finding,
            PolicyV2RemedyBindingResolver resolver,
            String offenseId,
            String ruleId
    ) {
        if (remedy.type() == RemedySpec.Type.OTHER) {
            assertTrue(remedy.enforcementBinding().isEmpty(), offenseId);
            return;
        }
        assertTrue(remedy.enforcementBinding().isPresent(),
                offenseId + RULE_SEPARATOR + ruleId + RULE_SEPARATOR + remedy.id());
        var resolved = resolver.resolve(remedy, finding);
        assertEquals(remedy.enforcementBinding().orElseThrow().scope(), resolved.scope());
    }

    @Test
    void ownerProfileAndVpnRemediesResolveToCorrectTypedConditions() {
        PolicySnapshot snapshot = load().activeSnapshot();
        PolicyV2RemedyBindingResolver resolver = new PolicyV2RemedyBindingResolver();

        for (String offenseId : List.of(
                "profile.inappropriate-username",
                "profile.inappropriate-skin",
                "profile.inappropriate-other",
                "access.vpn-compliance"
        )) {
            assertProfileVpnCondition(snapshot, resolver, offenseId);
        }
    }

    private static void assertProfileVpnCondition(
            PolicySnapshot snapshot, PolicyV2RemedyBindingResolver resolver, String offenseId
    ) {
        IncidentFinding finding = new IncidentFinding(offenseId, complianceAttributes(offenseId));
        RemedySpec remedy = snapshot.offense(offenseId).orElseThrow()
                .rules().getFirst().remedies().getFirst();
        var resolved = resolver.resolve(remedy, finding);
        assertEquals(PolicyV2RemedyEnforcement.Scope.NETWORK_ACCESS, resolved.scope(), offenseId);
        var expected = switch (offenseId) {
            case "profile.inappropriate-username" ->
                    PolicyV2RemedyEnforcement.Condition.username("BadName");
            case "profile.inappropriate-skin" ->
                    PolicyV2RemedyEnforcement.Condition.profileComponent("skin", "skin:bad");
            case "profile.inappropriate-other" ->
                    PolicyV2RemedyEnforcement.Condition.profileComponent("cape", "component:bad");
            default -> PolicyV2RemedyEnforcement.Condition.vpnApproval();
        };
        assertEquals(expected, resolved.condition(), offenseId);
    }

    @Test
    void nonBaselineHistoryTiersRetainMandatoryRemediesWithinEachContext() {
        for (OffensePolicy offense : load().activeSnapshot().offenses()) {
            assertOffenseHistoryRemedies(offense);
        }
    }

    private static void assertOffenseHistoryRemedies(OffensePolicy offense) {
        Map<String, List<String>> baseline = baselineRemediesByContext(offense);
        for (var rule : offense.rules()) {
            String suffix = historyTierSuffix(rule.id());
            if (suffix == null) {
                continue;
            }
            String group = rule.id().substring(0, rule.id().length() - suffix.length());
            List<String> expected = baseline.get(group);
            if (expected != null) {
                assertEquals(expected, remedyIds(rule.remedies()), offense.id() + RULE_SEPARATOR + rule.id());
            }
        }
    }

    private static Map<String, List<String>> baselineRemediesByContext(OffensePolicy offense) {
        Map<String, List<String>> baseline = new java.util.HashMap<>();
        for (var rule : offense.rules()) {
            if (rule.id().equals("baseline") || rule.id().endsWith("-baseline")) {
                String group = rule.id().substring(0, rule.id().length() - "baseline".length());
                baseline.put(group, remedyIds(rule.remedies()));
            }
        }
        return baseline;
    }

    private static String historyTierSuffix(String ruleId) {
        for (String tier : HISTORY_TIERS) {
            if (ruleId.equals(tier) || ruleId.endsWith("-" + tier)) {
                return tier;
            }
        }
        return null;
    }

    private static List<String> remedyIds(List<RemedySpec> remedies) {
        return remedies.stream().map(RemedySpec::id).sorted().toList();
    }

    @Test
    void olderOwnerSnapshotRemainsImmutableAndUnbound() {
        PolicyV2Configuration config = load();
        PolicySnapshot archived = config.snapshots().get("owner.2026-10-07.1");
        assertEquals(85, archived.offenses().size());
        assertTrue(archived.offense("profile.inappropriate-username")
                .orElseThrow().attributes().isEmpty());
        assertTrue(archived.offenses().stream()
                .flatMap(offense -> offense.rules().stream())
                .flatMap(rule -> rule.remedies().stream())
                .allMatch(remedy -> remedy.enforcementBinding().isEmpty()));
    }

    @Test
    void remedyOnlyComplianceFindingsNeverContributeToBehavioralHistory() {
        PolicySnapshot snapshot = load().activeSnapshot();
        Set<String> remedyOnlyOffenses = snapshot.offenses().stream()
                .filter(offense -> offense.rules().stream()
                        .allMatch(rule -> rule.action() instanceof PolicyAction.RemedyOnly))
                .map(OffensePolicy::id)
                .collect(Collectors.toUnmodifiableSet());

        assertFalse(remedyOnlyOffenses.isEmpty());
        for (String offenseId : remedyOnlyOffenses) {
            assertTrue(
                    snapshot.offense(offenseId).orElseThrow()
                            .historyPolicy().relationshipWeights().isEmpty(),
                    offenseId
            );
        }
        for (OffensePolicy offense : snapshot.offenses()) {
            assertTrue(
                    offense.historyPolicy().relationshipWeights().keySet().stream()
                            .noneMatch(remedyOnlyOffenses::contains),
                    offense.id()
            );
        }
    }

    @Test
    void everyPermanentNetworkBanRequiresAdminOrFounderApproval() {
        PolicySnapshot snapshot = load().activeSnapshot();

        for (OffensePolicy offense : snapshot.offenses()) {
            for (var rule : offense.rules()) {
                List<SanctionSpec> sanctions = punitiveSanctions(rule.action());
                boolean hasPermanentNetworkBan = sanctions.stream().anyMatch(
                        sanction -> sanction.type() == SanctionType.NETWORK_BAN
                                && sanction.length().isPermanent()
                );
                if (!hasPermanentNetworkBan) {
                    continue;
                }
                PolicyAction.ExactWithApproval exact = assertInstanceOf(
                        PolicyAction.ExactWithApproval.class,
                        rule.action(),
                        offense.id() + RULE_SEPARATOR + rule.id()
                );
                assertTrue(
                        exact.minimumRank().atLeast(StaffRank.ADMIN),
                        offense.id() + RULE_SEPARATOR + rule.id()
                );
            }
        }
    }

    @Test
    void terminalSafetyRulesAreFixedPermanentBansWithAdminApproval() {
        PolicySnapshot snapshot = load().activeSnapshot();

        for (String offenseId : List.of(
                "safety.credible-threat",
                "privacy.doxxing",
                "safety.blackmail-extortion",
                "safety.grooming",
                "safety.illegal-exploitative-content",
                "security.malicious-link",
                "security.malicious-file",
                "disruption.server-crash-attempt",
                "account.theft"
        )) {
            OffensePolicy offense = snapshot.offense(offenseId).orElseThrow();
            for (var rule : offense.rules()) {
                PolicyAction.ExactWithApproval action = assertInstanceOf(
                        PolicyAction.ExactWithApproval.class,
                        rule.action(),
                        offenseId + RULE_SEPARATOR + rule.id()
                );
                assertEquals(
                        StaffRank.ADMIN,
                        action.minimumRank(),
                        offenseId + RULE_SEPARATOR + rule.id()
                );
                assertTrue(
                        action.sanctions().stream().anyMatch(
                                sanction -> sanction.type() == SanctionType.NETWORK_BAN
                                        && sanction.length().isPermanent()
                        ),
                        offenseId + RULE_SEPARATOR + rule.id()
                );
            }
        }
    }

    private static List<SanctionSpec> punitiveSanctions(PolicyAction action) {
        if (action instanceof PolicyAction.Exact exact) {
            return exact.sanctions();
        }
        if (action instanceof PolicyAction.ExactWithApproval exact) {
            return exact.sanctions();
        }
        if (action instanceof PolicyAction.Bounded bounded) {
            return bounded.allowedOptions().stream().flatMap(List::stream).toList();
        }
        return List.of();
    }

    private static Map<String, IncidentAttributeValue> complianceAttributes(String offenseId) {
        return switch (offenseId) {
            case "profile.inappropriate-username" -> Map.of(
                    "prohibited-username", new IncidentAttributeValue.TextValue("BadName"));
            case "profile.inappropriate-skin" -> Map.of(
                    "prohibited-value", new IncidentAttributeValue.TextValue("skin:bad"));
            case "profile.inappropriate-other" -> Map.of(
                    "prohibited-value", new IncidentAttributeValue.TextValue("component:bad"),
                    "profile-component", new IncidentAttributeValue.TextValue("cape"));
            default -> Map.of();
        };
    }

    private static PolicyV2Configuration load() {
        return new PolicyV2ConfigurationLoader().load(
                PolicyV2OwnerSnapshotTest.class.getResourceAsStream("/policy-v2.yml"),
                "policy-v2.yml"
        );
    }
}
