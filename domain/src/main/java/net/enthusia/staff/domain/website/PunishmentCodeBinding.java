package net.enthusia.staff.domain.website;

import java.util.UUID;
import net.enthusia.staff.common.CaseId;

public record PunishmentCodeBinding(
        UUID punishmentId,
        CaseId caseId,
        int codeGeneration,
        String punishmentType,
        String boundUsername,
        boolean eligible,
        String eligibilityState
) {
    public PunishmentCodeBinding {
        if (punishmentId == null || caseId == null || codeGeneration < 1
                || punishmentType == null || punishmentType.isBlank()
                || boundUsername == null || !boundUsername.matches("[A-Za-z0-9_]{3,16}")
                || eligibilityState == null || eligibilityState.isBlank()) {
            throw new IllegalArgumentException("Punishment binding fields are invalid");
        }
    }
}
