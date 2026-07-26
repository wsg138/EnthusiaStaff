package net.enthusia.staff.domain.website;

import java.util.UUID;
import net.enthusia.staff.common.CaseId;

public record PunishmentCodeDisplay(
        UUID punishmentId,
        CaseId caseId,
        int generation,
        String punishmentType,
        String code
) {
    public PunishmentCodeDisplay {
        if (punishmentId == null || caseId == null || generation < 1
                || punishmentType == null || punishmentType.isBlank()
                || code == null || code.isBlank()) {
            throw new IllegalArgumentException("Punishment code display fields are invalid");
        }
    }
}
