package net.enthusia.staff.domain.migration;

import java.time.Instant;
import java.util.List;

public record CutoverEvidence(
        Instant shadowStartedAt,
        Instant shadowEndedAt,
        Instant assessedAt,
        List<Instant> successfulShadowSummaries,
        boolean countsMatch,
        boolean checksumsMatch,
        boolean activeSanctionsMatch,
        boolean uuidMappingsMatch,
        boolean expirationsMatch,
        DecisionComparison loginDecisions,
        DecisionComparison muteDecisions,
        DecisionComparison ipBanDecisions,
        long unresolvedOperations,
        boolean migrationIdle,
        boolean writesFrozen,
        boolean finalIncrementalImportComplete
) {
    public CutoverEvidence {
        if (shadowStartedAt == null || shadowEndedAt == null || assessedAt == null
                || successfulShadowSummaries == null || successfulShadowSummaries.isEmpty()
                || loginDecisions == null || muteDecisions == null || ipBanDecisions == null
                || unresolvedOperations < 0) {
            throw new IllegalArgumentException("cutover evidence fields must be present");
        }
        successfulShadowSummaries = List.copyOf(successfulShadowSummaries);
        if (shadowEndedAt.isBefore(shadowStartedAt) || assessedAt.isBefore(shadowEndedAt)) {
            throw new IllegalArgumentException("shadow and assessment timestamps must be ordered");
        }
        Instant previous = null;
        for (Instant summary : successfulShadowSummaries) {
            if (summary == null || summary.isBefore(shadowStartedAt) || summary.isAfter(shadowEndedAt)
                    || previous != null && summary.isBefore(previous)) {
                throw new IllegalArgumentException("shadow summaries must be ordered within the shadow window");
            }
            previous = summary;
        }
    }
}
