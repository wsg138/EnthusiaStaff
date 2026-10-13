package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

class StaffModeCompletedDamageAuditTest {
    @Test
    void combatDenialStaysAtHighestWhileOnlyFinalNonCancelledEventsLogSuccess() throws Exception {
        assertHandler("onDamage", EntityDamageEvent.class, EventPriority.HIGHEST);
        assertHandler("onDamageByEntity", EntityDamageByEntityEvent.class, EventPriority.HIGHEST);
        assertHandler("onCompletedDamage", EntityDamageEvent.class, EventPriority.MONITOR);
        assertHandler("onCompletedDamageByEntity", EntityDamageByEntityEvent.class, EventPriority.MONITOR);
    }

    private static void assertHandler(String name, Class<?> type, EventPriority priority) throws Exception {
        Method method = StaffModeManager.class.getMethod(name, type);
        EventHandler annotation = method.getAnnotation(EventHandler.class);
        assertNotNull(annotation, name);
        assertEquals(priority, annotation.priority(), name);
        assertTrue(annotation.ignoreCancelled(), name);
    }

    @Test
    void combatPermissionSemanticsRemainDeveloperOrExplicitUnrestricted() {
        for (StaffRank rank : new StaffRank[] {
                StaffRank.HELPER, StaffRank.MOD, StaffRank.ADMIN, StaffRank.FOUNDER, StaffRank.SYSTEM
        }) {
            assertFalse(StaffModeManager.permitsCombatAction(false, rank));
            assertTrue(StaffModeManager.permitsCombatAction(true, rank));
        }
        assertFalse(StaffModeManager.permitsCombatAction(false, null));
        assertTrue(StaffModeManager.permitsCombatAction(false, StaffRank.DEVELOPER));
        assertTrue(StaffModeManager.permitsCombatAction(true, null));
    }
}
