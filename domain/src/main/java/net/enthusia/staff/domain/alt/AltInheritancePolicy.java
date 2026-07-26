package net.enthusia.staff.domain.alt;

import java.time.Instant;
import java.util.Optional;

public final class AltInheritancePolicy {
    public boolean shouldInherit(
            AltRelationshipState relationshipState,
            boolean relationshipCreatedByObservation,
            Instant joiningFirstSeenAt,
            Optional<Instant> cutoverAt,
            boolean hasProtectedRelationshipHistory
    ) {
        if (relationshipState == null || joiningFirstSeenAt == null || cutoverAt == null) {
            throw new IllegalArgumentException("alt inheritance policy fields must be present");
        }
        if (relationshipState.inheritsAutomatically()) {
            return true;
        }
        if (relationshipState.preventsAutomaticInheritance() || hasProtectedRelationshipHistory
                || !relationshipCreatedByObservation || cutoverAt.isEmpty()) {
            return false;
        }
        return !joiningFirstSeenAt.isBefore(cutoverAt.orElseThrow());
    }
}
