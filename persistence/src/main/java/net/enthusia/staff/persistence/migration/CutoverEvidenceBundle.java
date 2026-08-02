package net.enthusia.staff.persistence.migration;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.migration.CutoverEvidence;

record CutoverEvidenceBundle(CutoverEvidence evidence, Optional<UUID> finalRunId) {
    CutoverEvidenceBundle {
        if (evidence == null || finalRunId == null) {
            throw new IllegalArgumentException("cutover evidence bundle fields must be present");
        }
        if (evidence.finalIncrementalImportComplete() != finalRunId.isPresent()) {
            throw new IllegalArgumentException("cutover evidence and final run linkage are inconsistent");
        }
    }
}
