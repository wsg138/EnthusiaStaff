package net.enthusia.staff.velocity;

import java.util.Set;
import java.util.UUID;

/** Calculates the network-wide player count safe to expose to public tab surfaces. */
final class PublicOnlineCountPolicy {
    static final String PLACEHOLDER = "<enthusiastaff_public_online>";

    private PublicOnlineCountPolicy() {
    }

    static int count(boolean verified, Set<UUID> onlinePlayers, Set<UUID> vanishedStaff) {
        if (!verified) {
            return 0;
        }
        int visible = onlinePlayers.size();
        for (UUID vanished : vanishedStaff) {
            if (onlinePlayers.contains(vanished)) {
                visible--;
            }
        }
        return visible;
    }
}
