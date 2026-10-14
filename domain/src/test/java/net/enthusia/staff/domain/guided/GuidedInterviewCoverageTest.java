package net.enthusia.staff.domain.guided;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Walk every category/conduct/detail branch and catch unreachable GUI options. */
class GuidedInterviewCoverageTest {
    private static final String DETAIL = "detail";
    private static final Map<String, Set<String>> REACHABILITY_CHOICES = Map.of(
            "continued", Set.of("not-applicable"),
            "persistence", Set.of("removed"),
            "extent", Set.of("few", "large-scale")
    );
    @Test
    void everyCategorySubtypeIsReachableWithAtMostSevenOptionsPerScreen() throws Exception {
        String policies = Files.readString(
                Path.of("../paper/src/main/resources/reason-policies.yml"));
        int count = 0;
        for (String category : new String[]{"chat", "gameplay", "safety",
                "accounts", "community", "economy"}) {
            var starting = GuidedIncidentWizard.start(category);
            for (var medium : starting.nextQuestion().orElseThrow().choices()) {
                var location = starting.answer("medium", medium.id());
                for (var conduct : location.nextQuestion().orElseThrow().choices()) {
                    var behavior = location.answer("conduct", conduct.id());
                    if (behavior.nextQuestion().isPresent()
                            && DETAIL.equals(behavior.nextQuestion().orElseThrow().id())) {
                        for (var detail : behavior.nextQuestion().orElseThrow().choices()) {
                            verifyRoute(behavior.answer("detail", detail.id()), policies);
                            count++;
                        }
                    } else {
                        verifyRoute(behavior, policies);
                        count++;
                    }
                }
            }
        }
        assertTrue(count >= 300, "Expected all navigation branches");
    }

    @Test
    void everyMappedPolicyReasonHasAReachableStaffInterview() {
        Set<String> reachable = new HashSet<>();
        for (String category : new String[]{"chat", "gameplay", "safety",
                "accounts", "community", "economy"}) {
            collectReachable(GuidedIncidentWizard.start(category), reachable);
        }
        Set<String> missing = new HashSet<>(GuidedOffenseClassifier.mappedReasonIds());
        missing.removeAll(reachable);
        assertTrue(missing.isEmpty(), "Reason mappings not accessible from the GUI: " + missing);
    }

    private static void collectReachable(GuidedIncidentWizard interview,
                                         Set<String> reachable) {
        var question = interview.nextQuestion();
        if (question.isEmpty()) {
            var finding = interview.finding();
            if (finding.candidateId() != null) {
                reachable.add(finding.candidateId());
            }
            return;
        }
        var prompt = question.orElseThrow();
        for (var choice : coverageChoices(prompt)) {
            collectReachable(interview.answer(prompt.id(), choice.id()), reachable);
        }
    }

    private static List<GuidedIncidentWizard.Choice> coverageChoices(
            GuidedIncidentWizard.Question question) {
        Set<String> selection = REACHABILITY_CHOICES.get(question.id());
        if (selection == null) {
            return question.choices();
        }
        return question.choices().stream()
                .filter(choice -> selection.contains(choice.id()))
                .toList();
    }

    private void verifyRoute(GuidedIncidentWizard incident, String policies) {
        GuidedIncidentWizard current = incident;
        for (int depth = 0; current.nextQuestion().isPresent(); depth++) {
            assertTrue(depth < 10, "Potential questionnaire loop");
            var question = current.nextQuestion().orElseThrow();
            assertTrue(question.choices().size() <= 7, "Too many GUI answers: " + question.id());
            String response = switch (question.id()) {
                case "targeting" -> "targeted";
                case "extent" -> "few";
                case "continued" -> "not-applicable";
                case "persistence" -> "removed";
                default -> question.choices().getFirst().id();
            };
            current = current.answer(question.id(), response);
        }
        var finding = current.finding();
        if (finding.candidateId() != null) {
            assertTrue(policies.contains("- id: " + finding.candidateId() + "\n")
                            || policies.contains("- id: " + finding.candidateId() + "\r\n"),
                    "Questionnaire resolved to an unknown reason: " + finding.candidateId());
        } else {
            assertTrue(finding.needsReview(), "Policy gap must require review");
        }
        assertFalse(current.nextQuestion().isPresent());
    }
}
