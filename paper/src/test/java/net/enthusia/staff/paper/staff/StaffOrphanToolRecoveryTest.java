package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.domain.staff.StaffSessionOwnership;
import org.junit.jupiter.api.Test;

class StaffOrphanToolRecoveryTest {
    private static final UUID PLAYER_ID = UUID.fromString("72000000-0000-0000-0000-000000000041");
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void ranklessDetachedSessionClosesWithoutReplayingSourceInventory() {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.durableSession = new StaffSessionSnapshot(UUID.randomUUID(), PLAYER_ID,
                StaffSessionOwnership.DETACHED_SERVER_ID, StaffSessionState.ACTIVE,
                false, 1, "a".repeat(64), new byte[]{9, 8, 7}, NOW, 4);
        byte[] original = fixture.durableSession.snapshot();
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertFalse(fixture.workers.queued.isEmpty(), "rank loss must schedule safe detached retirement");
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertTrue(fixture.closed);
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
        assertArrayEquals(original, fixture.durableSession.snapshot());
        assertEquals(1, fixture.exitNotifications);
    }

    @Test
    void rejectedDetachedClosureKeepsInventoryAndAllowsAnotherRecovery() {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.closeAccepted = false;
        fixture.durableSession = new StaffSessionSnapshot(UUID.randomUUID(), PLAYER_ID,
                StaffSessionOwnership.DETACHED_SERVER_ID, StaffSessionState.ACTIVE,
                false, 1, "a".repeat(64), new byte[]{9, 8, 7}, NOW, 4);
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertFalse(fixture.workers.queued.isEmpty(), "rank loss must schedule safe detached retirement");
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertFalse(fixture.closed);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
        fixture.closeAccepted = true;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertTrue(fixture.closed);
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void staleDetachedClosureCannotRetireAReplacedDurableLease() {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffSessionSnapshot old = fixture.detachedSession();
        fixture.durableSession = old;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        fixture.durableSession = fixture.detachedSession();
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertFalse(fixture.closed);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
    }

    @Test
    void staleDetachedEntityCallbackCannotCleanANewerLocalAttempt() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.durableSession = fixture.detachedSession();
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        fixture.workers.runNext();
        StaffModeRecoveryGate gate = fixture.recoveryGate();
        gate.clear(PLAYER_ID);
        Object replacement = gate.beginAttempt(PLAYER_ID);
        fixture.runEntityTasks();
        assertSame(fixture.tool, fixture.contents[0]);
        assertTrue(gate.owns(PLAYER_ID, replacement));
        assertEquals(0, fixture.exitNotifications);
    }

