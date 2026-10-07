package net.enthusia.staff.paper.punishment.policyv2;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;

public record PolicyV2ManualDraft(
        UUID targetId,
        Instant incidentAt,
        Optional<String> categoryId,
        Optional<String> offenseId,
        Map<String, IncidentAttributeValue> attributes,
        Optional<String> policyGapSummary
) {
    private static final int MAX_POLICY_GAP_SUMMARY = 500;

    public PolicyV2ManualDraft {
        if (targetId == null || incidentAt == null || categoryId == null || offenseId == null
                || attributes == null || policyGapSummary == null) {
            throw new IllegalArgumentException("Policy v2 draft fields must be present");
        }
        categoryId = normalize(categoryId);
        offenseId = normalize(offenseId);
        policyGapSummary = policyGapSummary.map(PolicyV2ManualDraft::normalizeSummary);
        attributes = Map.copyOf(attributes);
    }

    public static PolicyV2ManualDraft start(UUID targetId, Instant incidentAt) {
        return new PolicyV2ManualDraft(
                targetId, incidentAt, Optional.empty(), Optional.empty(), Map.of(), Optional.empty()
        );
    }

    public PolicyV2ManualDraft selectCategory(PolicyV2Category category) {
        if (category == null) {
            throw new IllegalArgumentException("Policy v2 category must be present");
        }
        return new PolicyV2ManualDraft(
                targetId, incidentAt, Optional.of(category.id()), Optional.empty(), Map.of(), Optional.empty()
        );
    }

    public PolicyV2ManualDraft selectOffense(String selectedOffenseId) {
        if (selectedOffenseId == null || selectedOffenseId.isBlank()) {
            throw new IllegalArgumentException("Policy v2 offense must be present");
        }
        return new PolicyV2ManualDraft(
                targetId, incidentAt, categoryId, Optional.of(selectedOffenseId.trim()), Map.of(), Optional.empty()
        );
    }

    public PolicyV2ManualDraft answer(String attributeId, IncidentAttributeValue value) {
        if (attributeId == null || attributeId.isBlank() || value == null) {
            throw new IllegalArgumentException("Policy v2 answer must be present");
        }
        java.util.HashMap<String, IncidentAttributeValue> changed = new java.util.HashMap<>(attributes);
        changed.put(attributeId.trim(), value);
        return new PolicyV2ManualDraft(
                targetId, incidentAt, categoryId, offenseId, changed, policyGapSummary
        );
    }

    public PolicyV2ManualDraft describePolicyGap(String summary) {
        return new PolicyV2ManualDraft(
                targetId, incidentAt, Optional.of(PolicyV2Category.POLICY_GAP.id()),
                Optional.empty(), Map.of(), Optional.of(normalizeSummary(summary))
        );
    }

    public PolicyV2ManualDraft editOffense() {
        return new PolicyV2ManualDraft(
                targetId, incidentAt, categoryId, Optional.empty(), Map.of(), Optional.empty()
        );
    }

    public PolicyV2ManualDraft editCategory() {
        return start(targetId, incidentAt);
    }

    public boolean isPolicyGap() {
        return categoryId.filter(PolicyV2Category.POLICY_GAP.id()::equals).isPresent();
    }

    private static Optional<String> normalize(Optional<String> value) {
        return value.map(text -> {
            if (text.isBlank()) {
                throw new IllegalArgumentException("Policy v2 identifiers must not be blank");
            }
            return text.trim();
        });
    }

    private static String normalizeSummary(String summary) {
        if (summary == null || summary.isBlank() || summary.trim().length() > MAX_POLICY_GAP_SUMMARY) {
            throw new IllegalArgumentException("Policy gap summary must contain 1-500 characters");
        }
        return summary.trim();
    }
}
