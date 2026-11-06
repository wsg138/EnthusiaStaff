package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StaffRanklessHandoffJoinTest {
    @ParameterizedTest
    @EnumSource(value = StaffSessionState.class, names = {"ACTIVE", "EXITING", "RECOVERY_REQUIRED"})
    void ranklessJoinWithHandoffHintRetiresEligibleDetachedLease(StaffSessionState state) {
        StaffModeRecoveryFixture fixture = joiningFixture(state);
        byte[] snapshot = fixture.durableSession.snapshot();

        fixture.manager.onJoin(new PlayerJoinEvent(fixture.player, (Component) null));

        assertFalse(fixture.workers.queued.isEmpty(), "handoff hint must not bypass durable recovery");
        assertSame(fixture.tool, fixture.contents[0], "join must not remove tools before durable closure");
        drainRecovery(fixture);

        assertTrue(fixture.closed);
        assertFalse(fixture.manager.active(StaffModeRecoveryFixture.PLAYER_ID));
        assertFalse(fixture.manager.transitioning(StaffModeRecoveryFixture.PLAYER_ID));
        assertNull(fixture.contents[0]);
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
        assertArrayEquals(snapshot, fixture.durableSession.snapshot());
    }

    @Test
    void ranklessHintDoesNotCloseInvalidDetachedTransition() {
        StaffModeRecoveryFixture fixture = joiningFixture(StaffSessionState.ENTERING);

        fixture.manager.onJoin(new PlayerJoinEvent(fixture.player, (Component) null));
        drainRecovery(fixture);

        assertFalse(fixture.closed);
        assertTrue(fixture.manager.transitioning(StaffModeRecoveryFixture.PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
    }

    @Test
    void unavailableStorageAfterRanklessHintKeepsRecoveryRetryable() throws Exception {
        StaffModeRecoveryFixture fixture = joiningFixture(StaffSessionState.ACTIVE);
        fixture.storageAvailable = false;

        fixture.manager.onJoin(new PlayerJoinEvent(fixture.player, (Component) null));
        drainRecovery(fixture);

        assertFalse(fixture.closed);
        assertTrue(fixture.recoveryGate().retryable(StaffModeRecoveryFixture.PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        fixture.storageAvailable = true;
        fixture.manager.recover(fixture.player);
        drainRecovery(fixture);
        assertTrue(fixture.closed);
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
    }

    @Test
    void rejectedDetachedRetirementAfterJoinPreservesInventoryAndRetry() throws Exception {
        StaffModeRecoveryFixture fixture = joiningFixture(StaffSessionState.ACTIVE);
        byte[] snapshot = fixture.durableSession.snapshot();
        fixture.closeAccepted = false;

        fixture.manager.onJoin(new PlayerJoinEvent(fixture.player, (Component) null));
        drainRecovery(fixture);

        assertFalse(fixture.closed);
        assertTrue(fixture.recoveryGate().retryable(StaffModeRecoveryFixture.PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
        assertArrayEquals(snapshot, fixture.durableSession.snapshot());
    }

    private static StaffModeRecoveryFixture joiningFixture(StaffSessionState state) {
        StaffModeRecoveryFixture fixture = new StaffModeRecoveryFixture();
        StaffSessionSnapshot detached = fixture.detachedSession();
        fixture.durableSession = new StaffSessionSnapshot(detached.sessionId(), detached.staffId(),
                detached.serverId(), state, false, detached.schemaVersion(), detached.checksum(),
                detached.snapshot(), detached.startedAt(), detached.revision());
        assertTrue(fixture.manager.prepareBackendHandoffResume(
                StaffModeRecoveryFixture.PLAYER_ID, UUID.randomUUID()));
        return fixture;
    }

    private static void drainRecovery(StaffModeRecoveryFixture fixture) {
        for (int step = 0; step < 5; step++) {
            if (!fixture.workers.queued.isEmpty()) {
                fixture.workers.runNext();
            }
            fixture.runEntityTasks();
        }
        assertTrue(fixture.workers.queued.isEmpty(), "recovery must finish within the bounded fixture");
    }
}
