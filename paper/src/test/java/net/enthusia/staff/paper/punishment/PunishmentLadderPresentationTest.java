package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class PunishmentLadderPresentationTest {
    @Test
    void showsSelectedAndNextConfiguredStep() {
        List<PunishmentStep> steps = steps();
        PunishmentAssessment assessment = assessment(steps, 1);

        assertEquals(List.of(
                "Current ladder: step 2/3 — mute 30d",
                "Next offense: step 3/3 — mute 45d"
        ), PunishmentLadderPresentation.lines(assessment));
    }

    @Test
    void maxStepDoesNotInventAnotherPunishment() {
        List<PunishmentStep> steps = steps();
        PunishmentAssessment assessment = assessment(steps, 2);

        assertEquals(List.of(
                "Current ladder: step 3/3 — mute 45d",
                "Next offense: remains at the maximum configured step"
        ), PunishmentLadderPresentation.lines(assessment));
    }

    private static PunishmentAssessment assessment(List<PunishmentStep> steps, int selected) {
        ReasonPolicy policy = new ReasonPolicy("spam.chat", "spam", "Chat spam", 40, true, steps);
        PunishmentStep step = steps.get(selected);
        EscalationDecision decision = new EscalationDecision(selected, selected, 0, List.of(), step);
        return new PunishmentAssessment("v1", policy, decision, step.sanctions());
    }

    private static List<PunishmentStep> steps() {
        return List.of(
                step(0, "15 day mute", 15),
                step(1, "30 day mute", 30),
                step(2, "45 day mute", 45)
        );
    }

    private static PunishmentStep step(int ordinal, String label, long days) {
        return new PunishmentStep(
                ordinal,
                label,
                List.of(new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofDays(days))))
        );
    }
}
