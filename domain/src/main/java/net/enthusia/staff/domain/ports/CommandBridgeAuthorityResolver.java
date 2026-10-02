package net.enthusia.staff.domain.ports;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;

/** Minecraft-side current authority lookup. Implementations must resolve rank and permission at call time. */
@FunctionalInterface
public interface CommandBridgeAuthorityResolver {
    Optional<Snapshot> current(UUID actorPlayerId, String requiredPermission);

    record Snapshot(StaffRank rank, boolean permissionGranted) {
        public Snapshot {
            if (rank == null) {
                throw new IllegalArgumentException("current staff rank is required");
            }
        }
    }
}
