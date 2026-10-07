package net.enthusia.staff.paper.punishment.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.DecayPolicy;
import net.enthusia.staff.domain.policyv2.HistoryPolicy;
import net.enthusia.staff.domain.policyv2.HistoryWindow;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.ResolutionRule;
import net.enthusia.staff.domain.policyv2.RuleCondition;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2PresentationTest {
    private static final Instant NOW = Instant.parse("2026-10-07T01:00:00Z");
    private static final String HARASSMENT_ID = "harassment.targeted";
    private static final UUID TARGET = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("71000000-0000-0000-0000-000000000002");

    @Test
    void navigationContainsRequiredSpecificCategoriesAndNoGriefingCategory() {
        List<String> titles = PolicyV2Category.ordered().stream().map(PolicyV2Category::title).toList();

        assertEquals(List.of(
                "Chat & Spam",
                "Harassment & Abuse",
                "Hate & Extremism",
                "Sexual/Inappropriate Content",
                "Safety/Threats/Privacy",
                "Advertising/Scams",
                "Cheating",
                "Exploits/Bug Abuse/Duplication",
                "Server Disruption",
                "Accounts/VPN/Access",
                "Evasion/Alt Abuse",
                "Profiles/Identity",
                "Reports/Evidence/Staff Cooperation",
                "Economy/Market",
                "Reputation",
                "Policy Gap"
        ), titles);
        assertFalse(titles.stream().anyMatch(title -> title.toLowerCase(Locale.ROOT).contains("grief")));
    }

    @Test
    void browsingShowsOnlyOffensesMappedToTheSelectedNavigationGroup() {
        PolicySnapshot snapshot = snapshot();
        PolicyV2ManualWorkflow workflow = new PolicyV2ManualWorkflow(
                () -> snapshot,
                (subjectId, at) -> List.of(),
                (review, key, at) -> UUID.randomUUID(),
                new DefaultAuthorizationPolicy(),
                java.time.Clock.systemUTC()
        );

        assertEquals(
                List.of(HARASSMENT_ID),
                workflow.offenses(PolicyV2Category.HARASSMENT_ABUSE).stream()
                        .map(OffensePolicy::id)
                        .toList()
        );
        assertEquals(
                List.of("cheating.client"),
                workflow.offenses(PolicyV2Category.CHEATING).stream()
                        .map(OffensePolicy::id)
                        .toList()
        );
        assertTrue(workflow.offenses(PolicyV2Category.POLICY_GAP).isEmpty());
    }

    @Test
    void reviewPresentationExplainsHistoryWithoutRawMathOrInternalIds() {
        List<BehavioralHistoryEntry> history = List.of(
                history("CASE-PRIVATE-1", 12),
                history("CASE-PRIVATE-2", 4)
        );
        PolicyV2ManualWorkflow workflow = new PolicyV2ManualWorkflow(
                PolicyV2PresentationTest::snapshot,
                (subjectId, at) -> history,
                (review, key, at) -> UUID.randomUUID(),
                new DefaultAuthorizationPolicy(),
                java.time.Clock.systemUTC()
        );
        PolicyV2ManualDraft draft = PolicyV2ManualDraft.start(TARGET, NOW)
                .selectCategory(PolicyV2Category.HARASSMENT_ABUSE)
                .selectOffense(HARASSMENT_ID);
        draft = workflow.answer(draft, "targeted", new IncidentAttributeValue.BooleanValue(true));

        PolicyV2ReviewPresentation view = PolicyV2ReviewPresentation.from(
                workflow.review(new Actor(ACTOR_ID, "Mod", StaffRank.MOD), draft)
        );

        assertEquals("Targeted Harassment", view.whatHappened());
        assertEquals(List.of("Targeted: Yes"), view.confirmedAttributes());
        assertTrue(view.historyExplanation().contains("Related history"));
        assertTrue(view.historyExplanation().contains("Repeated related conduct"));
        assertFalse(view.historyExplanation().contains("CASE-PRIVATE"));
        assertFalse(view.historyExplanation().matches(".*0\\.[0-9]+.*"));
        assertEquals("policy-test", view.policyVersion());
        assertTrue(view.authorityNotice().contains("Policy v1 remains authoritative"));
    }

    @Test
    void humanizedQuestionLabelsDoNotExposeStableIdsVerbatim() {
        assertEquals("Target Protected", PolicyV2ReviewPresentation.humanize("target.protected"));
        assertEquals("High Severity", PolicyV2ReviewPresentation.humanize("high-severity"));
    }

    private static BehavioralHistoryEntry history(String caseId, long daysAgo) {
        return new BehavioralHistoryEntry(
                caseId,
                NOW.minus(Duration.ofDays(daysAgo)),
                HARASSMENT_ID,
                HARASSMENT_ID,
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );
    }

    private static PolicySnapshot snapshot() {
        return new PolicySnapshot("policy-test", List.of(
                offense(
                        HARASSMENT_ID, "Targeted Harassment", "harassment-abuse",
                        List.of(IncidentAttributeDefinition.booleanValue("targeted", true))
                ),
                offense(
                        "cheating.client", "Unauthorized Client", "cheating",
                        List.of(IncidentAttributeDefinition.enumValue("severity", true, Set.of("low", "high")))
                )
        ));
    }

    private static OffensePolicy offense(
            String id,
            String name,
            String category,
            List<IncidentAttributeDefinition> attributes
    ) {
        return new OffensePolicy(
                id,
                name,
                category,
                attributes,
                new HistoryPolicy(
                        Map.of(id, 1.0),
                        DecayPolicy.exponential(Duration.ofDays(30), 0.5, 3.0)
                ),
                List.of(new ResolutionRule(
                        "base",
                        new RuleCondition(Map.of(), HistoryWindow.atLeast(0.0)),
                        new PolicyAction.Exact(List.of(
                                new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofHours(12)))
                        )),
                        List.of()
                ))
        );
    }
}
