package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StaffToolDispatchAuditTest {
    @Test
    void toolAuditDetailIsStableAndContainsNoTargetIdentifier() {
        String detail = StaffToolDispatcher.toolInvocationDetail(
                StaffToolDefinition.PLAYER_INSPECTOR, true);
        assertEquals("tool=PLAYER_INSPECTOR target=supplied", detail);
        assertFalse(detail.contains("12345678"));
        assertFalse(detail.contains("username"));
    }

    @Test
    void targetFreeToolsHaveExplicitNoTargetMetadata() {
        assertEquals("tool=REPORTS target=none",
                StaffToolDispatcher.toolInvocationDetail(StaffToolDefinition.REPORTS, false));
        assertEquals("tool=CHEAT_TESTER target=supplied",
                StaffToolDispatcher.toolInvocationDetail(StaffToolDefinition.CHEAT_TESTER, true));
    }

    @Test
    void allToolTypesProduceBoundedNonSensitiveDescriptors() {
        for (StaffToolDefinition tool : StaffToolDefinition.values()) {
            String detail = StaffToolDispatcher.toolInvocationDetail(tool, tool.targetRequired());
            assertTrue(detail.startsWith("tool=" + tool.name() + " target="));
            assertTrue(detail.length() < 100);
        }
        assertThrows(NullPointerException.class,
                () -> StaffToolDispatcher.toolInvocationDetail(null, true));
    }
}
