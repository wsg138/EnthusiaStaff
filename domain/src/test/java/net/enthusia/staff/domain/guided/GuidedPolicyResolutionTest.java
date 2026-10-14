package net.enthusia.staff.domain.guided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class GuidedPolicyResolutionTest {
    private static final String FLOOD_ID = "spam.message-flooding";
    private static SanctionSpec warn() {
        return new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
    }
    private static SanctionSpec mute(long minutes) {
        return new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofMinutes(minutes)));
    }
    private static final List<PunishmentStep> STEPS = List.of(
            new PunishmentStep(0, "Warning", List.of(warn())),
            new PunishmentStep(1, "Second warning", List.of(warn())),
            new PunishmentStep(2, "Short mute", List.of(mute(15))),
            new PunishmentStep(3, "Longer mute", List.of(mute(360))),
            new PunishmentStep(4, "Day mute", List.of(mute(1440)))
    );

    private static PunishmentAssessment assessment(int historyOrdinal, String id) {
        var policy = new ReasonPolicy(id, "spam", "Spam", 10, true, STEPS);
        var escalation = new EscalationDecision(historyOrdinal, historyOrdinal, 0,
                List.of(), STEPS.get(historyOrdinal));
        return new PunishmentAssessment("fixture.1", policy, escalation,
                STEPS.get(historyOrdinal).sanctions());
    }

    private static GuidedIncidentWizard.Finding finding(String extent, String warning) {
        return GuidedIncidentWizard.start("chat")
                .answer("medium", "public-chat").answer("conduct", "spam")
                .answer("extent", extent).answer("continued", warning).finding();
    }

    @Test
    void noWarningMeansNoDefaultPenalty() {
        var outcome = GuidedPolicyResolution.calculate(finding("few", "not-applicable"),
                assessment(0, "spam.low-level"), 0);
        assertEquals(List.of(warn()), outcome.sanctions());
        assertFalse(outcome.customDuration());
    }

    @Test
    void variableLengthRemainsBetweenConfiguredBounds() {
        var incident = finding("large-scale", "yes");
        var a = assessment(0, FLOOD_ID);
        Duration low = GuidedPolicyResolution.calculate(incident, a, -1)
                .sanctions().getFirst().length().temporary().orElseThrow();
        Duration mid = GuidedPolicyResolution.calculate(incident, a, 0)
                .sanctions().getFirst().length().temporary().orElseThrow();
        Duration high = GuidedPolicyResolution.calculate(incident, a, 1)
                .sanctions().getFirst().length().temporary().orElseThrow();
        assertTrue(low.compareTo(mid) < 0);
        assertTrue(mid.compareTo(high) < 0);
        assertTrue(low.compareTo(Duration.ofMinutes(15)) >= 0);
        assertTrue(high.compareTo(Duration.ofHours(6)) <= 0);
    }

    @Test
    void evaluatedCaseHistoryChangesDurationWithoutNewStaffQuestion() {
        var incident = finding("large-scale", "yes");
        var first = GuidedPolicyResolution.calculate(incident,
                assessment(0, FLOOD_ID), 0);
        var repeated = GuidedPolicyResolution.calculate(incident,
                assessment(1, FLOOD_ID), 0);
        Duration a = first.sanctions().getFirst().length().temporary().orElseThrow();
        Duration b = repeated.sanctions().getFirst().length().temporary().orElseThrow();
        assertTrue(b.compareTo(a) > 0);
    }

    private static PunishmentAssessment withFinalSanction(SanctionSpec finalSanction) {
        List<PunishmentStep> steps = List.of(
                new PunishmentStep(0, "Baseline warning", List.of(warn())),
                new PunishmentStep(1, "Elevated consequence", List.of(finalSanction)));
        ReasonPolicy policy = new ReasonPolicy(FLOOD_ID, "spam",
                "Flooding", 15, true, steps);
        EscalationDecision decision = new EscalationDecision(0, 0, 0, List.of(), steps.getFirst());
        return new PunishmentAssessment("fixture.1", policy, decision, List.of(warn()));
    }

    @Test
    void severityNeverAutomaticallyEscalatesToBanOrPermanentConsequence() {
        var incident = finding("large-scale", "yes");
        SanctionSpec temporaryBan = new SanctionSpec(SanctionType.BAN,
                SanctionLength.temporary(Duration.ofDays(7)));
        SanctionSpec permanentMute = new SanctionSpec(SanctionType.MUTE,
                SanctionLength.permanent());
        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(incident,
                        withFinalSanction(temporaryBan), 0));
        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(incident,
                        withFinalSanction(permanentMute), 0));
    }

    @Test
    void unsafeOrMismatchedOffenseIsRejected() {
        var spam = finding("few", "not-applicable");
        assertThrows(IllegalArgumentException.class, () -> GuidedPolicyResolution.calculate(
                spam, assessment(0, FLOOD_ID), 0));
        assertThrows(IllegalArgumentException.class, () -> GuidedPolicyResolution.calculate(
                spam, assessment(0, "spam.low-level"), 2));
    }
}
