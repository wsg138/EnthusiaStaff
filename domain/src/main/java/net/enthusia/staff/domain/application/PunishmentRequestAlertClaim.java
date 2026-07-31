package net.enthusia.staff.domain.application;

import java.time.Instant;
import java.util.UUID;

public record PunishmentRequestAlertClaim(
        UUID alertId,
        PunishmentRequestAlertIntent intent,
        int attemptCount,
        Instant leaseUntil
) {
    public PunishmentRequestAlertClaim {
        if (alertId == null || intent == null || attemptCount < 1 || leaseUntil == null) {
            throw new IllegalArgumentException("alert claim fields must be valid");
        }
    }
}
