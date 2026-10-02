package net.enthusia.staff.velocity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class StaffModeHandoffTracker {
    static final long TIMEOUT_SECONDS = 12L;

    private final ConcurrentHashMap<UUID, Pending> pending = new ConcurrentHashMap<>();

    boolean begin(
            UUID playerId,
            String source,
            String destination,
            UUID transferId,
            Instant now
    ) {
        Pending created = new Pending(
                playerId,
                source,
                destination,
                transferId,
                0,
                now.plusSeconds(TIMEOUT_SECONDS)
        );
        return pending.putIfAbsent(playerId, created) == null;
    }

    boolean inProgress(UUID playerId) {
        return pending.containsKey(playerId);
    }

    boolean completeReady(UUID playerId, String readyBackend, String currentBackend) {
        Pending current = pending.get(playerId);
        if (current == null || readyBackend == null || currentBackend == null
                || !current.destination().equalsIgnoreCase(readyBackend)
                || !current.destination().equalsIgnoreCase(currentBackend)) {
            return false;
        }
        return pending.remove(playerId, current);
    }

    Optional<Pending> claimTimedOut(UUID playerId, UUID transferId, Instant now) {
        Pending current = pending.get(playerId);
        if (current == null || !current.transferId().equals(transferId)
                || current.deadline().isAfter(now)
                || !pending.remove(playerId, current)) {
            return Optional.empty();
        }
        return Optional.of(current);
    }

    void retry(Pending previous, Instant now) {
        pending.putIfAbsent(previous.playerId(), new Pending(
                previous.playerId(),
                previous.source(),
                previous.destination(),
                previous.transferId(),
                previous.retryCount() + 1,
                now.plusSeconds(TIMEOUT_SECONDS)
        ));
    }

    void restore(Pending previous, Instant deadline) {
        pending.putIfAbsent(previous.playerId(), new Pending(
                previous.playerId(),
                previous.source(),
                previous.destination(),
                previous.transferId(),
                previous.retryCount(),
                deadline
        ));
    }

    void clear(UUID playerId, UUID transferId) {
        pending.computeIfPresent(playerId, (ignored, current) ->
                current.transferId().equals(transferId) ? null : current);
    }

    record Pending(
            UUID playerId,
            String source,
            String destination,
            UUID transferId,
            int retryCount,
            Instant deadline
    ) {
    }
}
