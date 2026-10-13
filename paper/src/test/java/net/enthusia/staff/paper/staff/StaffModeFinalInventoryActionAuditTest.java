package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.junit.jupiter.api.Test;

class StaffModeFinalInventoryActionAuditTest {
    @Test
    void deniedEnforcementStaysAtHighestAndSuccessOnlyAtMonitor() throws Exception {
        assertHandler("onDrop", PlayerDropItemEvent.class, EventPriority.HIGHEST);
        assertHandler("onCompletedDrop", PlayerDropItemEvent.class, EventPriority.MONITOR);
        assertHandler("onPickup", EntityPickupItemEvent.class, EventPriority.HIGHEST);
        assertHandler("onCompletedPickup", EntityPickupItemEvent.class, EventPriority.MONITOR);
        assertHandler("onSwapHands", PlayerSwapHandItemsEvent.class, EventPriority.HIGHEST);
        assertHandler("onCompletedSwapHands", PlayerSwapHandItemsEvent.class, EventPriority.MONITOR);
    }

    private static void assertHandler(String method, Class<?> type, EventPriority priority) throws Exception {
        Method handler = StaffModeManager.class.getMethod(method, type);
        EventHandler annotation = handler.getAnnotation(EventHandler.class);
        assertNotNull(annotation, method);
        assertEquals(priority, annotation.priority(), method);
        assertTrue(annotation.ignoreCancelled(), method);
    }

    @Test
    void preservesExistingRankAndUnrestrictedBehavior() {
        assertFalse(StaffModeManager.permitsInventoryMove(false, null));
        assertFalse(StaffModeManager.permitsInventoryMove(false, StaffRank.HELPER));
        assertFalse(StaffModeManager.permitsInventoryMove(false, StaffRank.SYSTEM));
        for (StaffRank rank : new StaffRank[] {
                StaffRank.MOD, StaffRank.DEVELOPER, StaffRank.ADMIN, StaffRank.FOUNDER
        }) {
            assertTrue(StaffModeManager.permitsInventoryMove(false, rank));
        }
        assertTrue(StaffModeManager.permitsInventoryMove(true, null));
        assertTrue(StaffModeManager.permitsInventoryMove(true, StaffRank.HELPER));
    }
}
