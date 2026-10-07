package net.enthusia.staff.domain.policyv2;

import java.util.List;

public record ResolutionRule(String id, RuleCondition condition, PolicyAction action, List<RemedySpec> remedies) {
    public ResolutionRule {
        id = PolicyIds.require(id, "resolution rule id");
        if (condition == null || action == null || remedies == null
                || remedies.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("resolution condition, action, and remedies must be present");
        }
        remedies = List.copyOf(remedies);
    }
}
