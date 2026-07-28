package net.enthusia.staff.domain.escalation;

import java.time.Instant;

public record PriorOffense(
        String family,
        int severity,
        int storedOrdinal,
        Instant endedAt,
        boolean contributes,
        boolean overturned
) {
    public PriorOffense {
        if (family == null || family.isBlank() || severity < 0 || storedOrdinal < 0 || endedAt == null) {
            throw new IllegalArgumentException("invalid prior offense");
        }
    }
}
