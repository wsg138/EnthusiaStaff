package net.enthusia.staff.domain.escalation;

import java.time.Instant;

public record PriorOffense(
        String family,
        int severity,
        int storedOrdinal,
        Instant endedAt,
        boolean contributes,
        boolean overturned,
        DecayEligibility decayEligibility
) {
    public PriorOffense {
        if (family == null || family.isBlank() || severity < 0 || storedOrdinal < 0 || endedAt == null
                || decayEligibility == null) {
            throw new IllegalArgumentException("invalid prior offense");
        }
    }

    /**
     * Compatibility constructor for history sources that do not yet carry the immutable policy value.
     * Such history is explicitly unknown and therefore does not decay.
     */
    public PriorOffense(
            String family,
            int severity,
            int storedOrdinal,
            Instant endedAt,
            boolean contributes,
            boolean overturned
    ) {
        this(family, severity, storedOrdinal, endedAt, contributes, overturned, DecayEligibility.UNKNOWN);
    }
}
