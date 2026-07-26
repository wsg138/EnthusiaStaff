package net.enthusia.staff.domain.migration;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record LegacySanction(
        String sourceTable,
        String externalId,
        LegacySanctionType type,
        Optional<UUID> playerId,
        Optional<String> username,
        String originalReason,
        String originalStaffName,
        Instant issuedAt,
        Optional<Instant> expiresAt,
        boolean active
) {
    public LegacySanction {
        if (sourceTable == null || sourceTable.isBlank() || externalId == null || externalId.isBlank()
                || type == null || playerId == null || username == null || originalReason == null
                || originalStaffName == null || issuedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("legacy sanction fields must be present");
        }
        if (expiresAt.filter(value -> !value.isAfter(issuedAt)).isPresent()) {
            throw new IllegalArgumentException("legacy expiration must follow issue time");
        }
        if (type != LegacySanctionType.IP_BAN && playerId.isEmpty() && username.isEmpty()) {
            throw new IllegalArgumentException("non-IP legacy sanction needs a UUID or username");
        }
    }
}
