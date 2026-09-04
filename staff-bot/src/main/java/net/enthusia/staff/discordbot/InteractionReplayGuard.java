package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded in-process duplicate-interaction guard for read-only Discord UX packages.
 * Destructive moderation operations must additionally use the durable domain/database idempotency mechanisms.
 */
public final class InteractionReplayGuard {
    public enum ClaimResult {
        CLAIMED,
        DUPLICATE,
        SATURATED
    }

    private static final int MIN_CAPACITY = 1;

    private final int capacity;
    private final Duration ttl;
    private final Map<Long, Instant> expirations = new HashMap<>();
    private final Object lock = new Object();

    public InteractionReplayGuard(int capacity, Duration ttl) {
        if (capacity < MIN_CAPACITY) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL must be positive");
        }
    }

    public ClaimResult claim(long interactionId) {
        return claim(interactionId, Instant.now());
    }

    ClaimResult claim(long interactionId, Instant now) {
        synchronized (lock) {
            if (interactionId <= 0) {
                throw new IllegalArgumentException("interaction id must be positive");
            }
            Objects.requireNonNull(now, "now");
            prune(now);
            if (expirations.containsKey(interactionId)) {
                return ClaimResult.DUPLICATE;
            }
            if (expirations.size() >= capacity) {
                return ClaimResult.SATURATED;
            }
            expirations.put(interactionId, now.plus(ttl));
            return ClaimResult.CLAIMED;
        }
    }

    /** Releases a pre-commit claim so a safely failed read-only attempt can be retried. */
    public boolean release(long interactionId) {
        synchronized (lock) {
            return expirations.remove(interactionId) != null;
        }
    }

    int size(Instant now) {
        synchronized (lock) {
            Objects.requireNonNull(now, "now");
            prune(now);
            return expirations.size();
        }
    }

    private void prune(Instant now) {
        Iterator<Map.Entry<Long, Instant>> iterator = expirations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Instant> entry = iterator.next();
            if (!now.isBefore(entry.getValue())) {
                iterator.remove();
            }
        }
    }
}
