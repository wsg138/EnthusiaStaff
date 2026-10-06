package net.enthusia.staff.domain.policyv2;

import java.time.Instant;
import java.util.Optional;

public record BehavioralHistoryEntry(
        String caseId,
        Instant occurredAt,
        String originalOffenseId,
        String effectiveOffenseId,
        FindingState findingState
) {
    public enum FindingState { CONFIRMED, RECLASSIFIED, OVERTURNED }

    public BehavioralHistoryEntry {
        if (caseId == null || caseId.isBlank() || occurredAt == null || findingState == null) {
            throw new IllegalArgumentException("history identity, time, and finding state must be present");
        }
        caseId = caseId.trim();
        originalOffenseId = PolicyIds.require(originalOffenseId, "original offense id");
        effectiveOffenseId = normalizeEffectiveFinding(originalOffenseId, effectiveOffenseId, findingState);
    }

    public Optional<String> contributingOffenseId() {
        return findingState == FindingState.OVERTURNED ? Optional.empty() : Optional.of(effectiveOffenseId);
    }

    private static String normalizeEffectiveFinding(String original, String effective, FindingState state) {
        if (state == FindingState.OVERTURNED) {
            if (effective != null) {
                throw new IllegalArgumentException("overturned findings cannot retain an effective offense");
            }
            return null;
        }
        effective = PolicyIds.require(effective, "effective offense id");
        boolean reclassified = !original.equals(effective);
        if ((state == FindingState.RECLASSIFIED) != reclassified) {
            throw new IllegalArgumentException("finding state must match original/effective offense identity");
        }
        return effective;
    }
}