    @Test
    void normalEntryRetriesAHeldRecoveryRatherThanStartingANewSnapshot() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffModeRecoveryGate gate = fixture.recoveryGate();
        gate.retry(PLAYER_ID);
        fixture.manager.enter(fixture.player, net.enthusia.staff.domain.auth.StaffRank.FOUNDER);
        assertFalse(fixture.workers.queued.isEmpty());
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
    }

    @Test
    void normalEntryDoesNotStealAnInFlightAttempt() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffModeRecoveryGate gate = fixture.recoveryGate();
        Object running = gate.beginAttempt(PLAYER_ID);
        fixture.manager.enter(fixture.player, net.enthusia.staff.domain.auth.StaffRank.FOUNDER);
        assertTrue(fixture.workers.queued.isEmpty());
        assertTrue(gate.owns(PLAYER_ID, running));
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
    }

    @Test
    void normalExitRetriesAHeldRecoveryBeforeCheckingLocalActivation() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffModeRecoveryGate gate = fixture.recoveryGate();
        gate.retry(PLAYER_ID);
        fixture.manager.exit(fixture.player);
        assertFalse(fixture.workers.queued.isEmpty());
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
    }

    @Test
    void rejectedDetachedEntitySchedulingCanBeRetried() {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.durableSession = fixture.detachedSession();
        fixture.rejectEntity = true;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        fixture.rejectEntity = false;
        fixture.manager.recover(PLAYER_ID, null);
        assertFalse(fixture.workers.queued.isEmpty());
        fixture.workers.runNext();
        fixture.runEntityTasks();
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertTrue(fixture.closed);
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void detachedClosureCleanupRetriesWithoutReplayingTheClosedSession() {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.durableSession = fixture.detachedSession();
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        fixture.workers.runNext();
        fixture.rejectEntity = true;
        fixture.runEntityTasks();
        assertTrue(fixture.closed);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        fixture.rejectEntity = false;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
        assertEquals(1, fixture.exitNotifications);
    }

    @Test
    void noDurableSessionRemovesOnlyTaggedToolsIncludingOffhandAndCursor() {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertNull(fixture.contents[0]);
        assertNull(fixture.contents[39]);
        assertNull(fixture.contents[40]);
        assertNull(fixture.cursor);
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void inactiveRecoveryKeepsFenceUntilEntityOwnedCleanupFinishes() {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();

        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        fixture.runEntityTasks();
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void unavailableStorageRetainsInventoryAndRecoveryFence() {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.storageAvailable = false;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertSame(fixture.tool, fixture.contents[0]);
        assertSame(fixture.tool, fixture.cursor);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void failedStorageReadDoesNotTreatUnknownSessionAsInactive() {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.failRead = true;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertSame(fixture.tool, fixture.contents[0]);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void newActiveSessionIsNotCleanedByAnOlderInactiveRead() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.activeSessions().put(PLAYER_ID, fixture.session(UUID.randomUUID()));
        fixture.runEntityTasks();

        assertSame(fixture.tool, fixture.contents[0]);
        assertTrue(fixture.manager.active(PLAYER_ID));
    }

    @Test
    void verifiedClosedSnapshotToolsAreRemovedWithoutChangingSavedBytes() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffSessionSnapshot session = fixture.startExit();
        byte[] savedBytes = session.snapshot();
        fixture.completeVerifiedExit(session);

        assertTrue(fixture.closed);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        fixture.runEntityTasks();

        assertNull(fixture.contents[0]);
        assertNull(fixture.cursor);
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
        assertArrayEquals(savedBytes, session.snapshot());
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertFalse(fixture.manager.active(PLAYER_ID));
    }

    @Test
    void rejectedDurableClosureRetainsSnapshotToolsForRecovery() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffSessionSnapshot session = fixture.startExit();
        fixture.closeAccepted = false;
        fixture.completeVerifiedExit(session);
        fixture.runEntityTasks();

        assertFalse(fixture.closed);
        assertSame(fixture.tool, fixture.contents[0]);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void lateClosedSessionCallbackCannotRemoveANewerSessionsTools() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffSessionSnapshot old = fixture.startExit();
        fixture.completeVerifiedExit(old);
        StaffSessionSnapshot replacement = fixture.session(UUID.randomUUID());
        fixture.activeSessions().put(PLAYER_ID, replacement);
        fixture.runEntityTasks();

        assertSame(fixture.tool, fixture.contents[0]);
        assertSame(replacement, fixture.activeSessions().get(PLAYER_ID));
    }

    @Test
    void ordinaryCursorIsPreservedEvenWhenItUsesAStaffToolMaterial() {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.cursor = fixture.ordinaryItem;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertSame(fixture.ordinaryItem, fixture.cursor);
    }

    @Test
    void retiredCleanupRemainsRetryableWithoutReplayingClosedSnapshot() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffSessionSnapshot session = fixture.startExit();
        fixture.rejectEntity = true;
        fixture.completeVerifiedExit(session);
        fixture.runEntityTasks();

        assertTrue(fixture.closed);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertTrue(fixture.manager.active(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);

        fixture.rejectEntity = false;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertNull(fixture.contents[0]);
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertFalse(fixture.manager.active(PLAYER_ID));
    }

    @Test
    void throwingEntitySchedulerKeepsInactiveCleanupFencedAndRetryable() {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        fixture.throwEntity = true;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        fixture.throwEntity = false;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertNull(fixture.contents[0]);
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void throwingEntitySchedulerKeepsClosedCleanupFencedAndRetryable() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffSessionSnapshot session = fixture.startExit();
        fixture.throwEntity = true;
        fixture.completeVerifiedExit(session);
        fixture.runEntityTasks();

        assertTrue(fixture.closed);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertTrue(fixture.manager.active(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        fixture.throwEntity = false;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertNull(fixture.contents[0]);
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertFalse(fixture.manager.active(PLAYER_ID));
    }

    @Test
    void delayedCompletionCannotOwnAReplacementRecoveryAttempt() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffSessionSnapshot old = fixture.startExit();
        StaffModeRecoveryGate gate = fixture.recoveryGate();
        gate.clear(PLAYER_ID);
        Object replacementAttempt = gate.beginAttempt(PLAYER_ID);
        StaffSessionSnapshot replacement = fixture.session(UUID.randomUUID());
        fixture.activeSessions().put(PLAYER_ID, replacement);

        fixture.completeVerifiedExit(old);
        fixture.runEntityTasks();

        assertTrue(fixture.closed);
        assertSame(fixture.tool, fixture.contents[0]);
        assertSame(replacement, fixture.activeSessions().get(PLAYER_ID));
        assertTrue(gate.owns(PLAYER_ID, replacementAttempt));
    }

    @Test
    void lateSchedulerFailureCannotInvalidateAReplacementRecoveryAttempt() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffSessionSnapshot old = fixture.startExit();
        fixture.completeVerifiedExit(old);
        StaffModeRecoveryGate gate = fixture.recoveryGate();
        gate.clear(PLAYER_ID);
        Object replacementAttempt = gate.beginAttempt(PLAYER_ID);
        StaffSessionSnapshot replacement = fixture.session(UUID.randomUUID());
        fixture.activeSessions().put(PLAYER_ID, replacement);
        fixture.throwEntity = true;

        fixture.runEntityTasks();

        assertSame(fixture.tool, fixture.contents[0]);
        assertSame(replacement, fixture.activeSessions().get(PLAYER_ID));
        assertTrue(gate.owns(PLAYER_ID, replacementAttempt));
    }

    @Test
    void exitNotificationCanBeginRecoveryWithoutLosingItsNewFence() throws Exception {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffSessionSnapshot session = fixture.startExit();
        fixture.manager.setExitListener(playerId -> fixture.manager.recover(playerId, null));
        fixture.completeVerifiedExit(session);
        fixture.runEntityTasks();

        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertFalse(fixture.manager.active(PLAYER_ID));
    }

}
