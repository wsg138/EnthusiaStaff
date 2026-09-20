package net.enthusia.staff.domain.application;

import java.util.List;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public record PunishmentExpectation(
        String configurationVersion,
        int stepOrdinal,
        String stepLabel,
        List<SanctionSpec> sanctions
) {
    public PunishmentExpectation {
        if (configurationVersion == null || configurationVersion.isBlank()
                || stepOrdinal < 0 || stepLabel == null || stepLabel.isBlank()
                || sanctions == null || sanctions.isEmpty()) {
            throw new IllegalArgumentException("punishment expectation fields must be present");
        }
        configurationVersion = configurationVersion.trim();
        stepLabel = stepLabel.trim();
        sanctions = List.copyOf(sanctions);
    }

    public static PunishmentExpectation from(PunishmentAssessment assessment) {
        if (assessment == null) {
            throw new IllegalArgumentException("assessment must be present");
        }
        return new PunishmentExpectation(
                assessment.configurationVersion(),
                assessment.escalation().selectedStep().ordinal(),
                assessment.escalation().selectedStep().label(),
                assessment.sanctions()
        );
    }

    public static PunishmentExpectation from(PunishmentPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("punishment plan must be present");
        }
        return new PunishmentExpectation(
                plan.configurationVersion(),
                plan.escalation().selectedStep().ordinal(),
                plan.escalation().selectedStep().label(),
                plan.sanctions()
        );
    }

    public boolean matches(PunishmentPlan plan) {
        return plan != null
                && configurationVersion.equals(plan.configurationVersion())
                && stepOrdinal == plan.escalation().selectedStep().ordinal()
                && stepLabel.equals(plan.escalation().selectedStep().label())
                && sanctions.equals(plan.sanctions());
    }

    public boolean matches(PunishmentAssessment assessment) {
        return assessment != null
                && configurationVersion.equals(assessment.configurationVersion())
                && stepOrdinal == assessment.escalation().selectedStep().ordinal()
                && stepLabel.equals(assessment.escalation().selectedStep().label())
                && sanctions.equals(assessment.sanctions());
    }
}
