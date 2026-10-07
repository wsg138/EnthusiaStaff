package net.enthusia.staff.domain.policyv2;

import java.util.List;

public record PolicyResolution(
        String policyVersion,
        String offenseId,
        String matchedRuleId,
        PolicyAction action,
        List<RemedySpec> remedies,
        HistoryAssessment history
) {
    public PolicyResolution {
        if (policyVersion == null || policyVersion.isBlank() || action == null || remedies == null || history == null) {
            throw new IllegalArgumentException("resolution metadata must be present");
        }
        remedies = List.copyOf(remedies);
    }

    public static PolicyResolution requiresReview(
            String policyVersion,
            String offenseId,
            String reasonCode,
            HistoryAssessment history
    ) {
        return new PolicyResolution(
                policyVersion,
                offenseId,
                null,
                new PolicyAction.RequiresReview(reasonCode),
                List.of(),
                history
        );
    }

    public boolean requiresReview() {
        return action instanceof PolicyAction.RequiresReview;
    }
}
