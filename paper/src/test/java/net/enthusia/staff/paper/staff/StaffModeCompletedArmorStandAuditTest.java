package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.junit.jupiter.api.Test;

class StaffModeCompletedArmorStandAuditTest {
    @Test
    void rankEnforcementPrecedesFinalSuccessfulEquipmentEditAudit() throws Exception {
        Method enforce = StaffModeWorldInteractionListener.class.getMethod(
                "onArmorStandManipulate", PlayerArmorStandManipulateEvent.class);
        Method successful = StaffModeWorldInteractionListener.class.getMethod(
                "onCompletedArmorStandManipulate", PlayerArmorStandManipulateEvent.class);
        EventHandler first = enforce.getAnnotation(EventHandler.class);
        EventHandler last = successful.getAnnotation(EventHandler.class);
        assertNotNull(first);
        assertNotNull(last);
        assertEquals(EventPriority.HIGHEST, first.priority());
        assertTrue(first.ignoreCancelled());
        assertEquals(EventPriority.MONITOR, last.priority());
        assertTrue(last.ignoreCancelled());
    }

    @Test
    void authorizedEditsAreAuditedWhileHelperAndUnknownTiersRemainDenied() {
        assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedArmorStandManipulate(
                false, StaffDutyTier.MOD));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedArmorStandManipulate(
                false, StaffDutyTier.DEVELOPER));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedArmorStandManipulate(
                false, StaffDutyTier.ADMIN));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedArmorStandManipulate(
                false, StaffDutyTier.HELPER));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedArmorStandManipulate(
                false, null));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedArmorStandManipulate(
                true, StaffDutyTier.HELPER));
    }
}
