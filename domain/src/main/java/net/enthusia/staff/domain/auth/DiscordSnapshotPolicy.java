package net.enthusia.staff.domain.auth;

import java.util.Optional;

final class DiscordSnapshotPolicy {
    private DiscordSnapshotPolicy() {
    }

    static boolean matches(
            DiscordAuthorizationSnapshot snapshot,
            Actor currentActor,
            Optional<Actor> currentTargetStaff
    ) {
        return snapshot.actorId().equals(currentActor.id())
                && snapshot.actorRank() == currentActor.rank()
                && snapshot.targetStaffId().equals(currentTargetStaff.map(Actor::id))
                && snapshot.targetStaffRank().equals(currentTargetStaff.map(Actor::rank));
    }
}
