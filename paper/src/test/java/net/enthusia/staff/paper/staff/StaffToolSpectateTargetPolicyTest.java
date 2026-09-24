package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StaffToolSpectateTargetPolicyTest {
    @Test
    void targetMustRemainVisibleAndNonExempt() {
        assertTrue(StaffToolSpectateTargetPolicy.eligible(false, false));
        assertFalse(StaffToolSpectateTargetPolicy.eligible(true, false));
        assertFalse(StaffToolSpectateTargetPolicy.eligible(false, true));
        assertFalse(StaffToolSpectateTargetPolicy.eligible(true, true));
    }
}
