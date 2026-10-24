package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
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
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;
import org.junit.jupiter.api.Test;

/**
 * Regression snapshot of the UNAPPROVED prototype against repository defaults.
 * This is not owner-approved policy and is not a simulation of the live custom YAML.
 */
class GuidedPolicyCalibrationMatrixTest {
    private record Scenario(String name, GuidedIncidentWizard.Finding finding,
                            int historyOrdinal, String expected) { }

    private static GuidedIncidentWizard.Finding spam(String medium, String extent, String warning) {
        return GuidedIncidentWizard.start("chat")
                .answer("medium", medium).answer("conduct", "spam")
                .answer("extent", extent).answer("continued", warning).finding();
    }

    private static GuidedIncidentWizard.Finding targetedHarassment(String extent, String warning) {
        return GuidedIncidentWizard.start("chat")
                .answer("medium", "public-chat").answer("conduct", "harassment")
                .answer("detail", "verbal").answer("targeting", "targeted")
                .answer("extent", extent).answer("continued", warning).finding();
    }

    private static GuidedIncidentWizard.Finding xray() {
        return GuidedIncidentWizard.start("gameplay")
                .answer("medium", "in-game").answer("conduct", "cheating")
                .answer("detail", "xray").answer("extent", "few")
                .answer("continued", "not-applicable").finding();
    }

    private static String observedOutcome(GuidedIncidentWizard.Finding finding,
                                           int historyOrdinal, Map<String, ReasonPolicy> byId,
                                           String version) {
        ReasonPolicy policy = byId.get(finding.candidateId());
        assertNotNull(policy, "Missing configured candidate reason " + finding.candidateId());
        assertTrue(historyOrdinal >= 0 && historyOrdinal < policy.steps().size());
        if (finding.needsReview()) {
            return "REVIEW";
        }
        var selected = policy.steps().get(historyOrdinal);
        var history = new EscalationDecision(historyOrdinal, historyOrdinal, 0,
                List.of(), selected);
        var assessment = new PunishmentAssessment(version, policy, history, selected.sanctions());
        try {
            List<SanctionSpec> sanctions = GuidedPolicyResolution
                    .calculate(finding, assessment, 0).sanctions();
            assertEquals(1, sanctions.size(), "Ordinary guided mode cannot create mixed sanctions");
            SanctionSpec sanction = sanctions.getFirst();
            return sanction.type() + " " + sanction.length().temporary()
                    .map(Object::toString)
                    .orElse(sanction.length().kind().name());
        } catch (IllegalArgumentException manualReview) {
            return "REVIEW";
        }
    }

    @Test
    void historicalPrototypeMatchesBundledPolicyScenarioSnapshot() {
        ReasonPolicyConfigurationLoader.LoadedPolicies loaded;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("reason-policies.yml")) {
            assertNotNull(in);
            loaded = new ReasonPolicyConfigurationLoader().load(in, "reason-policies.yml");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        assertEquals("2026-10-10.1", loaded.version(),
                "A changed default policy requires a fresh explicit severity review");
        Map<String, ReasonPolicy> byId = loaded.policies().stream()
                .collect(Collectors.toMap(ReasonPolicy::id, Function.identity()));

        List<Scenario> matrix = List.of(
                new Scenario("Isolated chat spam, no warning",
                        spam("public-chat", "few", "not-applicable"), 0, "WARNING INSTANT"),
                new Scenario("Isolated chat spam, stopped after warning",
                        spam("public-chat", "few", "no"), 0, "WARNING INSTANT"),
                new Scenario("Isolated chat spam, ignored warning",
                        spam("public-chat", "few", "yes"), 0, "WARNING INSTANT"),
                new Scenario("Sustained flooding, no warning",
                        spam("public-chat", "sustained", "not-applicable"), 0, "MUTE PT15H"),
                new Scenario("Sustained flooding, ignored warning",
                        spam("public-chat", "sustained", "yes"), 0, "MUTE PT15H"),
                new Scenario("Large-scale flooding, no warning",
                        spam("public-chat", "large-scale", "not-applicable"), 0, "MUTE PT15H"),
                new Scenario("Large-scale flooding, ignored warning",
                        spam("public-chat", "large-scale", "yes"), 0, "MUTE PT48H"),
                new Scenario("Repeated flooding, history step one",
                        spam("public-chat", "sustained", "not-applicable"), 1, "MUTE PT48H"),
                new Scenario("Private-message spam, sustained",
                        spam("direct-message", "sustained", "not-applicable"), 0, "MUTE PT15H"),
                new Scenario("Targeted verbal harassment, first",
                        targetedHarassment("few", "not-applicable"), 0, "MUTE PT48H"),
                new Scenario("Targeted harassment, repeated",
                        targetedHarassment("sustained", "not-applicable"), 0, "MUTE PT48H"),
                new Scenario("Targeted harassment, ignored warning",
                        targetedHarassment("sustained", "yes"), 0, "MUTE PT120H"),
                new Scenario("Voice spam (enforcement unverified)",
                        spam("voice", "sustained", "not-applicable"), 0, "REVIEW"),
                new Scenario("X-ray/ESP (network ban plus confiscation)",
                        xray(), 0, "REVIEW")
        );
        for (Scenario row : matrix) {
            assertEquals(row.expected(), observedOutcome(row.finding(), row.historyOrdinal(),
                    byId, loaded.version()), row.name());
        }
    }
}
