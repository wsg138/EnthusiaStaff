package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.security.ProtectedNetworkIdentity;
import net.enthusia.staff.domain.alt.AltRelationshipState;
import net.enthusia.staff.domain.alt.AltRelationshipSummary;
import net.enthusia.staff.domain.alt.NetworkIdentityObservationResult;
import net.enthusia.staff.domain.alt.NetworkIdentityRetentionResult;

public interface NetworkIdentityStore {
    NetworkIdentityObservationResult observeAndInherit(
            UUID joiningPlayerId,
            ProtectedNetworkIdentity identity,
            Instant observedAt,
            boolean suppressAutomatedEvidence
    );

    List<AltRelationshipSummary> relationships(UUID playerId);

    boolean setRelationship(
            UUID firstPlayerId,
            UUID secondPlayerId,
            AltRelationshipState state,
            UUID actorId,
            Instant changedAt,
            String reason
    );

    boolean reopen(UUID firstPlayerId, UUID secondPlayerId, UUID actorId, Instant changedAt, String reason);

    NetworkIdentityRetentionResult purgeExpired(Instant cutoff, int batchSize);
}
