package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class StaffToolSpectateContinuousFollowTest {
    @Test
    void creativeModeContinuesToTrackPlayer() {
        assertContinuedMovement(GameMode.CREATIVE);
    }

    @Test
    void survivalModeContinuesToTrackPlayer() {
        assertContinuedMovement(GameMode.SURVIVAL);
    }

    private static void assertContinuedMovement(GameMode mode) {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.actor.gameMode = mode;
        harness.reachFinalTargetHandoff();
        assertEquals(1, harness.actor.teleports);
        assertEquals(0, harness.actor.attachments);
        assertEquals(mode, harness.actor.gameMode);
        harness.target.location = new Location(null, 30, 65, -5);
        harness.actor.runRecurring();
        harness.runOwned(harness.target);
        harness.runOwned(harness.actor);
        assertEquals(2, harness.actor.teleports, "Follow should track target movement for " + mode);
        harness.target.location = new Location(null, 70, 70, 20);
        harness.actor.runRecurring();
        harness.runOwned(harness.target);
        harness.runOwned(harness.actor);
        assertEquals(3, harness.actor.teleports, "Follow must continue across multiple moves");
        assertTrue(harness.flow.stop(harness.actor.player));
        harness.actor.runRecurring();
        assertEquals(3, harness.actor.teleports, "Stopped follow must not teleport again");
    }

    @Test
    void lostAuthorityStopsContinuousFollowWithoutFurtherTeleporting() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.actor.gameMode = GameMode.CREATIVE;
        harness.reachFinalTargetHandoff();
        harness.actorAuthority.set(false);
        harness.actor.runRecurring();
        assertFalse(harness.flow.stop(harness.actor.player));
        assertEquals(1, harness.actor.teleports);
    }

    @Test
    void reconnectingTargetCancelsOldFollowInsteadOfSwitchingSessions() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.actor.gameMode = GameMode.CREATIVE;
        harness.reachFinalTargetHandoff();
        harness.reconnectTarget().location = new Location(null, 60, 65, 60);
        harness.actor.runRecurring();
        harness.runOwned(harness.target);
        assertFalse(harness.flow.stop(harness.actor.player));
        assertEquals(1, harness.actor.teleports);
    }

    @Test
    void movingTargetThatVanishesCannotBeContinuallyFollowed() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.actor.gameMode = GameMode.SURVIVAL;
        harness.reachFinalTargetHandoff();
        harness.target.location = new Location(null, 30, 65, 30);
        harness.vanished.add(harness.targetId);
        harness.actor.runRecurring();
        harness.runOwned(harness.target);
        assertFalse(harness.flow.stop(harness.actor.player));
        assertEquals(1, harness.actor.teleports);
    }
}
