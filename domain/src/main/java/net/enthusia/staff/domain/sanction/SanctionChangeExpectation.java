package net.enthusia.staff.domain.sanction;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record SanctionChangeExpectation(
        long caseRevision,
        Map<UUID, Long> sanctionRevisions,
        Optional<Boolean> escalationContributes,
        Optional<UUID> openOverturnRequestId
) {
    public SanctionChangeExpectation {
        if (caseRevision < 0 || sanctionRevisions == null
                || escalationContributes == null || openOverturnRequestId == null) {
            throw new IllegalArgumentException("sanction change expectation fields must be present");
        }
        sanctionRevisions = Map.copyOf(sanctionRevisions);
        if (sanctionRevisions.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("sanction revisions must be non-negative");
        }
    }
}
