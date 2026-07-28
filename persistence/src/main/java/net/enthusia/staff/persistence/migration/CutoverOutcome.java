package net.enthusia.staff.persistence.migration;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.migration.CutoverAssessment;

public record CutoverOutcome(CutoverAssessment assessment, boolean activated, Optional<UUID> cutoverId) {
    public CutoverOutcome {
        if (assessment == null || cutoverId == null || activated != cutoverId.isPresent()) {
            throw new IllegalArgumentException("cutover outcome fields are inconsistent");
        }
    }
}
