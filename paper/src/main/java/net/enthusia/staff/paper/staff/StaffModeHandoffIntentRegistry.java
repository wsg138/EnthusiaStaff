package net.enthusia.staff.paper.staff;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class StaffModeHandoffIntentRegistry {
    private static final Duration TTL = Duration.ofSeconds(30);

    private final Clock clock;
    private final ConcurrentHashMap<UUID, Intent> intents = new ConcurrentHashMap<>();

    StaffModeHandoffIntentRegistry(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    boolean prepare(UUID playerId, UUID transferId) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(transferId, "transferId");
        Instant now = clock.instant();
        AtomicBoolean accepted = new AtomicBoolean();
        intents.compute(playerId, (ignored, existing) -> {
            if (existing != null && existing.expiresAt().isAfter(now)
                    && !existing.transferId().equals(transferId)) {
                return existing;
            }
            accepted.set(true);
            return new Intent(transferId, now.plus(TTL));
        });
        return accepted.get();
    }

    Optional<UUID> consume(UUID playerId) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        Intent intent = intents.remove(playerId);
        if (intent == null || !intent.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(intent.transferId());
    }

    void cancel(UUID playerId, UUID transferId) {
        if (playerId == null || transferId == null) {
            return;
        }
        intents.computeIfPresent(playerId, (ignored, existing) ->
                existing.transferId().equals(transferId) ? null : existing);
    }

    private record Intent(UUID transferId, Instant expiresAt) {
    }
}
