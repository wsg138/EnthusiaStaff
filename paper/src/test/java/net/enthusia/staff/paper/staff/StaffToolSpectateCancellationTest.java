package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Regression coverage for late entity-scheduler and teleport callbacks after cancellation. */
class StaffToolSpectateCancellationTest {
    @Test
    void stoppingBeforeTargetSchedulerExecutesInvalidatesPendingStart() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.flow.begin(harness.actor.player, harness.targetId);

        assertTrue(harness.flow.stop(harness.actor.player));
        harness.runOwned(harness.target);

        assertEquals(0, harness.actor.teleports);
        assertFalse(harness.flow.stop(harness.actor.player));
    }

    @Test
    void stoppingDuringAsyncTeleportCannotRestartFollowWhenItCompletes() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachTeleportRequest();

        assertTrue(harness.flow.stop(harness.actor.player));
        harness.actor.teleport.complete(true);

        assertEquals(0, harness.actor.attachments);
        assertEquals(0, harness.globalTasks.size(), "No attachment handoff may be queued after cancellation");
        assertFalse(harness.flow.stop(harness.actor.player));
    }

    @Test
    void supersededRequestCannotTeleportBeforeNewRequest() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.flow.begin(harness.actor.player, harness.targetId);
        harness.flow.begin(harness.actor.player, harness.targetId);

        harness.runOwned(harness.target); // Retired first request.
        assertEquals(0, harness.actor.teleports);
        harness.runOwned(harness.target); // Current request.
        harness.runOwned(harness.actor);
        harness.runOwned(harness.target);
        harness.runOwned(harness.actor);

        assertEquals(1, harness.actor.teleports);
        assertTrue(harness.flow.stop(harness.actor.player));
    }

    @Test
    void stoppingOwnedNativeSpectatorAttachmentDetachesButDoesNotRestart() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.finishSuccessfulFlow();
        assertSame(harness.target.player, harness.actor.lastSpectator);

        assertTrue(harness.flow.stop(harness.actor.player));
        assertNull(harness.actor.lastSpectator);
        harness.actor.runRecurring();
        assertFalse(harness.flow.stop(harness.actor.player));
    }

    @Test
    void becomingSpectateExemptAfterAttachmentDetachesOnTargetOwnedCheck() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.finishSuccessfulFlow();
        harness.target.exempt.set(true);

        for (int tick = 0; tick < 20; tick++) {
            harness.actor.runRecurring();
        }
        harness.runOwned(harness.target); // Reads permission on the target scheduler.
        harness.runOwned(harness.actor); // Detaches only on the observer scheduler.

        assertNull(harness.actor.lastSpectator);
        assertEquals(1, harness.actor.detachments);
        assertFalse(harness.flow.stop(harness.actor.player));
    }
}
