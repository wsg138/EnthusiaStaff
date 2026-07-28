package net.enthusia.staff.protocol;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReplayGuard {
    private final int maximumEntries;
    private final Duration retention;
    private final Object lock = new Object();
    // Linked insertion order drives eviction; lock serializes every access.
    @SuppressWarnings("PMD.DocumentMutableMapFieldConcurrency")
    private final Map<String, Instant> seen = new LinkedHashMap<>();

    public ReplayGuard(int maximumEntries, Duration retention) {
        if (maximumEntries < 1 || retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("invalid replay guard bounds");
        }
        this.maximumEntries = maximumEntries;
        this.retention = retention;
    }

    public boolean recordIfNew(String serverId, String nonce, Instant now) {
        synchronized (lock) {
            evictExpired(now);
            String key = serverId + '\u0000' + nonce;
            if (seen.containsKey(key)) {
                return false;
            }
            seen.put(key, now.plus(retention));
            while (seen.size() > maximumEntries) {
                Iterator<String> iterator = seen.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
            return true;
        }
    }

    private void evictExpired(Instant now) {
        Iterator<Map.Entry<String, Instant>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isAfter(now)) {
                continue;
            }
            iterator.remove();
        }
    }
}
