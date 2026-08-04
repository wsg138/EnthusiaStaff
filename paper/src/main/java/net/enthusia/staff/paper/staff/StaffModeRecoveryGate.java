package net.enthusia.staff.paper.staff;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class StaffModeRecoveryGate {
    private final Set<UUID> transitions;
    private final Set<UUID> retryable = ConcurrentHashMap.newKeySet();

    StaffModeRecoveryGate(Set<UUID> transitions) {
        this.transitions = Objects.requireNonNull(transitions, "transitions");
    }

    boolean begin(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (transitions.add(playerId)) {
            return true;
        }
        return retryable.remove(playerId);
    }

    void retry(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        transitions.add(playerId);
        retryable.add(playerId);
    }

    void clear(UUID playerId) {
        retryable.remove(playerId);
        transitions.remove(playerId);
    }
}
