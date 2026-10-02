package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import org.junit.jupiter.api.Test;

class StaffModeReconnectCoordinatorTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID STALE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void recoveredOwnerMayContinueToOriginalDestinationOnce() {
        StaffModeReconnectCoordinator coordinator = new StaffModeReconnectCoordinator();
        coordinator.remember(PLAYER, session(), "HUB", NOW);

        assertEquals("HUB", coordinator.claimDestination(PLAYER, SESSION, "SMP", "SMP", NOW).orElseThrow());
        assertTrue(coordinator.claimDestination(PLAYER, SESSION, "SMP", "SMP", NOW).isEmpty());
    }

    @Test
    void staleReadyDoesNotEraseNewerPendingReconnect() {
        StaffModeReconnectCoordinator coordinator = new StaffModeReconnectCoordinator();
        coordinator.remember(PLAYER, session(), "HUB", NOW);

        assertTrue(coordinator.claimDestination(PLAYER, STALE, "SMP", "SMP", NOW).isEmpty());
        assertEquals("HUB", coordinator.claimDestination(PLAYER, SESSION, "SMP", "SMP", NOW).orElseThrow());
    }

    @Test
    void wrongOwnerCurrentServerAndExpiryFailClosed() {
        StaffModeReconnectCoordinator coordinator = new StaffModeReconnectCoordinator();
        coordinator.remember(PLAYER, session(), "HUB", NOW);

        assertTrue(coordinator.claimDestination(PLAYER, SESSION, "TEST", "SMP", NOW).isEmpty());
        assertTrue(coordinator.claimDestination(PLAYER, SESSION, "SMP", "TEST", NOW).isEmpty());
        assertTrue(coordinator.claimDestination(PLAYER, SESSION, "SMP", "SMP", NOW.plusSeconds(31)).isEmpty());
    }

    @Test
    void recoverySessionIsNeverScheduledForPortableReconnect() {
        StaffModeReconnectCoordinator coordinator = new StaffModeReconnectCoordinator();
        StaffSessionSnapshot recovery = new StaffSessionSnapshot(
                SESSION, PLAYER, "SMP", StaffSessionState.RECOVERY_REQUIRED, true, 1,
                "a".repeat(64), new byte[]{1}, NOW, 2L);

        coordinator.remember(PLAYER, recovery, "HUB", NOW);

        assertTrue(coordinator.claimDestination(PLAYER, SESSION, "SMP", "SMP", NOW).isEmpty());
    }

    private static StaffSessionSnapshot session() {
        return new StaffSessionSnapshot(
                SESSION, PLAYER, "SMP", StaffSessionState.ACTIVE, true, 1,
                "a".repeat(64), new byte[]{1}, NOW, 2L);
    }
}
