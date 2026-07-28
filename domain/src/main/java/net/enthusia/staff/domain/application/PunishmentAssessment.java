package net.enthusia.staff.domain.application;

import java.util.List;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public record PunishmentAssessment(
        String configurationVersion,
        ReasonPolicy policy,
        EscalationDecision escalation,
        List<SanctionSpec> sanctions
) {
    public PunishmentAssessment {
        if (configurationVersion == null || configurationVersion.isBlank()
                || policy == null || escalation == null || sanctions == null || sanctions.isEmpty()) {
            throw new IllegalArgumentException("assessment fields must be present");
        }
        configurationVersion = configurationVersion.trim();
        sanctions = List.copyOf(sanctions);
    }
}
