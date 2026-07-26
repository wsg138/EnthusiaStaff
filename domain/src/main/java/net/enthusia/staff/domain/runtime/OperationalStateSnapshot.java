package net.enthusia.staff.domain.runtime;

import java.time.Instant;
import net.enthusia.staff.domain.OperationalMode;

public record OperationalStateSnapshot(
        OperationalMode mode,
        long revision,
        String reason,
        Instant updatedAt
) {
    public OperationalStateSnapshot {
        if (mode == null || revision < 0 || reason == null || reason.isBlank() || updatedAt == null) {
            throw new IllegalArgumentException("operational state fields must be present");
        }
    }
}
