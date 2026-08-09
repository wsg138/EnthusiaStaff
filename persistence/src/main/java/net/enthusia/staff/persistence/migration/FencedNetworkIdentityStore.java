package net.enthusia.staff.persistence.migration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.security.ProtectedNetworkIdentity;
import net.enthusia.staff.domain.alt.AltRelationshipState;
import net.enthusia.staff.domain.alt.AltRelationshipSummary;
import net.enthusia.staff.domain.alt.NetworkIdentityObservationResult;
import net.enthusia.staff.domain.alt.NetworkIdentityRetentionResult;
import net.enthusia.staff.domain.ports.NetworkIdentityStore;

public final class FencedNetworkIdentityStore implements NetworkIdentityStore {
    private final NetworkIdentityStore delegate;
    private final AuthoritativeWriteFence fence;

    public FencedNetworkIdentityStore(DataSource dataSource, NetworkIdentityStore delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("network identity store delegate must be present");
        }
        this.delegate = delegate;
        this.fence = new AuthoritativeWriteFence(dataSource);
    }

    @Override
    public NetworkIdentityObservationResult observeAndInherit(
            UUID joiningPlayerId,
            ProtectedNetworkIdentity identity,
            Instant observedAt,
            boolean suppressAutomatedEvidence
    ) {
        return fence.execute(
                () -> delegate.observeAndInherit(
                        joiningPlayerId,
                        identity,
                        observedAt,
                        suppressAutomatedEvidence
                ),
                () -> delegate.observeAndInherit(
                        joiningPlayerId,
                        identity,
                        observedAt,
                        true
                )
        );
    }

    @Override
    public List<AltRelationshipSummary> relationships(UUID playerId) {
        return delegate.relationships(playerId);
    }

    @Override
    public boolean setRelationship(
            UUID firstPlayerId,
            UUID secondPlayerId,
            AltRelationshipState state,
            UUID actorId,
            Instant changedAt,
            String reason
    ) {
        return fence.execute(
                () -> delegate.setRelationship(
                        firstPlayerId,
                        secondPlayerId,
                        state,
                        actorId,
                        changedAt,
                        reason
                ),
                () -> false
        );
    }

    @Override
    public boolean reopen(
            UUID firstPlayerId,
            UUID secondPlayerId,
            UUID actorId,
            Instant changedAt,
            String reason
    ) {
        return fence.execute(
                () -> delegate.reopen(firstPlayerId, secondPlayerId, actorId, changedAt, reason),
                () -> false
        );
    }

    @Override
    public NetworkIdentityRetentionResult purgeExpired(Instant cutoff, int batchSize) {
        return fence.execute(
                () -> delegate.purgeExpired(cutoff, batchSize),
                () -> new NetworkIdentityRetentionResult(0, 0)
        );
    }
}
