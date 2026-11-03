package net.enthusia.staff.paper.punishment;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * One pending asynchronous history presentation per viewer. Tokens are
 * consumed on the entity thread immediately before the inventory is opened,
 * not when a background database query completes.
 */
final class PunishmentGuiHistoryFence {
    private final ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();

    Object begin(UUID viewerId) {
        Object token = new Object();
        pending.put(viewerId, token);
        return token;
    }

    boolean consume(UUID viewerId, Object token) {
        return pending.remove(viewerId, token);
    }

    void invalidate(UUID viewerId) {
        pending.remove(viewerId);
    }
}
