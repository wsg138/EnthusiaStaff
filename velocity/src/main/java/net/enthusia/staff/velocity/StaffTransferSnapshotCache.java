package net.enthusia.staff.velocity;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.enthusia.staff.domain.staff.StaffTransferSnapshot;

/**
 * Proxy-side cache for cross-server transfer snapshots (overnight/cross-server).
 *
 * <p>The source backend uploads its in-memory vanish/staff-mode snapshot to the proxy
 * <em>before</em> any database write. The proxy holds it here just long enough to embed it
 * in the {@code STAFF_MODE_HANDOFF_PREPARE} message for the destination backend, so the
 * transfer never waits on persistence.</p>
 *
 * <p>Entries expire after a short TTL: a snapshot that never gets consumed (player
 * disconnected mid-transfer, destination unreachable) simply disappears, and both sides
 * fall back to the database. A proxy restart clears the cache entirely, which is safe for
 * the same reason.</p>
 */
final class StaffTransferSnapshotCache {
    private static final Duration TTL = Duration.ofSeconds(30);

    private final Clock clock;
    private final ConcurrentHashMap<UUID, Entry> cached = new ConcurrentHashMap<>();

    StaffTransferSnapshotCache(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    void put(StaffTransferSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        cached.put(snapshot.playerId(), new Entry(snapshot, clock.millis() + TTL.toMillis()));
    }

    /**
     * Takes the cached snapshot for a transfer, consuming it. Empty when nothing was
     * uploaded, it expired, or the transfer id does not match.
     */
    Optional<StaffTransferSnapshot> take(UUID playerId, UUID transferId) {
        if (playerId == null || transferId == null) {
            return Optional.empty();
        }
        Entry entry = cached.remove(playerId);
        if (entry == null || entry.expiresAtMillis() <= clock.millis()) {
            return Optional.empty();
        }
        StaffTransferSnapshot snapshot = entry.snapshot();
        return snapshot.transferId().equals(transferId) ? Optional.of(snapshot) : Optional.empty();
    }

    /** Drops any cached snapshot, e.g. when the player disconnects mid-transfer. */
    void evict(UUID playerId) {
        if (playerId != null) {
            cached.remove(playerId);
        }
    }

    private record Entry(StaffTransferSnapshot snapshot, long expiresAtMillis) {
    }
}
