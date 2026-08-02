package net.enthusia.staff.domain.application;

import java.util.UUID;

public record PunishmentRequestAlertDeliveryId(
        UUID alertId,
        UUID recipientId
) {
    public PunishmentRequestAlertDeliveryId {
        if (alertId == null || recipientId == null) {
            throw new IllegalArgumentException("alert delivery identity fields must be present");
        }
    }
}
