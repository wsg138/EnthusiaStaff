package net.enthusia.staff.persistence.migration;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.migration.LegacyNetworkAddress;

public record LegacyNetworkObservation(
        String sourceTable,
        String externalId,
        UUID playerId,
        Optional<String> username,
        LegacyNetworkAddress networkAddress,
        Instant observedAt
) {
    public LegacyNetworkObservation {
        if (sourceTable == null || sourceTable.isBlank() || externalId == null || externalId.isBlank()
                || playerId == null || username == null || networkAddress == null || observedAt == null) {
            throw new IllegalArgumentException("legacy network observation fields must be present");
        }
    }
}
