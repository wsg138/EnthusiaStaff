package net.enthusia.staff.domain.migration;

import java.util.UUID;

public record FounderOverride(UUID actorId, String warningAcknowledgement, String reason) {
    public FounderOverride {
        if (actorId == null || warningAcknowledgement == null || warningAcknowledgement.isBlank()
                || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Founder override requires actor, warning acknowledgement and reason");
        }
        if (reason.length() > 2_000) {
            throw new IllegalArgumentException("Founder override reason is too long");
        }
    }
}
