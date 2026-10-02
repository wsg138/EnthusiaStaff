package net.enthusia.staff.paper.economy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;

/**
 * Supplies only the movement-lock contract needed by item confiscation when EnthusiaCurrency is absent.
 * Economy mutation methods deliberately remain unavailable.
 */
public final class InventoryOnlyCurrencyGateway implements CurrencyGateway {
    @Override
    public int apiVersion() {
        return 0;
    }

    @Override
    public boolean acquireMovementLock(UUID playerId, UUID operationId, Duration leaseDuration) {
        requireLockArguments(playerId, operationId, leaseDuration);
        return true;
    }

    @Override
    public boolean renewMovementLock(UUID playerId, UUID operationId, Duration leaseDuration) {
        requireLockArguments(playerId, operationId, leaseDuration);
        return true;
    }

    @Override
    public boolean releaseMovementLock(UUID playerId, UUID operationId) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(operationId, "operationId");
        return true;
    }

    @Override
    public boolean isMovementLocked(UUID playerId) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        return false;
    }

    @Override
    public CurrencyAccountState snapshot(Player player) {
        throw economyUnavailable();
    }

    @Override
    public CurrencyRemovalPlanState planRemoval(
            UUID operationId,
            CurrencyAccountState snapshot,
            long amount,
            List<CurrencyAssetSource> sourceOrder
    ) {
        throw economyUnavailable();
    }

    @Override
    public CompletionStage<CurrencyRemovalOutcome> applyRemoval(Player player, CurrencyRemovalPlanState plan) {
        throw economyUnavailable();
    }

    @Override
    public CompletionStage<CurrencyRestoreOutcome> restore(
            Player player,
            UUID operationId,
            CurrencyAccountState snapshot,
            String expectedCurrentChecksum
    ) {
        throw economyUnavailable();
    }

    private static void requireLockArguments(UUID playerId, UUID operationId, Duration leaseDuration) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(operationId, "operationId");
        Duration lease = java.util.Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }

    private static UnsupportedOperationException economyUnavailable() {
        return new UnsupportedOperationException("Economy operations require EnthusiaCurrency");
    }
}
