package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/** Bounded replay guard for short-lived private read API request nonces. */
final class ModerationReadReplayGuard {
    private final int capacity;
    private final Duration ttl;
    private final Map<String, Instant> expirations = new HashMap<>();

    ModerationReadReplayGuard(int capacity, Duration ttl) {
        if (capacity < 1 || ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("replay guard bounds are invalid");
        }
        this.capacity = capacity;
        this.ttl = ttl;
    }

    synchronized boolean claim(String nonce, Instant now) {
        Objects.requireNonNull(now, "now");
        if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{32,64}")) {
            return false;
        }
        prune(now);
        if (expirations.containsKey(nonce) || expirations.size() >= capacity) {
            return false;
        }
        expirations.put(nonce, now.plus(ttl));
        return true;
    }

    private void prune(Instant now) {
        Iterator<Map.Entry<String, Instant>> iterator = expirations.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!now.isBefore(iterator.next().getValue())) {
                iterator.remove();
            }
        }
    }
}
