package net.enthusia.staff.paper.visibility;

import io.papermc.paper.event.player.PlayerFailMoveEvent;

final class VanishNoclipMovementPolicy {
    private VanishNoclipMovementPolicy() { }

    static boolean bypass(boolean vanished, PlayerFailMoveEvent.FailReason reason) {
        return vanished && reason == PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK;
    }
}
