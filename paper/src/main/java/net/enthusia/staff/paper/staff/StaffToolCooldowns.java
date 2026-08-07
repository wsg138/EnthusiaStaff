package net.enthusia.staff.paper.staff;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Small per-player/per-tool cooldown ledger that remains independent of Bukkit event timing. */
final class StaffToolCooldowns {
    private final Clock clock;
    private final Map<Key, Instant> readyAt = new ConcurrentHashMap<>();

    StaffToolCooldowns(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    Result acquire(UUID playerId, StaffToolDefinition tool, Duration cooldown) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(tool, "tool");
        java.util.Objects.requireNonNull(cooldown, "cooldown");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown cannot be negative");
        }
        Instant now = clock.instant();
        AtomicBoolean allowed = new AtomicBoolean();
        AtomicLong remainingMillis = new AtomicLong();
        readyAt.compute(new Key(playerId, tool), (ignored, current) -> {
            if (current != null && current.isAfter(now)) {
                remainingMillis.set(Math.max(1L, Duration.between(now, current).toMillis()));
                return current;
            }
            allowed.set(true);
            return now.plus(cooldown);
        });
        return new Result(allowed.get(), remainingMillis.get());
    }

    void clear(UUID playerId) {
        readyAt.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    record Result(boolean allowed, long remainingMillis) {
    }

    private record Key(UUID playerId, StaffToolDefinition tool) {
    }
}
