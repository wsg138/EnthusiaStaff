package net.enthusia.staff.domain.migration;

import java.time.Instant;

public record CutoverEvidence(
        Instant shadowStartedAt,
        Instant assessedAt,
        boolean countsMatch,
        boolean checksumsMatch,
        boolean activeSanctionsMatch,
        boolean uuidMappingsMatch,
        boolean expirationsMatch,
        DecisionComparison loginDecisions,
        DecisionComparison muteDecisions,
        DecisionComparison ipBanDecisions,
        long unresolvedOperations,
        boolean writesFrozen,
        boolean finalIncrementalImportComplete
) {
    public CutoverEvidence {
        if (shadowStartedAt == null || assessedAt == null || loginDecisions == null
                || muteDecisions == null || ipBanDecisions == null || unresolvedOperations < 0) {
            throw new IllegalArgumentException("cutover evidence fields must be present");
        }
        if (assessedAt.isBefore(shadowStartedAt)) {
            throw new IllegalArgumentException("assessment cannot precede shadow start");
        }
    }
}
