package net.enthusia.staff.paper.aireview;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class AiReviewLoadFence {
    private final ConcurrentHashMap<UUID, UUID> pending = new ConcurrentHashMap<>();

    UUID begin(UUID viewerId) {
        if (viewerId == null) {
            throw new IllegalArgumentException("viewerId must be present");
        }
        UUID token = UUID.randomUUID();
        pending.put(viewerId, token);
        return token;
    }

    boolean consume(UUID viewerId, UUID token) {
        return viewerId != null && token != null && pending.remove(viewerId, token);
    }

    void retire(UUID viewerId) {
        if (viewerId != null) {
            pending.remove(viewerId);
        }
    }

    int pendingViewerCount() {
        return pending.size();
    }
}
