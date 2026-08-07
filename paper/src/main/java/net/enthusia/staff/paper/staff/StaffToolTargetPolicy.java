package net.enthusia.staff.paper.staff;

import java.util.UUID;
import org.bukkit.GameMode;

/** Pure suitability checks for random-player teleport targets. */
final class StaffToolTargetPolicy {
    private StaffToolTargetPolicy() {
    }

    static boolean eligibleRandomTarget(
            UUID actorId,
            UUID targetId,
            boolean staffModeActive,
            boolean vanished,
            boolean frozen,
            boolean exempt,
            boolean dead,
            boolean sleeping,
            boolean insideVehicle,
            GameMode gameMode,
            boolean worldEnabled
    ) {
        if (actorId == null || targetId == null || actorId.equals(targetId)) {
            return false;
        }
        if (staffModeActive || vanished || frozen || exempt || dead || sleeping || insideVehicle || !worldEnabled) {
            return false;
        }
        return gameMode != null && gameMode != GameMode.SPECTATOR;
    }
}
