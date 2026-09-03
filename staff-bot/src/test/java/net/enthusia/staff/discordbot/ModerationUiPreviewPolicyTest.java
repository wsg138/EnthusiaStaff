package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModerationUiPreviewPolicyTest {
    private final ModerationUiPreviewPolicy policy = new ModerationUiPreviewPolicy();

    @Test
    void firstMinorOffenseStartsWithLightRecommendation() {
        ModerationUiPreviewModel.Recommendation recommendation = policy.evaluate(
                ModerationUiPreviewModel.SampleScenario.FIRST_MINOR,
                ModerationUiPreviewModel.Offense.SPAM);

        assertEquals(0, recommendation.relevantHistory());
        assertEquals(1, recommendation.ladderStep());
        assertEquals(ModerationUiPreviewModel.Action.WARN, recommendation.action());
        assertEquals(ModerationUiPreviewModel.NOT_APPLICABLE, recommendation.duration());
    }

    @Test
    void repeatOffenseUsesOnlyMatchingHistoryToEscalate() {
        ModerationUiPreviewModel.Recommendation recommendation = policy.evaluate(
                ModerationUiPreviewModel.SampleScenario.REPEAT,
                ModerationUiPreviewModel.Offense.SPAM);

        assertEquals(4, recommendation.totalHistory());
        assertEquals(2, recommendation.relevantHistory());
        assertEquals(3, recommendation.ladderStep());
        assertEquals(ModerationUiPreviewModel.Action.MUTE, recommendation.action());
        assertEquals("3d", recommendation.duration());
        assertTrue(recommendation.explanation().contains("2 prior Spam / flooding"));
        assertTrue(recommendation.explanation().contains("2 other moderation"));
    }

    @Test
    void unrelatedHistoryDoesNotBecomeLadderProgression() {
        ModerationUiPreviewModel.Recommendation recommendation = policy.evaluate(
                ModerationUiPreviewModel.SampleScenario.UNRELATED_HISTORY,
                ModerationUiPreviewModel.Offense.SPAM);

        assertEquals(6, recommendation.totalHistory());
        assertEquals(1, recommendation.relevantHistory());
        assertEquals(2, recommendation.ladderStep());
        assertEquals(ModerationUiPreviewModel.Action.MUTE, recommendation.action());
        assertEquals("2h", recommendation.duration());
        assertTrue(recommendation.explanation().contains("5 other moderation"));
    }

    @Test
    void severeOffenseCanStartHigherWithoutInventingRelevantPriors() {
        ModerationUiPreviewModel.Recommendation recommendation = policy.evaluate(
                ModerationUiPreviewModel.SampleScenario.SEVERE,
                ModerationUiPreviewModel.Offense.HATE_SLURS);

        assertEquals(0, recommendation.relevantHistory());
        assertEquals(3, recommendation.ladderStep());
        assertEquals(ModerationUiPreviewModel.Action.BAN, recommendation.action());
        assertEquals("30d", recommendation.duration());
        assertTrue(recommendation.explanation().contains("starts at ladder step 3"));
    }

    @Test
    void adminEscalationShowsPermanentRecommendationAndApproval() {
        ModerationUiPreviewModel.Recommendation recommendation = policy.evaluate(
                ModerationUiPreviewModel.SampleScenario.ADMIN_ESCALATION,
                ModerationUiPreviewModel.Offense.HATE_SLURS);

        assertEquals(4, recommendation.totalHistory());
        assertEquals(3, recommendation.relevantHistory());
        assertEquals(4, recommendation.ladderStep());
        assertEquals(ModerationUiPreviewModel.Action.BAN, recommendation.action());
        assertEquals(ModerationUiPreviewModel.PERMANENT, recommendation.duration());
        assertTrue(recommendation.approvalRequirement().contains("Admin+"));
    }
}
