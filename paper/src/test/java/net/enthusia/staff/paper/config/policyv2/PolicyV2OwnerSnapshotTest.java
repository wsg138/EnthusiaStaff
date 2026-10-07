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

    @Test
    void bundledOwnerSnapshotIsCompleteButRuntimeRemainsDisabled() {
        PolicyV2Configuration configuration = load();
        PolicySnapshot snapshot = configuration.activeSnapshot();

        assertEquals(PolicyV2FeatureMode.DISABLED, configuration.mode());
        assertEquals(OWNER_VERSION, configuration.activeVersion());
        assertEquals(OWNER_VERSION, snapshot.version());
        assertEquals(3, configuration.snapshots().size());
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
            var resolution = resolver.resolve(
                    snapshot,
                    new IncidentFinding(offenseId, complianceAttributes(offenseId)),
                    NOW,
                    List.of()
            );
            assertInstanceOf(PolicyAction.RemedyOnly.class, resolution.action(), offenseId);
            assertFalse(resolution.remedies().isEmpty(), offenseId);
            assertEquals(0.0, resolution.history().totalContribution(), 0.0, offenseId);
        }
    }

    @Test
    void everySupportedRemedyHasVersionedEnforcementMetadataAndResolvesFromPinnedFinding() {
        PolicySnapshot snapshot = load().activeSnapshot();
        PolicyV2RemedyBindingResolver bindings = new PolicyV2RemedyBindingResolver();
        int supported = 0;
        int manualOnly = 0;

        for (OffensePolicy offense : snapshot.offenses()) {
            IncidentFinding finding = new IncidentFinding(
                    offense.id(),
                    complianceAttributes(offense.id())
            );
            for (var rule : offense.rules()) {
                for (RemedySpec remedy : rule.remedies()) {
                    if (remedy.type() == RemedySpec.Type.OTHER) {
                        manualOnly++;
                        assertTrue(remedy.enforcementBinding().isEmpty(), offense.id());
                        continue;
                    }
                    supported++;
                    assertTrue(remedy.enforcementBinding().isPresent(),
                            offense.id() + " / " + rule.id() + " / " + remedy.id());
                    var binding = bindings.resolve(remedy, finding);
                    assertEquals(remedy.enforcementBinding().orElseThrow().scope(), binding.scope());
                }
            }
        }
        assertTrue(supported > 0);
        assertTrue(manualOnly > 0);
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
            IncidentFinding finding = new IncidentFinding(offenseId, complianceAttributes(offenseId));
            RemedySpec remedy = snapshot.offense(offenseId).orElseThrow()
                    .rules().getFirst().remedies().getFirst();
            var resolved = resolver.resolve(remedy, finding);
            assertEquals(
                    PolicyV2RemedyEnforcement.Scope.NETWORK_ACCESS,
                    resolved.scope(),
                    offenseId
            );
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
    }

    @Test
    void nonBaselineHistoryTiersRetainMandatoryRemediesWithinEachContext() {
        PolicySnapshot snapshot = load().activeSnapshot();

        for (OffensePolicy offense : snapshot.offenses()) {
            Map<String, List<String>> baseline = new java.util.HashMap<>();
            for (var rule : offense.rules()) {
                if (rule.id().equals("baseline") || rule.id().endsWith("-baseline")) {
                    String group = rule.id().substring(0, rule.id().length() - "baseline".length());
                    baseline.put(group, rule.remedies().stream().map(RemedySpec::id).sorted().toList());
                }
            }
            for (var rule : offense.rules()) {
                String suffix = List.of("related", "pattern", "chronic", "heavy").stream()
                        .filter(name -> rule.id().equals(name) || rule.id().endsWith("-" + name))
                        .findFirst().orElse(null);
                if (suffix == null) {
                    continue;
                }
                String group = rule.id().substring(0, rule.id().length() - suffix.length());
                if (!baseline.containsKey(group)) {
                    continue;
                }
                assertEquals(
                        baseline.get(group),
                        rule.remedies().stream().map(RemedySpec::id).sorted().toList(),
                        offense.id() + " / " + rule.id()
                );
            }
        }
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
                        offense.id() + " / " + rule.id()
                );
                assertTrue(
                        exact.minimumRank().atLeast(StaffRank.ADMIN),
                        offense.id() + " / " + rule.id()
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
                        offenseId + " / " + rule.id()
                );
                assertEquals(
                        StaffRank.ADMIN,
                        action.minimumRank(),
                        offenseId + " / " + rule.id()
                );
                assertTrue(
                        action.sanctions().stream().anyMatch(
                                sanction -> sanction.type() == SanctionType.NETWORK_BAN
                                        && sanction.length().isPermanent()
                        ),
                        offenseId + " / " + rule.id()
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
