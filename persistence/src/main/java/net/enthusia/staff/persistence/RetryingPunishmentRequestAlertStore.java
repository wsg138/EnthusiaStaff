package net.enthusia.staff.persistence;

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
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;

/**
 * Retries an empty claim once after the first transaction releases any parent-intent locks.
 *
 * <p>MariaDB can legitimately return an empty {@code FOR UPDATE SKIP LOCKED} result when another
 * recipient is concurrently claiming the same immutable audience intent. A second bounded
 * transaction preserves non-blocking workers while allowing independent recipient deliveries to
 * progress. Lease fencing and the unique delivery identity remain authoritative.</p>
 */
final class RetryingPunishmentRequestAlertStore implements PunishmentRequestAlertStore {
    private final PunishmentRequestAlertStore delegate;

    RetryingPunishmentRequestAlertStore(PunishmentRequestAlertStore delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must be present");
        }
        this.delegate = delegate;
    }

    @Override
    public boolean insert(PunishmentRequestAlertIntent intent) {
        return delegate.insert(intent);
    }

    @Override
    public List<PunishmentRequestAlertClaim> claimDirect(
            UUID recipientId,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) {
        return delegate.claimDirect(recipientId, owner, limit, lease, now);
    }

    @Override
    public List<PunishmentRequestAlertClaim> claimAudience(
            PunishmentRequestAlertAudience audience,
            UUID recipientId,
            StaffRank recipientRank,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) {
        List<PunishmentRequestAlertClaim> claims = delegate.claimAudience(
                audience, recipientId, recipientRank, owner, limit, lease, now);
        return claims.isEmpty()
                ? delegate.claimAudience(
                        audience, recipientId, recipientRank, owner, limit, lease, now)
                : claims;
    }

    @Override
    public boolean delivered(PunishmentRequestAlertDeliveryId deliveryId, String owner, Instant now) {
        return delegate.delivered(deliveryId, owner, now);
    }

    @Override
    public boolean failed(
            PunishmentRequestAlertDeliveryId deliveryId,
            String owner,
            String errorCode,
            Instant availableAt,
            Instant now,
            int maximumAttempts
    ) {
        return delegate.failed(deliveryId, owner, errorCode, availableAt, now, maximumAttempts);
    }

    @Override
    public boolean cancel(
            PunishmentRequestAlertDeliveryId deliveryId,
            String owner,
            String reason,
            Instant now
    ) {
        return delegate.cancel(deliveryId, owner, reason, now);
    }

    @Override
    public boolean requeueDeadLetter(
            PunishmentRequestAlertDeliveryId deliveryId,
            Instant availableAt,
            String reason,
            Instant now
    ) {
        return delegate.requeueDeadLetter(deliveryId, availableAt, reason, now);
    }

    @Override
    public boolean resolveDeadLetter(
            PunishmentRequestAlertDeliveryId deliveryId,
            String reason,
            Instant now
    ) {
        return delegate.resolveDeadLetter(deliveryId, reason, now);
    }

    @Override
    public boolean closeIntent(UUID alertId, String reason, Instant now) {
        return delegate.closeIntent(alertId, reason, now);
    }

    @Override
    public int expireIntents(Instant now, int limit) {
        return delegate.expireIntents(now, limit);
    }

    @Override
    public int reclaimExpiredDeliveries(Instant now, int limit) {
        return delegate.reclaimExpiredDeliveries(now, limit);
    }

    @Override
    public PunishmentRequestAlertBacklog backlog(Instant now) {
        return delegate.backlog(now);
    }

    @Override
    public int deleteTerminalIntentsBefore(Instant cutoff, int limit) {
        return delegate.deleteTerminalIntentsBefore(cutoff, limit);
    }
}
