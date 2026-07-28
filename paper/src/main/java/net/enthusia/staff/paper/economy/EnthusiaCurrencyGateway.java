package net.enthusia.staff.paper.economy;

import com.enthusia.enthusiacurrency.api.moderation.CurrencyAccountSnapshot;
import com.enthusia.enthusiacurrency.api.moderation.CurrencyModerationApi;
import com.enthusia.enthusiacurrency.api.moderation.CurrencyRemovalPlan;
import com.enthusia.enthusiacurrency.api.moderation.CurrencyRemovalResult;
import com.enthusia.enthusiacurrency.api.moderation.CurrencyRestoreResult;
import com.enthusia.enthusiacurrency.api.moderation.CurrencySource;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;

public final class EnthusiaCurrencyGateway implements CurrencyGateway {
    private final CurrencyModerationApi service;

    private EnthusiaCurrencyGateway(CurrencyModerationApi service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public static Discovery discover(ServicesManager services) {
        Objects.requireNonNull(services, "services");
        try {
            CurrencyModerationApi loaded = services.load(CurrencyModerationApi.class);
            if (loaded == null) {
                return new Discovery(
                        Optional.empty(),
                        "EnthusiaCurrency did not register its moderation service"
                );
            }
            if (loaded.apiVersion() != CurrencyModerationApi.API_VERSION) {
                return new Discovery(
                        Optional.empty(),
                        "EnthusiaCurrency moderation API version " + loaded.apiVersion()
                                + " is incompatible with required version "
                                + CurrencyModerationApi.API_VERSION
                );
            }
            return new Discovery(Optional.of(new EnthusiaCurrencyGateway(loaded)), "");
        } catch (LinkageError | RuntimeException exception) {
            return new Discovery(
                    Optional.empty(),
                    "EnthusiaCurrency moderation API could not be linked: "
                            + exception.getClass().getSimpleName()
            );
        }
    }

    @Override
    public int apiVersion() {
        return service.apiVersion();
    }

    @Override
    public boolean acquireMovementLock(
            UUID playerId,
            UUID operationId,
            Duration leaseDuration
    ) {
        return service.acquireMovementLock(playerId, operationId, leaseDuration);
    }

    @Override
    public boolean renewMovementLock(
            UUID playerId,
            UUID operationId,
            Duration leaseDuration
    ) {
        return service.renewMovementLock(playerId, operationId, leaseDuration);
    }

    @Override
    public boolean releaseMovementLock(UUID playerId, UUID operationId) {
        return service.releaseMovementLock(playerId, operationId);
    }

    @Override
    public boolean isMovementLocked(UUID playerId) {
        return service.isMovementLocked(playerId);
    }

    @Override
    public CurrencyAccountState snapshot(Player player) {
        return fromApi(service.snapshot(player));
    }

    @Override
    public CurrencyRemovalPlanState planRemoval(
            UUID operationId,
            CurrencyAccountState snapshot,
            long amount,
            List<CurrencyAssetSource> sourceOrder
    ) {
        CurrencyRemovalPlan plan = service.planRemoval(
                operationId,
                toApi(snapshot),
                amount,
                sourceOrder.stream().map(EnthusiaCurrencyGateway::toApi).toList()
        );
        return fromApi(plan);
    }

    @Override
    public CompletionStage<CurrencyRemovalOutcome> applyRemoval(
            Player player,
            CurrencyRemovalPlanState plan
    ) {
        return service.applyRemoval(player, toApi(plan)).thenApply(EnthusiaCurrencyGateway::fromApi);
    }

    @Override
    public CompletionStage<CurrencyRestoreOutcome> restore(
            Player player,
            UUID operationId,
            CurrencyAccountState snapshot,
            String expectedCurrentChecksum
    ) {
        return service.restore(
                player,
                operationId,
                toApi(snapshot),
                expectedCurrentChecksum
        ).thenApply(EnthusiaCurrencyGateway::fromApi);
    }

    private static CurrencyAccountState fromApi(CurrencyAccountSnapshot snapshot) {
        return new CurrencyAccountState(
                snapshot.playerId(),
                snapshot.bankBalance(),
                snapshot.bankRevision(),
                snapshot.inventory(),
                snapshot.enderChest(),
                snapshot.inventoryValue(),
                snapshot.enderChestValue(),
                snapshot.authoritativeTotal(),
                snapshot.checksum()
        );
    }

    private static CurrencyAccountSnapshot toApi(CurrencyAccountState snapshot) {
        return new CurrencyAccountSnapshot(
                snapshot.playerId(),
                snapshot.bankBalance(),
                snapshot.bankRevision(),
                snapshot.inventory(),
                snapshot.enderChest(),
                snapshot.inventoryValue(),
                snapshot.enderChestValue(),
                snapshot.authoritativeTotal(),
                snapshot.checksum()
        );
    }

    private static CurrencyRemovalPlanState fromApi(CurrencyRemovalPlan plan) {
        return new CurrencyRemovalPlanState(
                plan.operationId(),
                plan.playerId(),
                plan.amount(),
                fromApi(plan.before()),
                plan.replacementBankBalance(),
                plan.replacementInventory(),
                plan.replacementEnderChest(),
                plan.expectedFinalTotal(),
                plan.replacementChecksum(),
                plan.sourceOrder().stream().map(EnthusiaCurrencyGateway::fromApi).toList()
        );
    }

    private static CurrencyRemovalPlan toApi(CurrencyRemovalPlanState plan) {
        return new CurrencyRemovalPlan(
                plan.operationId(),
                plan.playerId(),
                plan.amount(),
                toApi(plan.before()),
                plan.replacementBankBalance(),
                plan.replacementInventory(),
                plan.replacementEnderChest(),
                plan.expectedFinalTotal(),
                plan.replacementChecksum(),
                plan.sourceOrder().stream().map(EnthusiaCurrencyGateway::toApi).toList()
        );
    }

    private static CurrencyRemovalOutcome fromApi(CurrencyRemovalResult result) {
        return new CurrencyRemovalOutcome(
                CurrencyRemovalOutcome.Status.valueOf(result.status().name()),
                result.amountRemoved(),
                result.finalTotal(),
                result.accountState().map(EnthusiaCurrencyGateway::fromApi),
                result.detail()
        );
    }

    private static CurrencyRestoreOutcome fromApi(CurrencyRestoreResult result) {
        return new CurrencyRestoreOutcome(
                CurrencyRestoreOutcome.Status.valueOf(result.status().name()),
                result.accountState().map(EnthusiaCurrencyGateway::fromApi),
                result.detail()
        );
    }

    private static CurrencySource toApi(CurrencyAssetSource source) {
        return CurrencySource.valueOf(source.name());
    }

    private static CurrencyAssetSource fromApi(CurrencySource source) {
        return CurrencyAssetSource.valueOf(source.name());
    }

    public record Discovery(Optional<CurrencyGateway> gateway, String issue) {
        public Discovery {
            gateway = Objects.requireNonNull(gateway, "gateway");
            issue = Objects.requireNonNull(issue, "issue");
            if (gateway.isPresent() == !issue.isEmpty()) {
                throw new IllegalArgumentException("successful discovery cannot contain an issue");
            }
        }
    }
}
