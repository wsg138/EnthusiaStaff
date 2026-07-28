package net.enthusia.staff.domain.freeze;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record FreezeRecord(
        UUID playerId,
        UUID frozenBy,
        String reason,
        Instant frozenAt,
        Optional<Instant> offlineExpiresAt,
        boolean keepActive,
        long revision
) {
    public FreezeRecord {
        if (playerId == null || frozenBy == null || reason == null || reason.isBlank() || frozenAt == null
                || offlineExpiresAt == null || revision < 0) {
            throw new IllegalArgumentException("freeze record fields must be present");
        }
    }
}
