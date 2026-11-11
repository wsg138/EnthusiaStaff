package net.enthusia.staff.paper.visibility;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Selects only viewers whose actual visibility changes during a vanish transition. */
final class PresenceTransitionAudience {
    private PresenceTransitionAudience() {
    }

    static Map<UUID, Boolean> capture(
            UUID subjectId,
            List<UUID> viewers,
            BiPredicate<UUID, UUID> canSee
    ) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(viewers, "viewers");
        Objects.requireNonNull(canSee, "canSee");
        Map<UUID, Boolean> before = new HashMap<>();
        for (UUID viewerId : viewers) {
            if (!subjectId.equals(viewerId)) {
                before.put(viewerId, canSee.test(viewerId, subjectId));
            }
        }
        return Map.copyOf(before);
    }

    static boolean shouldNotify(boolean visibleBefore, boolean visibleNow, boolean vanished) {
        return visibleBefore != visibleNow && visibleNow != vanished;
    }
}
