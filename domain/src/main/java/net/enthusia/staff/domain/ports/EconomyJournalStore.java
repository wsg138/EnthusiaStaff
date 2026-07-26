package net.enthusia.staff.domain.ports;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.economy.EconomyJournalResult;
import net.enthusia.staff.domain.economy.EconomyOperation;
import net.enthusia.staff.domain.economy.EconomyPreparation;
import net.enthusia.staff.domain.economy.EconomyPrepareRequest;
import net.enthusia.staff.domain.economy.EconomyTerminalUpdate;
import net.enthusia.staff.domain.economy.EconomyValidatedPlan;

public interface EconomyJournalStore {
    EconomyPreparation prepare(EconomyPrepareRequest request, Duration leaseDuration, Instant now);

    Optional<EconomyOperation> renewLease(
            UUID operationId,
            long fencingToken,
            Duration leaseDuration,
            Instant now
    );

    Optional<EconomyOperation> reclaim(
            UUID operationId,
            Duration leaseDuration,
            Instant now
    );

    EconomyJournalResult saveValidatedPlan(
            UUID operationId,
            long fencingToken,
            EconomyValidatedPlan plan,
            Instant now
    );

    EconomyJournalResult markApplying(UUID operationId, long fencingToken, Instant now);

    EconomyJournalResult finish(
            UUID operationId,
            long fencingToken,
            EconomyTerminalUpdate update,
            Instant now
    );

    EconomyJournalResult release(UUID operationId, long fencingToken, Instant now);

    Optional<EconomyOperation> find(UUID operationId);

    List<EconomyOperation> recoverableForTarget(
            UUID targetId,
            String owningServerId,
            int limit
    );

    Optional<String> lockedOwningServer(UUID targetId);
}
