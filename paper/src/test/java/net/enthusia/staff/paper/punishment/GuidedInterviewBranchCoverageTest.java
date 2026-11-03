package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.enthusia.staff.domain.guided.GuidedIncidentWizard;
import net.enthusia.staff.domain.guided.GuidedOffenseClassifier;
import org.junit.jupiter.api.Test;

/**
 * Exhaustively traverses supported answer paths for the guided punishment
 * questionnaire. No live server, actor, policy write or punishment is involved.
 */
class GuidedInterviewBranchCoverageTest {
    private static final List<String> CATEGORIES = List.of(
            "chat", "gameplay", "safety", "accounts", "community", "economy", "other");
    private static final int MAX_GUIDED_ANSWERS = 7;

    private static final class Coverage {
        private int completed;
        private int manual;
        private int classified;
        private int maximumDepth;
    }

    @Test
    void everyReachableGuidedPathTerminatesFitsGuiAndClassifiesConservatively() {
        Coverage results = new Coverage();
        Set<String> mapped = GuidedOffenseClassifier.mappedReasonIds();
        for (String category : CATEGORIES) {
            int before = results.completed;
            visit(GuidedIncidentWizard.start(category), 0, results, mapped);
            assertTrue(results.completed > before, "No terminal path for " + category);
        }
        assertTrue(results.completed > 100, "Expected thorough guided branch coverage");
        assertTrue(results.manual > 0, "Manual-review branches must exist");
        assertTrue(results.classified > 0, "Authorized classification must remain possible");
        assertTrue(results.maximumDepth <= MAX_GUIDED_ANSWERS);
        System.out.printf("Guided branch coverage: %d completed, %d manual, %d classified, "
                + "max depth %d%n", results.completed, results.manual,
                results.classified, results.maximumDepth);
    }

    private static void visit(GuidedIncidentWizard wizard, int depth,
                              Coverage results, Set<String> mapped) {
        assertTrue(depth <= MAX_GUIDED_ANSWERS, "Questionnaire exceeded answer limit");
        assertEquals(depth, wizard.answers().size(),
                "Each step should preserve exactly the recorded answers");
        var question = wizard.nextQuestion();
        if (question.isPresent()) {
            var current = question.orElseThrow();
            assertTrue(!current.choices().isEmpty(), "A question cannot be unanswerable");
            assertTrue(current.choices().size() <= PunishmentGuiRenderer.GUIDED_CHOICE_SLOTS.length,
                    "GUI cannot display all choices for " + current.id());
            for (var choice : current.choices()) {
                visit(wizard.answer(current.id(), choice.id()), depth + 1, results, mapped);
            }
            return;
        }

        var finding = wizard.finding();
        if (depth > 0) {
            var previous = wizard.back();
            assertEquals(depth - 1, previous.answers().size(),
                    "Staff must be able to undo the most recent answer");
            assertTrue(previous.nextQuestion().isPresent(),
                    "Back must restore the prior question");
        }
        results.completed++;
        results.maximumDepth = Math.max(results.maximumDepth, depth);
        if (finding.needsReview()) {
            results.manual++;
        } else {
            results.classified++;
            assertNotNull(finding.candidateId(), "Automatic flow must identify a reason");
        }
        if (finding.candidateId() != null) {
            assertTrue(mapped.contains(finding.candidateId()),
                    "Generated candidate outside the known classifier map: " + finding.candidateId());
        }

        for (String name : List.of("medium", "detail", "extent", "targeting")) {
            if ("unknown".equals(wizard.answers().get(name))) {
                assertTrue(finding.needsReview(), "Unverified " + name + " cannot auto-punish");
            }
        }
    }
}
