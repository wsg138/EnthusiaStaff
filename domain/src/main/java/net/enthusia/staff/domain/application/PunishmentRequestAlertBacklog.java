package net.enthusia.staff.domain.application;

public record PunishmentRequestAlertBacklog(
        long activeIntents,
        long pendingDeliveries,
        long leasedDeliveries,
        long deliveredDeliveries,
        long cancelledDeliveries,
        long deadLetterDeliveries,
        long reclaimableLeases
) {
    public PunishmentRequestAlertBacklog {
        if (activeIntents < 0 || pendingDeliveries < 0 || leasedDeliveries < 0
                || deliveredDeliveries < 0 || cancelledDeliveries < 0
                || deadLetterDeliveries < 0 || reclaimableLeases < 0) {
            throw new IllegalArgumentException("alert backlog counts cannot be negative");
        }
    }

    /** Compatibility constructor retained for callers compiled against the B1.1 shape. */
    public PunishmentRequestAlertBacklog(
            long activeIntents,
            long pendingDeliveries,
            long leasedDeliveries,
            long deliveredDeliveries,
            long deadLetterDeliveries,
            long reclaimableLeases
    ) {
        this(activeIntents, pendingDeliveries, leasedDeliveries, deliveredDeliveries,
                0, deadLetterDeliveries, reclaimableLeases);
    }
}
