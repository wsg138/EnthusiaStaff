package net.enthusia.staff.domain.ports;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertBacklog;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertDeliveryId;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.auth.StaffRank;

public interface PunishmentRequestAlertStore {
    boolean insert(PunishmentRequestAlertIntent intent);

    List<PunishmentRequestAlertClaim> claimDirect(
            UUID recipientId,
            String owner,
            int limit,
            Duration lease,
            Instant now
    );

    List<PunishmentRequestAlertClaim> claimAudience(
            PunishmentRequestAlertAudience audience,
            UUID recipientId,
            StaffRank recipientRank,
            String owner,
            int limit,
            Duration lease,
            Instant now
    );

    /**
     * Cancels a bounded number of pending audience deliveries that the recipient is no longer
     * authorized to receive. Direct-recipient deliveries and active fenced leases are unaffected.
     */
    default int reconcileRecipientAuthorization(
            UUID recipientId,
            StaffRank currentRank,
            Instant now,
            int limit
    ) {
        return 0;
    }

    boolean delivered(PunishmentRequestAlertDeliveryId deliveryId, String owner, Instant now);

    boolean failed(
            PunishmentRequestAlertDeliveryId deliveryId,
            String owner,
            String errorCode,
            Instant availableAt,
            Instant now,
            int maximumAttempts
    );

    /** Cancels a currently valid lease after a final eligibility/presentation recheck. */
    boolean cancel(
            PunishmentRequestAlertDeliveryId deliveryId,
            String owner,
            String reason,
            Instant now
    );

    /** Explicit operational recovery for unresolved dead-letter work. */
    boolean requeueDeadLetter(
            PunishmentRequestAlertDeliveryId deliveryId,
            Instant availableAt,
            String reason,
            Instant now
    );

    /** Explicitly resolves dead-letter work that an operator has decided not to retry. */
    boolean resolveDeadLetter(
            PunishmentRequestAlertDeliveryId deliveryId,
            String reason,
            Instant now
    );

    boolean closeIntent(UUID alertId, String reason, Instant now);

    int expireIntents(Instant now, int limit);

    int reclaimExpiredDeliveries(Instant now, int limit);

    PunishmentRequestAlertBacklog backlog(Instant now);

    int deleteTerminalIntentsBefore(Instant cutoff, int limit);
}
