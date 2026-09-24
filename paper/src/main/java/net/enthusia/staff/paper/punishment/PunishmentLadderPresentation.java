package net.enthusia.staff.paper.punishment;

import java.util.List;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.escalation.PunishmentStep;

final class PunishmentLadderPresentation {
    private PunishmentLadderPresentation() {
    }

    static List<String> lines(PunishmentAssessment assessment) {
        if (assessment == null) {
            return List.of();
        }
        PunishmentStep selected = assessment.escalation().selectedStep();
        int total = assessment.policy().steps().size();
        String current = "Current ladder: step " + (selected.ordinal() + 1) + '/' + total
                + " — " + PunishmentRequestPresentation.sanctions(selected.sanctions());
        if (selected.ordinal() + 1 >= total) {
            return List.of(current, "Next offense: remains at the maximum configured step");
        }
        PunishmentStep next = assessment.policy().steps().get(selected.ordinal() + 1);
        String nextLine = "Next offense: step " + (next.ordinal() + 1) + '/' + total
                + " — " + PunishmentRequestPresentation.sanctions(next.sanctions());
        return List.of(current, nextLine);
    }
}
