package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import org.junit.jupiter.api.Test;

final class VelocityStaffDutyContextTest {
    @Test
    void grantsContextOnlyForActiveSessionOnCurrentBackend() {
        assertTrue(VelocityStaffDutyContext.matchesActiveSession(
                Optional.of(session(StaffSessionState.ACTIVE)), "smp"
        ));
        assertFalse(VelocityStaffDutyContext.matchesActiveSession(
                Optional.of(session(StaffSessionState.ENTERING)), "SMP"
        ));
        assertFalse(VelocityStaffDutyContext.matchesActiveSession(
                Optional.of(session(StaffSessionState.EXITING)), "SMP"
        ));
        assertFalse(VelocityStaffDutyContext.matchesActiveSession(
                Optional.of(session(StaffSessionState.RECOVERY_REQUIRED)), "SMP"
        ));
        assertFalse(VelocityStaffDutyContext.matchesActiveSession(
                Optional.of(session(StaffSessionState.ACTIVE)), "HUB"
        ));
        assertFalse(VelocityStaffDutyContext.matchesActiveSession(Optional.empty(), "SMP"));
    }

    private static StaffSessionSnapshot session(StaffSessionState state) {
        return new StaffSessionSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), "SMP", state, false, 1,
                "a".repeat(64), new byte[]{1}, Instant.now(), 1L
        );
    }
}
