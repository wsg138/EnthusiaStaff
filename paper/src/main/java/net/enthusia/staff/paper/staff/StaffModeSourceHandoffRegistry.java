package net.enthusia.staff.paper.staff;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

final class StaffModeSourceHandoffRegistry {
    private final ConcurrentHashMap<UUID, Entry> active = new ConcurrentHashMap<>();

    boolean begin(UUID playerId, UUID transferId) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(transferId, "transferId");
        Entry created = new Entry(transferId);
        Entry existing = active.putIfAbsent(playerId, created);
        return existing == null || existing.transferId().equals(transferId);
    }

    boolean abort(UUID playerId, UUID transferId) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(transferId, "transferId");
        Entry entry = active.get(playerId);
        if (entry == null) {
            return true;
        }
        if (!entry.transferId().equals(transferId)) {
            return false;
        }
        synchronized (entry) {
            if (active.get(playerId) == entry) {
                active.remove(playerId, entry);
            }
            return true;
        }
    }

    void finish(UUID playerId, UUID transferId) {
        if (playerId == null || transferId == null) {
            return;
        }
        Entry entry = active.get(playerId);
        if (entry == null || !entry.transferId().equals(transferId)) {
            return;
        }
        synchronized (entry) {
            active.remove(playerId, entry);
        }
    }

    Optional<Boolean> commitIfActive(
            UUID playerId,
            UUID transferId,
            BooleanSupplier commit
    ) {
        java.util.Objects.requireNonNull(commit, "commit");
        Entry entry = active.get(playerId);
        if (entry == null || !entry.transferId().equals(transferId)) {
            return Optional.empty();
        }
        synchronized (entry) {
            if (active.get(playerId) != entry) {
                return Optional.empty();
            }
            try {
                return Optional.of(commit.getAsBoolean());
            } finally {
                active.remove(playerId, entry);
            }
        }
    }

    private record Entry(UUID transferId) {
    }
}
