package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.guided.GuidedIncidentWizard;
import net.enthusia.staff.domain.guided.GuidedPolicyResolution;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;
import org.junit.jupiter.api.Test;

/**
 * Cross-product contract for the unapproved prototype and bundled policy.
 * No punishment is created, no network or database is contacted, and this is
 * NOT an approval of severity thresholds or the owner's live custom YAML.
 */
class GuidedPolicyExhaustiveSafetyTest {
    private static final List<String> CATEGORIES = List.of(
            "chat", "gameplay", "safety", "accounts", "community", "economy", "other");

    private static final class Coverage {
        private int interviews;
        private int manualInterviews;
        private int evaluated;
        private int ordinary;
        private int manualOutcomes;
        private int changedDurations;
    }

    @Test
    void allReachableGuidedFindingsRemainOrdinaryAndPolicyBoundedForEveryHistoryAndPreference() {
        ReasonPolicyConfigurationLoader.LoadedPolicies loaded;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("reason-policies.yml")) {
            assertNotNull(in, "Bundled reason-policies.yml should exist");
            loaded = new ReasonPolicyConfigurationLoader().load(in, "reason-policies.yml");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        Map<String, ReasonPolicy> policies = loaded.policies().stream()
                .collect(Collectors.toMap(ReasonPolicy::id, Function.identity()));
        Coverage coverage = new Coverage();
        for (String category : CATEGORIES) {
            check(GuidedIncidentWizard.start(category), policies, loaded.version(), coverage);
        }
        assertTrue(coverage.interviews > 1000, "Test must enumerate full guided questionnaire graph");
        assertTrue(coverage.manualInterviews > 0);
        assertTrue(coverage.ordinary > 0, "Some prototype paths should support a policy-bounded sanction");
        assertTrue(coverage.manualOutcomes > 0, "Restricted sanctions must require manual review");
        assertTrue(coverage.changedDurations > 0, "Variable mute interpolation must actually be checked");
        System.out.printf("Guided policy cross-product: %d interviews, %d manual interviews, "
                        + "%d outcomes, %d ordinary, %d manual, %d calculated durations%n",
                coverage.interviews, coverage.manualInterviews, coverage.evaluated,
                coverage.ordinary, coverage.manualOutcomes, coverage.changedDurations);
    }

    private static void check(GuidedIncidentWizard interview, Map<String, ReasonPolicy> policies,
                              String policyVersion, Coverage coverage) {
        var next = interview.nextQuestion();
        if (next.isPresent()) {
            var question = next.orElseThrow();
            for (var choice : question.choices()) {
                check(interview.answer(question.id(), choice.id()), policies, policyVersion, coverage);
            }
            return;
        }
        var finding = interview.finding();
        coverage.interviews++;
        if (finding.needsReview()) {
            coverage.manualInterviews++;
        }
        if (finding.candidateId() == null) {
            assertTrue(finding.needsReview(), "Unclassified incidents must never be auto-issued");
            return;
        }
        ReasonPolicy policy = policies.get(finding.candidateId());
        assertNotNull(policy, "Classifier candidate is absent from bundled policy: " + finding.candidateId());
        for (int historical = 0; historical < policy.steps().size(); historical++) {
            var step = policy.steps().get(historical);
            var decision = new EscalationDecision(historical, historical, 0, List.of(), step);
            var assessment = new PunishmentAssessment(policyVersion, policy, decision, step.sanctions());
            Duration previousDuration = null;
            for (int preference = -1; preference <= 1; preference++) {
                coverage.evaluated++;
                GuidedPolicyResolution.Outcome outcome;
                try {
                    outcome = GuidedPolicyResolution.calculate(finding, assessment, preference);
                } catch (IllegalArgumentException manual) {
                    coverage.manualOutcomes++;
                    continue;
                }
                assertTrue(!finding.needsReview(),
                        "Manual-review questionnaire classified a sanction: " + finding.candidateId());
                assertEquals(1, outcome.sanctions().size(),
                        "Guided route must never create a sanction combination");
                SanctionSpec sanction = outcome.sanctions().getFirst();
                boolean warning = sanction.type() == SanctionType.WARNING
                        && sanction.length().isInstant();
                boolean temporaryMute = sanction.type() == SanctionType.MUTE
                        && sanction.length().temporary().isPresent();
                assertTrue(warning || temporaryMute,
                        "Protected remedy escaped guided review: " + finding.candidateId() + " " + sanction);
                if (outcome.customDuration()) {
                    assertTrue(temporaryMute, "Only ordinary mute durations may be interpolated");
                    coverage.changedDurations++;
                }
                if (temporaryMute) {
                    Duration duration = sanction.length().temporary().orElseThrow();
                    Duration largest = policy.steps().stream()
                            .flatMap(candidate -> candidate.sanctions().stream())
                            .filter(candidate -> candidate.type() == SanctionType.MUTE)
                            .flatMap(candidate -> candidate.length().temporary().stream())
                            .max(Duration::compareTo).orElseThrow();
                    assertTrue(duration.compareTo(largest) <= 0,
                            "Calculator exceeded largest configured temporary mute: " + policy.id());
                    if (previousDuration != null) {
                        assertTrue(duration.compareTo(previousDuration) >= 0,
                                "A stronger preference decreased the same policy mute: " + policy.id());
                    }
                    previousDuration = duration;
                }
                coverage.ordinary++;
            }
        }
    }
}
