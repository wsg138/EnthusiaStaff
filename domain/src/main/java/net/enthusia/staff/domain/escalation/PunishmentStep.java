package net.enthusia.staff.domain.escalation;

import java.util.List;
import net.enthusia.staff.common.Checks;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public record PunishmentStep(int ordinal, String label, List<SanctionSpec> sanctions) {
    public PunishmentStep {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        label = Checks.nonBlank(label, "label", 80);
        sanctions = List.copyOf(Checks.nonEmpty(sanctions, "sanctions"));
    }
}
