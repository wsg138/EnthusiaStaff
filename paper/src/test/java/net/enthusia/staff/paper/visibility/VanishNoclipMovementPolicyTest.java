package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.event.player.PlayerFailMoveEvent;
import org.junit.jupiter.api.Test;

class VanishNoclipMovementPolicyTest {
    @Test
    void bypassesOnlyClippedMovementForVanishedStaff() {
        assertTrue(VanishNoclipMovementPolicy.bypass(
                true,
                PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK
        ));
        assertFalse(VanishNoclipMovementPolicy.bypass(
                false,
                PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK
        ));
        assertFalse(VanishNoclipMovementPolicy.bypass(
                true,
                PlayerFailMoveEvent.FailReason.MOVED_WRONGLY
        ));
        assertFalse(VanishNoclipMovementPolicy.bypass(
                true,
                PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY
        ));
        assertFalse(VanishNoclipMovementPolicy.bypass(
                true,
                PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK
        ));
    }
}
