package net.enthusia.staff.domain.application;

import java.time.Instant;

public record PunishmentRequestAlertClaim(
        PunishmentRequestAlertDeliveryId deliveryId,
        PunishmentRequestAlertIntent intent,
        int attemptCount,
        Instant leaseUntil
) {
    public PunishmentRequestAlertClaim {
        if (deliveryId == null || intent == null || attemptCount < 1 || leaseUntil == null) {
            throw new IllegalArgumentException("alert claim fields must be valid");
        }
        if (!deliveryId.alertId().equals(intent.alertId())) {
            throw new IllegalArgumentException("alert claim delivery must belong to the intent");
        }
    }
}
