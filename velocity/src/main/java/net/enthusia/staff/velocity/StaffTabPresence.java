package net.enthusia.staff.velocity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.auth.StaffVanishVisibility;

/** Immutable, verified presence. Tab callbacks never access storage. */
record StaffTabPresence(Map<UUID, StaffRank> vanished, Set<UUID> staffMode) {
    StaffTabPresence {
        vanished = Map.copyOf(vanished);
        staffMode = Set.copyOf(staffMode);
    }

    boolean canSee(UUID viewer, StaffRank viewerRank, UUID target, StaffRank currentTargetRank) {
        if (viewer.equals(target)) {
            return true;
        }
        StaffRank savedRank = vanished.get(target);
        return (savedRank == null || (StaffVanishVisibility.canSee(viewerRank, savedRank)
                && StaffVanishVisibility.canSee(viewerRank, currentTargetRank)))
                && (!staffMode.contains(target) || StaffVanishVisibility.canSee(viewerRank, currentTargetRank));
    }

    Set<UUID> hiddenFromPublic() {
        java.util.HashSet<UUID> hidden = new java.util.HashSet<>(vanished.keySet());
        hidden.addAll(staffMode);
        return Set.copyOf(hidden);
    }

    boolean hiddenFromPublic(UUID target) {
        return vanished.containsKey(target) || staffMode.contains(target);
    }

    String marker(UUID target) {
        return (staffMode.contains(target) ? "<gold>[STAFF]</gold> " : "")
                + (vanished.containsKey(target) ? "<aqua>[V]</aqua> " : "");
    }
}
