package net.enthusia.staff.domain.guided;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GuidedIncidentWizardTest {
    private static GuidedIncidentWizard spam(String extent, String warning) {
        return GuidedIncidentWizard.start("chat")
                .answer("medium", "public-chat")
                .answer("conduct", "spam")
                .answer("extent", extent)
                .answer("continued", warning);
    }

    @Test
    void normalChatFlowStartsWithPlaceNotSpecificOffense() {
        var flow = GuidedIncidentWizard.start("chat");
        assertEquals("medium", flow.nextQuestion().orElseThrow().id());
        flow = flow.answer("medium", "public-chat");
        assertEquals("conduct", flow.nextQuestion().orElseThrow().id());
        flow = flow.answer("conduct", "spam");
        assertEquals("extent", flow.nextQuestion().orElseThrow().id());
        flow = flow.answer("extent", "few");
        assertEquals("continued", flow.nextQuestion().orElseThrow().id());
        assertTrue(flow.nextQuestion().orElseThrow().choices().stream()
                .anyMatch(x -> x.id().equals("not-applicable")));
    }

    @Test
    void noWarningDoesNotInflateImpact() {
        var notWarned = spam("few", "not-applicable").finding();
        var stopped = spam("few", "no").finding();
        var ignored = spam("few", "yes").finding();
        assertEquals(notWarned.preliminaryImpact(), stopped.preliminaryImpact());
        assertTrue(ignored.preliminaryImpact() > notWarned.preliminaryImpact());
        assertEquals("spam.low-level", ignored.candidateId());
    }

    @Test
    void answerChangeClearsAllDependentAnswers() {
        var flow = spam("sustained", "yes");
        flow = flow.answer("conduct", "slur");
        assertFalse(flow.answers().containsKey("extent"));
        assertFalse(flow.answers().containsKey("continued"));
        assertEquals("targeting", flow.nextQuestion().orElseThrow().id());
        assertThrows(IllegalStateException.class, flow::finding);
    }

    @Test
    void otherCategoriesHaveDistinctRelevantChoicesButNoUnapprovedSanction() {
        for (String category : new String[]{"gameplay","safety","accounts","community","economy"}) {
            var flow = GuidedIncidentWizard.start(category).answer("medium", "in-game");
            var conduct = flow.nextQuestion().orElseThrow();
            assertEquals("conduct", conduct.id());
            String value = conduct.choices().get(0).id();
            flow = flow.answer("conduct", value);
            while (flow.nextQuestion().isPresent()) {
                var q = flow.nextQuestion().orElseThrow();
                String answer = q.id().equals("continued") ? "not-applicable" : q.choices().get(0).id();
                flow = flow.answer(q.id(), answer);
            }
            assertTrue(flow.finding().needsReview(), category + " needs calibrated policy");
            assertNull(flow.finding().candidateId());
        }
    }

    @Test
    void signSpamCannotBecomeAChatMuteWithoutReview() {
        var flow = GuidedIncidentWizard.start("chat")
                .answer("medium", "sign").answer("conduct", "spam")
                .answer("extent", "sustained").answer("continued", "not-applicable")
                .answer("persistence", "persistent");
        assertTrue(flow.finding().needsReview());
        assertNull(flow.finding().candidateId());
    }

    @Test
    void backNavigationAndChoiceValidationAreFailClosed() {
        var flow = GuidedIncidentWizard.start("chat");
        assertThrows(IllegalArgumentException.class, () -> flow.answer("conduct", "spam"));
        assertThrows(IllegalArgumentException.class, () -> flow.answer("medium", "fake"));
        assertEquals(flow.answers(), flow.back().answers());
        var first = flow.answer("medium", "public-chat");
        assertTrue(first.back().answers().isEmpty());
    }
}
