package net.enthusia.staff.domain.ports;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertBacklog;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.auth.StaffRank;

public interface PunishmentRequestAlertStore {
    boolean insert(PunishmentRequestAlertIntent intent);

    List<PunishmentRequestAlertClaim> claimDirect(
            UUID recipientId, String owner, int limit, Duration lease, Instant now);

    List<PunishmentRequestAlertClaim> claimAudience(
            PunishmentRequestAlertAudience audience,
            UUID recipientId,
            StaffRank recipientRank,
            String owner,
            int limit,
            Duration lease,
            Instant now);

    boolean delivered(UUID alertId, String owner, Instant now);

    boolean failed(
            UUID alertId,
            String owner,
            String errorCode,
            Instant availableAt,
            Instant now,
            int maximumAttempts);

    int reclaimExpired(Instant now, int limit);

    PunishmentRequestAlertBacklog backlog(Instant now);

    int deleteDeliveredBefore(Instant cutoff, int limit);
}
