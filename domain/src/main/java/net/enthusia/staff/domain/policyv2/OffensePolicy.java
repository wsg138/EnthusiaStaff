package net.enthusia.staff.domain.policyv2;

import java.util.List;

public record OffensePolicy(
        String id,
        String displayName,
        String navigationGroupId,
        List<IncidentAttributeDefinition> attributes,
        HistoryPolicy historyPolicy,
        List<ResolutionRule> rules
) {
    public OffensePolicy {
        id = PolicyIds.require(id, "offense id");
        navigationGroupId = PolicyIds.require(navigationGroupId, "navigation group id");
        if (displayName == null || displayName.isBlank() || attributes == null || historyPolicy == null
                || rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("offense metadata, history policy, and rules must be present");
        }
        displayName = displayName.trim();
        attributes = List.copyOf(attributes);
        rules = List.copyOf(rules);
    }
}
