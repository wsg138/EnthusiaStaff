package net.enthusia.staff.domain.guided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.guided.GuidedChatInterview.Key;
import org.junit.jupiter.api.Test;

class GuidedChatInterviewTest {
    private static GuidedChatInterview spam(String medium, String extent, String continued) {
        GuidedChatInterview draft = GuidedChatInterview.start()
                .answer(Key.MEDIUM, medium).answer(Key.CONDUCT, "spam")
                .answer(Key.EXTENT, extent).answer(Key.CONTINUED, continued);
        if ("sign".equals(medium) || "book".equals(medium)) {
            draft = draft.answer(Key.PERSISTENCE, "persistent");
        }
        return draft.answer(Key.EVIDENCE, "verified");
    }

    @Test
    void aShortIncidentAndSustainedSpamProduceDifferentFindings() {
        var minor = spam("public-chat", "few", "no").finding();
        var severe = spam("public-chat", "large-scale", "yes").finding();
        assertEquals("chat.spam.low-level", minor.candidateId());
        assertEquals("chat.spam.flooding", severe.candidateId());
        assertTrue(severe.impact() > minor.impact());
        assertTrue(severe.factors().contains("Continued after warning"));
        assertFalse(minor.reviewRequired());
    }

    @Test
    void mediumChangesMeaningfulMappingAndRemedyCannotBeAssumed() {
        assertEquals("chat.spam.private-message", spam("direct-message", "few", "no")
                .finding().candidateId());
        assertEquals("chat.disruption.noise", spam("voice", "few", "no")
                .finding().candidateId());
        var sign = spam("sign", "few", "no").finding();
        assertNull(sign.candidateId());
        assertTrue(sign.reviewRequired());
        assertTrue(sign.factors().contains("Persistent placed content"));
    }

    @Test
    void changingParentClearsAllInapplicableAnswers() {
        GuidedChatInterview previous = spam("public-chat", "sustained", "yes");
        var amended = previous.answer(Key.CONDUCT, "slur");
        assertFalse(amended.answers().containsKey(Key.EXTENT));
        assertFalse(amended.answers().containsKey(Key.CONTINUED));
        assertFalse(amended.answers().containsKey(Key.EVIDENCE));
        assertEquals(Key.TARGETING, amended.nextQuestion().orElseThrow().id());
        amended = amended.answer(Key.TARGETING, "targeted").answer(Key.EVIDENCE, "verified");
        assertEquals("hate.slur", amended.finding().candidateId());
        assertTrue(amended.finding().reviewRequired());
    }

    @Test
    void missingOrDisputedEvidenceRequiresReviewWithoutInflatingSeverity() {
        var verified = spam("public-chat", "few", "no");
        var uncertain = verified.answer(Key.EVIDENCE, "conflicting");
        assertEquals(verified.finding().impact(), uncertain.finding().impact());
        assertTrue(uncertain.finding().reviewRequired());
    }

    @Test
    void uncertainAndSensitiveIncidentsDoNotForcePunishments() {
        var threat = GuidedChatInterview.start().answer(Key.MEDIUM, "public-chat")
                .answer(Key.CONDUCT, "threat-privacy")
                .answer(Key.THREAT, "credible").answer(Key.EVIDENCE, "verified");
        assertTrue(threat.finding().reviewRequired());
        var unidentified = GuidedChatInterview.start().answer(Key.MEDIUM, "unknown")
                .answer(Key.CONDUCT, "other").answer(Key.EVIDENCE, "unverified");
        assertNull(unidentified.finding().candidateId());
        assertTrue(unidentified.finding().reviewRequired());
    }

    @Test
    void sameConductCanHaveDifferentImpactWithoutPresetLadderSteps() {
        var beforeWarning = spam("public-chat", "few", "no").finding();
        var afterWarning = spam("public-chat", "few", "yes").finding();
        assertEquals(beforeWarning.candidateId(), afterWarning.candidateId());
        assertTrue(afterWarning.impact() > beforeWarning.impact());
    }

    @Test
    void untargetedAbuseAndNonCredibleThreatAreNotForcedIntoSevereReasons() {
        var general = GuidedChatInterview.start().answer(Key.MEDIUM, "public-chat")
                .answer(Key.CONDUCT, "harassment")
                .answer(Key.TARGETING, "general").answer(Key.EVIDENCE, "verified");
        assertNull(general.finding().candidateId());
        assertTrue(general.finding().reviewRequired());

        var unproven = GuidedChatInterview.start().answer(Key.MEDIUM, "public-chat")
                .answer(Key.CONDUCT, "threat-privacy")
                .answer(Key.THREAT, "not-credible").answer(Key.EVIDENCE, "verified");
        assertNull(unproven.finding().candidateId());
        assertTrue(unproven.finding().reviewRequired());
    }

    @Test
    void cannotChooseASanctionOrSkipMandatoryQuestions() {
        GuidedChatInterview draft = GuidedChatInterview.start();
        assertEquals(Key.MEDIUM, draft.nextQuestion().orElseThrow().id());
        assertThrows(IllegalArgumentException.class, () -> draft.answer(Key.EVIDENCE, "verified"));
        assertThrows(IllegalArgumentException.class, () -> draft.answer(Key.MEDIUM, "fake-medium"));
        assertThrows(IllegalStateException.class, draft::finding);
    }
}
