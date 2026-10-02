package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StaffModeDeathPolicyTest {
    @Test
    void offDutyDeathsAreIgnored() {
        assertEquals(
                StaffModeDeathPolicy.Action.IGNORE,
                StaffModeDeathPolicy.decide(false, false)
        );
    }

    @Test
    void usableAuthorityContainsDeathAndBeginsDurableExit() {
        assertEquals(
                StaffModeDeathPolicy.Action.CONTAIN_AND_EXIT,
                StaffModeDeathPolicy.decide(true, true)
        );
    }

    @Test
    void lifecycleOwnedTransitionContainsWithoutStartingACompetingExit() {
        assertEquals(
                StaffModeDeathPolicy.Action.CONTAIN,
                StaffModeDeathPolicy.decide(true, false)
        );
    }
}
