package net.enthusia.staff.domain.policyv2;

import java.util.List;

public record HistoryAssessment(double totalContribution, List<Contribution> contributions) {
    public HistoryAssessment {
        if (!Double.isFinite(totalContribution) || totalContribution < 0.0 || contributions == null) {
            throw new IllegalArgumentException("history assessment must be finite and non-negative");
        }
        contributions = List.copyOf(contributions);
    }

    public static HistoryAssessment empty() {
        return new HistoryAssessment(0.0, List.of());
    }

    public record Contribution(
            String caseId,
            String offenseId,
            double relationshipWeight,
            double decayFactor,
            double halfLifeMultiplier,
            int priorRelatedCount,
            double contribution,
            double patternPersistence
    ) {
        public Contribution {
            if (!Double.isFinite(patternPersistence) || patternPersistence < 0.0) {
                throw new IllegalArgumentException("pattern persistence must be finite and non-negative");
            }
        }
    }
}
