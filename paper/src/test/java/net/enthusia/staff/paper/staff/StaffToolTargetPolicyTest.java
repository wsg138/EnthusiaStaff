package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class StaffToolTargetPolicyTest {
    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void normalSurvivalPlayerIsEligible() {
        assertTrue(eligible(TARGET, false, false, false, false, false, false, false, GameMode.SURVIVAL, true));
    }

    @Test
    void randomTeleportExcludesSelfStaffHiddenFrozenExemptAndUnsafeStates() {
        assertFalse(eligible(ACTOR, false, false, false, false, false, false, false, GameMode.SURVIVAL, true));
        assertFalse(eligible(TARGET, true, false, false, false, false, false, false, GameMode.SURVIVAL, true));
        assertFalse(eligible(TARGET, false, true, false, false, false, false, false, GameMode.SURVIVAL, true));
        assertFalse(eligible(TARGET, false, false, true, false, false, false, false, GameMode.SURVIVAL, true));
        assertFalse(eligible(TARGET, false, false, false, true, false, false, false, GameMode.SURVIVAL, true));
        assertFalse(eligible(TARGET, false, false, false, false, true, false, false, GameMode.SURVIVAL, true));
        assertFalse(eligible(TARGET, false, false, false, false, false, true, false, GameMode.SURVIVAL, true));
        assertFalse(eligible(TARGET, false, false, false, false, false, false, true, GameMode.SURVIVAL, true));
        assertFalse(eligible(TARGET, false, false, false, false, false, false, false, GameMode.SPECTATOR, true));
        assertFalse(eligible(TARGET, false, false, false, false, false, false, false, GameMode.SURVIVAL, false));
    }

    private static boolean eligible(
            UUID target,
            boolean staffMode,
            boolean vanished,
            boolean frozen,
            boolean exempt,
            boolean dead,
            boolean sleeping,
            boolean vehicle,
            GameMode gameMode,
            boolean worldEnabled
    ) {
        return StaffToolTargetPolicy.eligibleRandomTarget(
                ACTOR,
                target,
                staffMode,
                vanished,
                frozen,
                exempt,
                dead,
                sleeping,
                vehicle,
                gameMode,
                worldEnabled
        );
    }
}
