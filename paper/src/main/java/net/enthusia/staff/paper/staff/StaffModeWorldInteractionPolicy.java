package net.enthusia.staff.paper.staff;

import java.util.Objects;
import org.bukkit.event.block.Action;

final class StaffModeWorldInteractionPolicy {
    private StaffModeWorldInteractionPolicy() {
    }

    static boolean blocksMutation(boolean activeStaffMode) {
        return activeStaffMode;
    }

    static boolean blocksBlockInteraction(boolean activeStaffMode, Action action) {
        Objects.requireNonNull(action, "action");
        if (!activeStaffMode) {
            return false;
        }
        return action != Action.LEFT_CLICK_AIR && action != Action.RIGHT_CLICK_AIR;
    }
}
