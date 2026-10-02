package net.enthusia.staff.velocity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Calculates the visible player count for one backend from a verified proxy presence snapshot. */
final class LocalPublicOnlineCountPolicy {
    static final String PLACEHOLDER = "<enthusiastaff_local_public_online>";

    private LocalPublicOnlineCountPolicy() {
    }

    static int count(
            boolean verified,
            String viewerBackend,
            Map<UUID, String> playerBackends,
            Set<UUID> vanishedStaff
    ) {
        if (!verified || viewerBackend == null || viewerBackend.isBlank()) {
            return 0;
        }
        int visible = 0;
        for (Map.Entry<UUID, String> player : playerBackends.entrySet()) {
            if (viewerBackend.equals(player.getValue()) && !vanishedStaff.contains(player.getKey())) {
                visible++;
            }
        }
        return visible;
    }
}
