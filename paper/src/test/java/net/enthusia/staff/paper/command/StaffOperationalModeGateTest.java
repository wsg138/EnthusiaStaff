package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.OperationalMode;
import org.junit.jupiter.api.Test;

class StaffOperationalModeGateTest {
    @Test
    void staffModeEntryIsAvailableDuringActiveAndShadowOnly() {
        assertTrue(StaffOperationalModeGate.staffModeTransitionAllowed(OperationalMode.ACTIVE, false));
        assertTrue(StaffOperationalModeGate.staffModeTransitionAllowed(OperationalMode.SHADOW_MIGRATION, false));
        assertFalse(StaffOperationalModeGate.staffModeTransitionAllowed(OperationalMode.BOOTSTRAP, false));
        assertFalse(StaffOperationalModeGate.staffModeTransitionAllowed(OperationalMode.DEGRADED, false));
        assertFalse(StaffOperationalModeGate.staffModeTransitionAllowed(OperationalMode.MAINTENANCE, false));
        assertFalse(StaffOperationalModeGate.staffModeTransitionAllowed(OperationalMode.READ_ONLY_FAILURE, false));
    }

    @Test
    void activeStaffSessionMayAlwaysExit() {
        for (OperationalMode mode : OperationalMode.values()) {
            assertTrue(StaffOperationalModeGate.staffModeTransitionAllowed(mode, true));
        }
    }

    @Test
    void vanishEnableIsAvailableDuringActiveAndShadowOnly() {
        assertTrue(StaffOperationalModeGate.vanishChangeAllowed(OperationalMode.ACTIVE, false));
        assertTrue(StaffOperationalModeGate.vanishChangeAllowed(OperationalMode.SHADOW_MIGRATION, false));
        assertFalse(StaffOperationalModeGate.vanishChangeAllowed(OperationalMode.DEGRADED, false));
        assertFalse(StaffOperationalModeGate.vanishChangeAllowed(OperationalMode.MAINTENANCE, false));
        assertFalse(StaffOperationalModeGate.vanishChangeAllowed(OperationalMode.READ_ONLY_FAILURE, false));
    }

    @Test
    void existingVanishMayAlwaysBeDisabled() {
        for (OperationalMode mode : OperationalMode.values()) {
            assertTrue(StaffOperationalModeGate.vanishChangeAllowed(mode, true));
        }
    }
}
