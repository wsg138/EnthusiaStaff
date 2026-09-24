package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class StaffToolTargetPolicyTest {
    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void normalSurvivalPlayerIsEligible() {
        assertTrue(eligible(TARGET, safeState(), safeEnvironment()));
    }

    @Test
    void randomTeleportExcludesSelfAndUnsafeIdentity() {
        assertFalse(eligible(ACTOR, safeState(), safeEnvironment()));
        assertFalse(StaffToolTargetPolicy.eligibleRandomTarget(new StaffToolTargetPolicy.Candidate(
                new StaffToolTargetPolicy.Identity(null, TARGET),
                safeState(),
                safeEnvironment()
        )));
    }

    @Test
    void randomTeleportExcludesStaffHiddenFrozenExemptAndUnsafePlayerStates() {
        for (StaffToolTargetPolicy.State unsafe : unsafeStates()) {
            assertFalse(eligible(TARGET, unsafe, safeEnvironment()));
        }
    }

    @Test
    void executionTimeRevalidationRejectsEverySupportedStateChange() {
        assertTrue(eligible(TARGET, safeState(), safeEnvironment()));
        for (StaffToolTargetPolicy.State unsafe : unsafeStates()) {
            assertFalse(eligible(TARGET, unsafe, safeEnvironment()));
        }
        assertFalse(eligible(
                TARGET,
                safeState(),
                new StaffToolTargetPolicy.Environment(GameMode.SPECTATOR, true)
        ));
        assertFalse(eligible(
                TARGET,
                safeState(),
                new StaffToolTargetPolicy.Environment(GameMode.SURVIVAL, false)
        ));
    }

    @Test
    void randomTeleportExcludesSpectatorsAndDisabledWorlds() {
        assertFalse(eligible(TARGET, safeState(), new StaffToolTargetPolicy.Environment(GameMode.SPECTATOR, true)));
        assertFalse(eligible(TARGET, safeState(), new StaffToolTargetPolicy.Environment(GameMode.SURVIVAL, false)));
        assertFalse(eligible(TARGET, safeState(), new StaffToolTargetPolicy.Environment(null, true)));
    }

    private static boolean eligible(
            UUID targetId,
            StaffToolTargetPolicy.State state,
            StaffToolTargetPolicy.Environment environment
    ) {
        return StaffToolTargetPolicy.eligibleRandomTarget(new StaffToolTargetPolicy.Candidate(
                new StaffToolTargetPolicy.Identity(ACTOR, targetId),
                state,
                environment
        ));
    }

    private static List<StaffToolTargetPolicy.State> unsafeStates() {
        return List.of(
                state(true, false, false, false, false, false, false),
                state(false, true, false, false, false, false, false),
                state(false, false, true, false, false, false, false),
                state(false, false, false, true, false, false, false),
                state(false, false, false, false, true, false, false),
                state(false, false, false, false, false, true, false),
                state(false, false, false, false, false, false, true)
        );
    }

    private static StaffToolTargetPolicy.State safeState() {
        return state(false, false, false, false, false, false, false);
    }

    private static StaffToolTargetPolicy.State state(
            boolean staffMode,
            boolean vanished,
            boolean frozen,
            boolean exempt,
            boolean dead,
            boolean sleeping,
            boolean vehicle
    ) {
        return new StaffToolTargetPolicy.State(
                staffMode,
                vanished,
                frozen,
                exempt,
                dead,
                sleeping,
                vehicle
        );
    }

    private static StaffToolTargetPolicy.Environment safeEnvironment() {
        return new StaffToolTargetPolicy.Environment(GameMode.SURVIVAL, true);
    }
}
