package net.enthusia.staff.paper.staff;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks durable death-exit intent separately from the single active scheduler chain. */
final class StaffModeDeathExitGate {
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeChains = ConcurrentHashMap.newKeySet();

    boolean request(UUID playerId) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        pending.add(playerId);
        return activeChains.add(playerId);
    }

    boolean resume(UUID playerId) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        return pending.contains(playerId) && activeChains.add(playerId);
    }

    boolean pending(UUID playerId) {
        return playerId != null && pending.contains(playerId);
    }

    void release(UUID playerId) {
        if (playerId != null) {
            activeChains.remove(playerId);
        }
    }

    void complete(UUID playerId) {
        if (playerId != null) {
            pending.remove(playerId);
            activeChains.remove(playerId);
        }
    }
}
