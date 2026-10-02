package net.enthusia.staff.paper.staff;

import java.util.Objects;
import org.bukkit.event.block.Action;

/** Rules for the non-participating Helper staff-mode profile after profile resolution. */
final class HelperObserverPolicy {
    private HelperObserverPolicy() {
    }

    static boolean blocksAirItemUse(
            boolean helperProfile,
            Action action,
            boolean hasOrdinaryItem
    ) {
        Objects.requireNonNull(action, "action");
        return helperProfile
                && hasOrdinaryItem
                && action == Action.RIGHT_CLICK_AIR;
    }

    static boolean blocksProjectileCollision(boolean helperProfile, boolean hitEntity) {
        return helperProfile && hitEntity;
    }

    static int experienceAmount(boolean helperProfile, int proposedAmount) {
        return helperProfile ? 0 : proposedAmount;
    }
}
