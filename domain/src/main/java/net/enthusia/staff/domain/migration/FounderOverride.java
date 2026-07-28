package net.enthusia.staff.domain.migration;

import java.util.UUID;

public record FounderOverride(UUID actorId, String warningAcknowledgement, String reason) {
    public static final String REQUIRED_ACKNOWLEDGEMENT = "I_UNDERSTAND_CUTOVER_BLOCKERS";

    public FounderOverride {
        if (actorId == null || warningAcknowledgement == null || warningAcknowledgement.isBlank()
                || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Founder override requires actor, warning acknowledgement and reason");
        }
        if (reason.length() > 2_000) {
            throw new IllegalArgumentException("Founder override reason is too long");
        }
        if (!REQUIRED_ACKNOWLEDGEMENT.equals(warningAcknowledgement)) {
            throw new IllegalArgumentException("Founder override warning acknowledgement is invalid");
        }
        reason = reason.trim();
    }
}
