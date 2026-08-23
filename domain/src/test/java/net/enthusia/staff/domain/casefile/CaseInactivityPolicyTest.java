package net.enthusia.staff.domain.casefile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CaseInactivityPolicyTest {
    private static final Instant LAST_ACTIVITY = Instant.parse("2026-07-24T12:00:00Z");

    @Test
    void openCaseClosesAtThirtyDaysWithoutActivity() {
        CaseInactivityPolicy policy = new CaseInactivityPolicy();

        assertFalse(policy.shouldClose(
                CaseState.OPEN,
                LAST_ACTIVITY,
                LAST_ACTIVITY.plus(CaseInactivityPolicy.DEFAULT_INACTIVITY).minusSeconds(1)
        ));
        assertTrue(policy.shouldClose(
                CaseState.OPEN,
                LAST_ACTIVITY,
                LAST_ACTIVITY.plus(CaseInactivityPolicy.DEFAULT_INACTIVITY)
        ));
    }

    @Test
    void terminalCaseIsNotAutoClosedAgain() {
        CaseInactivityPolicy policy = new CaseInactivityPolicy();
        Instant later = LAST_ACTIVITY.plus(CaseInactivityPolicy.DEFAULT_INACTIVITY).plusSeconds(1);

        assertFalse(policy.shouldClose(CaseState.CLOSED, LAST_ACTIVITY, later));
        assertFalse(policy.shouldClose(CaseState.FULLY_OVERTURNED, LAST_ACTIVITY, later));
    }

    @Test
    void missingActivityTimestampFailsClosedForAnyState() {
        CaseInactivityPolicy policy = new CaseInactivityPolicy();

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.shouldClose(CaseState.CLOSED, null, LAST_ACTIVITY)
        );
    }
}
