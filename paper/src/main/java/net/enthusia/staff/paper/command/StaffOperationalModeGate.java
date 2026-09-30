package net.enthusia.staff.paper.command;

import net.enthusia.staff.domain.OperationalMode;

/** Operational-mode boundary for non-enforcement staff state used during shadow acceptance. */
final class StaffOperationalModeGate {
    private StaffOperationalModeGate() {
    }

    static boolean staffModeTransitionAllowed(OperationalMode mode, boolean activeSession) {
        return activeSession || nonEnforcementStateChangeAllowed(mode);
    }

    static boolean vanishChangeAllowed(OperationalMode mode, boolean currentlyVanished) {
        return currentlyVanished || nonEnforcementStateChangeAllowed(mode);
    }

    private static boolean nonEnforcementStateChangeAllowed(OperationalMode mode) {
        return mode == OperationalMode.ACTIVE || mode == OperationalMode.SHADOW_MIGRATION;
    }
}
