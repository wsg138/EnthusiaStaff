package net.enthusia.staff.paper.visibility;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fences asynchronous viewer and subject callbacks to the latest vanish commit.
 * Tokens are retired on disconnect; reconnects never reuse a previous token.
 */
final class PresenceTransitionRevisions {
    private final Map<UUID, UUID> current = new ConcurrentHashMap<>();

    UUID advance(UUID subjectId) {
        UUID revision = UUID.randomUUID();
        current.put(Objects.requireNonNull(subjectId, "subjectId"), revision);
        return revision;
    }

    boolean isCurrent(UUID subjectId, UUID revision) {
        return revision != null && revision.equals(current.get(subjectId));
    }

    void retire(UUID subjectId) {
        current.remove(subjectId);
    }

    void retire(UUID subjectId, UUID revision) {
        current.remove(subjectId, revision);
    }
}
