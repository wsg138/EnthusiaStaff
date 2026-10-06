package net.enthusia.staff.domain.policyv2;

import java.util.Map;

public record HistoryPolicy(Map<String, Double> relationshipWeights, DecayPolicy decayPolicy) {
    public HistoryPolicy {
        if (relationshipWeights == null || decayPolicy == null) {
            throw new IllegalArgumentException("history relationships and decay policy must be present");
        }
        relationshipWeights.forEach((id, weight) -> {
            PolicyIds.require(id, "related offense id");
            if (weight == null || !Double.isFinite(weight) || weight <= 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("relationship weights must be greater than zero and at most one");
            }
        });
        relationshipWeights = Map.copyOf(relationshipWeights);
    }

    public double weightFor(String offenseId) {
        return relationshipWeights.getOrDefault(offenseId, 0.0);
    }
}
