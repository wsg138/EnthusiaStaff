package net.enthusia.staff.domain.alt;

import java.time.Instant;
import java.util.UUID;

public record AltRelationshipSummary(
        UUID otherPlayerId,
        AltRelationshipState state,
        double confidence,
        boolean lockedUntilReopened,
        Instant updatedAt
) {
    public AltRelationshipSummary {
        if (otherPlayerId == null || state == null || confidence < 0.0 || confidence > 1.0 || updatedAt == null) {
            throw new IllegalArgumentException("alt relationship summary fields are invalid");
        }
    }
}
