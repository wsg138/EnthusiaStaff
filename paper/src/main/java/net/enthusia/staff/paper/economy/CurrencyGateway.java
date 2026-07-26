package net.enthusia.staff.paper.economy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;

public interface CurrencyGateway {
    int apiVersion();

    boolean acquireMovementLock(UUID playerId, UUID operationId, Duration leaseDuration);

    boolean renewMovementLock(UUID playerId, UUID operationId, Duration leaseDuration);

    boolean releaseMovementLock(UUID playerId, UUID operationId);

    boolean isMovementLocked(UUID playerId);

    CurrencyAccountState snapshot(Player player);

    CurrencyRemovalPlanState planRemoval(
            UUID operationId,
            CurrencyAccountState snapshot,
            long amount,
            List<CurrencyAssetSource> sourceOrder
    );

    CompletionStage<CurrencyRemovalOutcome> applyRemoval(
            Player player,
            CurrencyRemovalPlanState plan
    );

    CompletionStage<CurrencyRestoreOutcome> restore(
            Player player,
            UUID operationId,
            CurrencyAccountState snapshot,
            String expectedCurrentChecksum
    );
}
