package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StaffToolDispatcherSpectateFlowTest {
    @Test
    void visibleNonExemptTargetSucceeds() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();

        harness.finishSuccessfulFlow();

        assertEquals(1, harness.actor.teleports);
        assertEquals(1, harness.actor.attachments);
        assertSame(harness.target.player, harness.actor.lastSpectator);
    }

    @Test
    void targetAlreadyVanishedIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.vanished.add(harness.targetId);

        harness.beginAndInspectTarget();

        assertEquals(0, harness.actor.teleports);
        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void targetAlreadySpectateExemptIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.target.exempt.set(true);

        harness.beginAndInspectTarget();

        assertEquals(0, harness.actor.teleports);
        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void targetBecomesVanishedBeforeTeleportIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachPreTeleportTargetHandoff();
        harness.vanished.add(harness.targetId);

        harness.runOwned(harness.target);

        assertEquals(0, harness.actor.teleports);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void targetBecomesExemptBeforeTeleportIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachPreTeleportTargetHandoff();
        harness.target.exempt.set(true);

        harness.runOwned(harness.target);

        assertEquals(0, harness.actor.teleports);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void sameUuidReconnectBeforeTeleportIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachPreTeleportTargetHandoff();
        StaffToolSpectateFlowHarness.Handle staleTarget = harness.target;
        StaffToolSpectateFlowHarness.Handle replacement = harness.reconnectTarget();

        harness.runOwned(replacement);

        assertFalse(staleTarget.online.get());
        assertTrue(replacement.online.get());
        assertEquals(0, harness.actor.teleports);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void targetDisconnectBeforeTeleportIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachPreTeleportTargetHandoff();
        harness.disconnectTarget();

        harness.runNextGlobal();

        assertEquals(0, harness.actor.teleports);
        assertEquals(0, harness.actor.attachments);
    }

    @Test
    void targetSchedulerRetirementBeforeTeleportIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachPreTeleportTargetHandoff();
        harness.target.retireNext();

        harness.runNextGlobal();

        assertEquals(0, harness.actor.teleports);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void targetSchedulerRejectionBeforeTeleportIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachPreTeleportTargetHandoff();
        harness.target.rejectNext();

        harness.runNextGlobal();

        assertEquals(0, harness.actor.teleports);
        assertEquals(0, harness.actor.attachments);
    }

    @Test
    void targetSchedulerExceptionBeforeTeleportIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachPreTeleportTargetHandoff();
        harness.target.throwNext();

        harness.runNextGlobal();

        assertEquals(0, harness.actor.teleports);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void duplicateRetirementAndRejectionSettlesOnce() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachPreTeleportTargetHandoff();
        harness.target.retireAndRejectNext();

        harness.runNextGlobal();

        assertEquals(1, harness.globalTasks.size());
        assertEquals(0, harness.actor.teleports);
        harness.runOwned(harness.actor);
        assertEquals(1, harness.actor.messages.size());
        assertTrue(harness.globalTasks.isEmpty());
    }

    @Test
    void globalSchedulerSubmissionExceptionFailsClosed() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.failNextGlobal.set(true);

        harness.flow.begin(harness.actor.player, harness.targetId);

        assertEquals(0, harness.actor.teleports);
        assertEquals(0, harness.actor.attachments);
    }

    @Test
    void actorLosesStaffSessionBeforeTeleportIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachPreTeleportActorHandoff();
        harness.actorSession.set(false);

        harness.runOwned(harness.actor);

        assertEquals(0, harness.actor.teleports);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void actorLosesSpectatePermissionBeforeTeleportIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachPreTeleportActorHandoff();
        harness.actor.permission.set(false);

        harness.runOwned(harness.actor);

        assertEquals(0, harness.actor.teleports);
        assertEquals(0, harness.actor.attachments);
    }

    @Test
    void teleportAsyncSynchronousExceptionFailsClosed() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachPreTeleportActorHandoff();
        harness.actor.teleportThrows.set(true);

        harness.runOwned(harness.actor);

        assertEquals(1, harness.actor.teleports);
        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void teleportAsyncFalseFailsClosed() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachTeleportRequest();

        harness.actor.teleport.complete(false);

        assertEquals(1, harness.actor.teleports);
        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void teleportAsyncExceptionalCompletionFailsClosed() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachTeleportRequest();

        harness.actor.teleport.completeExceptionally(new IllegalStateException("teleport failed"));

        assertEquals(1, harness.actor.teleports);
        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void teleportAsyncCancellationFailsClosed() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachTeleportRequest();

        assertTrue(harness.actor.teleport.cancel(true));

        assertEquals(1, harness.actor.teleports);
        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void targetBecomesVanishedAfterTeleportBeforeAttachmentIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalTargetHandoff();
        harness.vanished.add(harness.targetId);

        harness.runOwned(harness.target);

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void targetBecomesExemptAfterTeleportBeforeAttachmentIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalTargetHandoff();
        harness.target.exempt.set(true);

        harness.runOwned(harness.target);

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void sameUuidReconnectBeforeAttachmentRevalidationIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalTargetHandoff();
        StaffToolSpectateFlowHarness.Handle staleTarget = harness.target;
        StaffToolSpectateFlowHarness.Handle replacement = harness.reconnectTarget();

        harness.runOwned(replacement);

        assertFalse(staleTarget.online.get());
        assertTrue(replacement.online.get());
        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void targetDisconnectBeforeAttachmentIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalTargetHandoff();
        harness.disconnectTarget();

        harness.runNextGlobal();

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void targetSchedulerRetirementBeforeAttachmentIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalTargetHandoff();
        harness.target.retireNext();

        harness.runNextGlobal();

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void targetSchedulerRejectionBeforeAttachmentIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalTargetHandoff();
        harness.target.rejectNext();

        harness.runNextGlobal();

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void targetSchedulerExceptionBeforeAttachmentIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalTargetHandoff();
        harness.target.throwNext();

        harness.runNextGlobal();

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void sameUuidReconnectCannotAttachStalePlayerObject() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalActorHandoff();
        StaffToolSpectateFlowHarness.Handle staleTarget = harness.target;
        StaffToolSpectateFlowHarness.Handle replacement = harness.reconnectTarget();

        harness.runOwned(harness.actor);

        assertFalse(staleTarget.online.get());
        assertTrue(replacement.online.get());
        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void actorLosesAuthorityBeforeFinalAttachmentIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalActorHandoff();
        harness.actorAuthority.set(false);

        harness.runOwned(harness.actor);

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void actorLosesStaffSessionBeforeFinalAttachmentIsRejected() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalActorHandoff();
        harness.actorSession.set(false);

        harness.runOwned(harness.actor);

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void actorSchedulerRejectionBeforeFinalAttachmentFailsClosed() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalActorHandoff();
        harness.actor.rejectNext();

        harness.runNextGlobal();

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void actorSchedulerRetirementBeforeFinalAttachmentFailsClosed() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalActorHandoff();
        harness.actor.retireNext();

        harness.runNextGlobal();

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void actorSchedulerExceptionBeforeFinalAttachmentFailsClosed() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalActorHandoff();
        harness.actor.throwNext();

        harness.runNextGlobal();

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
    }

    @Test
    void duplicateSchedulerCallbackCannotAttachTwice() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.reachFinalActorHandoff();
        harness.runNextGlobal();

        harness.actor.runOwnedTwice();

        assertEquals(1, harness.actor.attachments);
        assertSame(harness.target.player, harness.actor.lastSpectator);
    }
}
