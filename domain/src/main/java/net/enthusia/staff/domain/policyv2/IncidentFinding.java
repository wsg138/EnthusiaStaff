package net.enthusia.staff.domain.policyv2;

import java.util.Map;

public record IncidentFinding(String offenseId, Map<String, IncidentAttributeValue> attributes) {
    public IncidentFinding {
        offenseId = PolicyIds.require(offenseId, "offense id");
        if (attributes == null) {
            throw new IllegalArgumentException("attributes must be present");
        }
        attributes.forEach((key, value) -> {
            PolicyIds.require(key, "attribute id");
            if (value == null) {
                throw new IllegalArgumentException("attribute values must be present");
            }
        });
        attributes = Map.copyOf(attributes);
    }
}
