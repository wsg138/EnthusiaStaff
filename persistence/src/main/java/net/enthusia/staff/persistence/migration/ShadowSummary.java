package net.enthusia.staff.persistence.migration;

import net.enthusia.staff.domain.migration.DecisionComparison;

public record ShadowSummary(
        boolean countsMatch,
        boolean checksumsMatch,
        boolean activeSanctionsMatch,
        boolean uuidMappingsMatch,
        boolean expirationsMatch,
        DecisionComparison loginDecisions,
        DecisionComparison muteDecisions,
        DecisionComparison ipBanDecisions,
        long mismatchCount
) {
    public ShadowSummary {
        if (loginDecisions == null || muteDecisions == null || ipBanDecisions == null || mismatchCount < 0) {
            throw new IllegalArgumentException("shadow summary fields must be present");
        }
    }
}
