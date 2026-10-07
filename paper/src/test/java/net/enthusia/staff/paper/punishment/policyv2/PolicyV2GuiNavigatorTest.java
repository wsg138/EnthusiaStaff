package net.enthusia.staff.paper.punishment.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
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

class PolicyV2GuiNavigatorTest {
    private static final Instant NOW = Instant.parse("2026-10-07T02:00:00Z");
    private static final UUID VIEWER = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("72000000-0000-0000-0000-000000000002");
    private static final String TARGET_NAME = "TargetPlayer";
    private static final String OFFENSE_ID = "harassment.targeted";
    private static final String ATTRIBUTE_ID = "targeted";

    @Test
    void categoryOffenseQuestionsAndBackEditingFormOneCoherentFlow() {
        PolicyV2ManualWorkflow workflow = workflow();
        PolicyV2GuiNavigator navigator = new PolicyV2GuiNavigator(workflow);
        PolicyV2GuiState.Categories start = navigator.start(VIEWER, TARGET, TARGET_NAME, NOW);

        int categoryIndex = start.categories().indexOf(PolicyV2Category.HARASSMENT_ABUSE);
        PolicyV2GuiState.Offenses offenses = assertInstanceOf(
                PolicyV2GuiState.Offenses.class,
                navigator.selectCategory(start, categoryIndex)
        );
        assertEquals(NOW, offenses.draft().incidentAt());
        assertEquals(List.of(OFFENSE_ID), offenses.offenses().stream().map(OffensePolicy::id).toList());

        PolicyV2GuiState.Questions questions = navigator.selectOffense(offenses, 0);
        assertEquals("Targeted Harassment", questions.conductLabel());
        assertEquals(List.of(ATTRIBUTE_ID), questions.questions().stream()
                .map(IncidentAttributeDefinition::id).toList());
        assertThrows(IllegalArgumentException.class, () -> navigator.review(questions));

        PolicyV2ManualDraft answered = workflow.answer(
                questions.draft(), ATTRIBUTE_ID, new IncidentAttributeValue.BooleanValue(true)
        );
        PolicyV2GuiState.Questions answeredState = navigator.withDraft(questions, answered);
        PolicyV2GuiState.Review review = navigator.review(answeredState);
        assertTrue(review.draft().attributes().containsKey(ATTRIBUTE_ID));

        PolicyV2GuiState.Questions reviewBack = assertInstanceOf(
                PolicyV2GuiState.Questions.class, navigator.back(review).orElseThrow()
        );
        assertTrue(reviewBack.draft().attributes().containsKey(ATTRIBUTE_ID));

        PolicyV2GuiState.Offenses questionBack = assertInstanceOf(
                PolicyV2GuiState.Offenses.class, navigator.back(reviewBack).orElseThrow()
        );
        assertTrue(questionBack.draft().offenseId().isEmpty());
        assertTrue(questionBack.draft().attributes().isEmpty());
        assertEquals(NOW, questionBack.draft().incidentAt());
    }

    @Test
    void policyGapUsesDescriptionThenBackReturnsToCategoriesWithoutSentencingControls() {
        PolicyV2GuiNavigator navigator = new PolicyV2GuiNavigator(workflow());
        PolicyV2GuiState.Categories start = navigator.start(VIEWER, TARGET, TARGET_NAME, NOW);
        int gapIndex = start.categories().indexOf(PolicyV2Category.POLICY_GAP);

        PolicyV2GuiState.Questions gap = assertInstanceOf(
                PolicyV2GuiState.Questions.class,
                navigator.selectCategory(start, gapIndex)
        );
        assertTrue(gap.draft().isPolicyGap());
        assertTrue(gap.questions().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> navigator.review(gap));

        PolicyV2GuiState.Questions described = navigator.withDraft(
                gap, gap.draft().describePolicyGap("Novel conduct requiring owner review.")
        );
        PolicyV2GuiState.Review review = navigator.review(described);
        assertEquals("Policy Gap / Unclassified Incident", review.conductLabel());

        PolicyV2GuiState.Categories back = assertInstanceOf(
                PolicyV2GuiState.Categories.class, navigator.back(gap).orElseThrow()
        );
        assertTrue(back.draft().categoryId().isEmpty());
        assertEquals(NOW, back.draft().incidentAt());
    }

    private static PolicyV2ManualWorkflow workflow() {
        return new PolicyV2ManualWorkflow(
                PolicyV2GuiNavigatorTest::snapshot,
                (subjectId, asOf) -> List.of(),
                (review, operationKey, evaluatedAt) -> UUID.randomUUID(),
                new DefaultAuthorizationPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static PolicySnapshot snapshot() {
        OffensePolicy offense = new OffensePolicy(
                OFFENSE_ID,
                "Targeted Harassment",
                PolicyV2Category.HARASSMENT_ABUSE.id(),
                List.of(IncidentAttributeDefinition.booleanValue(ATTRIBUTE_ID, true)),
                new HistoryPolicy(
                        Map.of(OFFENSE_ID, 1.0),
                        DecayPolicy.exponential(Duration.ofDays(30), 0.25, 2.0)
                ),
                List.of(new ResolutionRule(
                        "base",
                        new RuleCondition(Map.of(), HistoryWindow.atLeast(0.0)),
                        new PolicyAction.Exact(List.of(new SanctionSpec(
                                SanctionType.WARNING, SanctionLength.instant()
                        ))),
                        List.of()
                ))
        );
        return new PolicySnapshot("policy-gui-test", List.of(offense));
    }
}
