package net.enthusia.staff.domain.policyv2;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public record RuleCondition(
        Map<String, Set<IncidentAttributeValue>> acceptedValues,
        HistoryWindow historyWindow
) {
    public RuleCondition {
        if (acceptedValues == null || historyWindow == null) {
            throw new IllegalArgumentException("rule conditions must be present");
        }
        Map<String, Set<IncidentAttributeValue>> copied = new HashMap<>();
        acceptedValues.forEach((id, values) -> {
            PolicyIds.require(id, "condition attribute id");
            if (values == null || values.isEmpty() || values.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("condition values must be non-empty");
            }
            copied.put(id, Set.copyOf(values));
        });
        acceptedValues = Map.copyOf(copied);
    }

    public boolean matches(IncidentFinding finding, double historyContribution) {
        if (!historyWindow.matches(historyContribution)) {
            return false;
        }
        return acceptedValues.entrySet().stream().allMatch(entry ->
                entry.getValue().contains(finding.attributes().get(entry.getKey())));
    }

    public boolean overlaps(RuleCondition other) {
        if (!historyWindow.overlaps(other.historyWindow)) {
            return false;
        }
        for (Map.Entry<String, Set<IncidentAttributeValue>> entry : acceptedValues.entrySet()) {
            Set<IncidentAttributeValue> otherValues = other.acceptedValues.get(entry.getKey());
            if (otherValues != null && entry.getValue().stream().noneMatch(otherValues::contains)) {
                return false;
            }
        }
        return true;
    }
}
