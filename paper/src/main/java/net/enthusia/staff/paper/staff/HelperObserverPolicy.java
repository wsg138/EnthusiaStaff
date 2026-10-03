package net.enthusia.staff.paper.staff;

import java.util.Objects;
import org.bukkit.event.block.Action;

/** Rules for the non-participating Helper staff-mode profile. */
final class HelperObserverPolicy {
    private HelperObserverPolicy() {
    }

    static boolean blocksAirItemUse(
            boolean helperObserverActive,
            Action action,
            boolean hasOrdinaryItem
    ) {
        Objects.requireNonNull(action, "action");
        return helperObserverActive
                && hasOrdinaryItem
                && action == Action.RIGHT_CLICK_AIR;
    }

    static boolean blocksProjectileCollision(boolean helperObserverActive, boolean hitEntity) {
        return helperObserverActive && hitEntity;
    }

    static int experienceAmount(boolean helperObserverActive, int proposedAmount) {
        return helperObserverActive ? 0 : proposedAmount;
    }
}
