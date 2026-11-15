package net.enthusia.staff.paper.safetycheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** No server, player data, credentials or outside HTTP required. */
final class PrivateSafetyScopeTest {
    @Test
    void onlyTheExactPhysicalServerScopeMayAccessSafetyChecks() {
        assertEquals("smp", PrivateSafetyCheckBridge.verifiedScope("smp", "smp"));
        assertThrows(IllegalArgumentException.class,
                () -> PrivateSafetyCheckBridge.verifiedScope("smp", "other"));
        assertThrows(IllegalArgumentException.class,
                () -> PrivateSafetyCheckBridge.verifiedScope("smp", ""));
        assertThrows(IllegalArgumentException.class,
                () -> PrivateSafetyCheckBridge.verifiedScope("", "smp"));
    }

    @Test
    void trailingWhitespaceDoesNotGrantCrossScopeAccess() {
        assertEquals("smp", PrivateSafetyCheckBridge.verifiedScope(" smp ", "smp"));
        assertThrows(IllegalArgumentException.class,
                () -> PrivateSafetyCheckBridge.verifiedScope("smp1", "smp"));
    }
}
