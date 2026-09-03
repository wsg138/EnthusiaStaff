package net.enthusia.staff.paper.inventory;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.inventory.InventoryRecoveryResult;
import net.enthusia.staff.domain.ports.InventoryRecoveryStore;

public final class InventoryRecoveryCoordinator {
    private final Clock clock;
    private final Supplier<InventoryRecoveryStore> store;
    private final AuthorizationPolicy authorization;

    public InventoryRecoveryCoordinator(
            Clock clock,
            Supplier<InventoryRecoveryStore> store,
            AuthorizationPolicy authorization
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.store = Objects.requireNonNull(store, "store");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public InventoryRecoveryResult recover(Actor actor, CaseId caseId) {
        if (actor == null || caseId == null
                || !authorization.permits(actor, ModerationAction.RESTORE_ASSETS)) {
            return result(
                    InventoryRecoveryResult.Status.UNAUTHORIZED,
                    "Only the Founder may authorize quarantined item recovery"
            );
        }
        InventoryRecoveryStore loaded = store.get();
        if (loaded == null) {
            return result(
                    InventoryRecoveryResult.Status.STORAGE_UNAVAILABLE,
                    "Inventory recovery storage is unavailable; nothing changed"
            );
        }
        return loaded.requeueCaseAssets(caseId, actor.id(), clock.instant());
    }

    private static InventoryRecoveryResult result(
            InventoryRecoveryResult.Status status,
            String detail
    ) {
        return new InventoryRecoveryResult(status, Optional.empty(), detail);
    }
}
