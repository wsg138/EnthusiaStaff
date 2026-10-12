package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.junit.jupiter.api.Test;

class StaffModeCompletedGenericInventoryAuditTest {
    @Test
    void inventoryEnforcementAndDenialsRemainEarlyWhileSuccessWaitsForFinalCancellation() throws Exception {
        assertHandler("onInventoryClick", InventoryClickEvent.class, EventPriority.HIGHEST);
        assertHandler("onInventoryDrag", InventoryDragEvent.class, EventPriority.HIGHEST);
        assertHandler("onCompletedInventoryClick", InventoryClickEvent.class, EventPriority.MONITOR);
        assertHandler("onCompletedInventoryDrag", InventoryDragEvent.class, EventPriority.MONITOR);
    }

    private static void assertHandler(String methodName, Class<?> eventClass, EventPriority priority)
            throws Exception {
        Method callback = StaffModeManager.class.getMethod(methodName, eventClass);
        EventHandler annotation = callback.getAnnotation(EventHandler.class);
        assertNotNull(annotation, methodName);
        assertEquals(priority, annotation.priority(), methodName);
        assertTrue(annotation.ignoreCancelled(), methodName);
    }

    @Test
    void allowedRankMatrixDoesNotOverrideEnderChestOrHelperPolicies() {
        assertTrue(StaffModeManager.shouldAuditCompletedGenericInventoryEdit(false, StaffRank.MOD, false));
        assertTrue(StaffModeManager.shouldAuditCompletedGenericInventoryEdit(false, StaffRank.DEVELOPER, false));
        assertTrue(StaffModeManager.shouldAuditCompletedGenericInventoryEdit(false, StaffRank.ADMIN, false));
        assertTrue(StaffModeManager.shouldAuditCompletedGenericInventoryEdit(false, StaffRank.DEVELOPER, true));
        assertFalse(StaffModeManager.shouldAuditCompletedGenericInventoryEdit(false, StaffRank.ADMIN, true));
        assertFalse(StaffModeManager.shouldAuditCompletedGenericInventoryEdit(false, StaffRank.MOD, true));
        assertFalse(StaffModeManager.shouldAuditCompletedGenericInventoryEdit(false, StaffRank.HELPER, false));
        assertFalse(StaffModeManager.shouldAuditCompletedGenericInventoryEdit(false, StaffRank.SYSTEM, false));
        assertFalse(StaffModeManager.shouldAuditCompletedGenericInventoryEdit(false, null, false));
        assertTrue(StaffModeManager.shouldAuditCompletedGenericInventoryEdit(true, StaffRank.HELPER, true));
    }

    @Test
    void successfulGenericEventsExcludeNoOpsAndAlreadyOwnedContainerMutations() {
        assertTrue(StaffModeManager.shouldAuditGenericInventoryClick(InventoryAction.PICKUP_ALL, false));
        assertFalse(StaffModeManager.shouldAuditGenericInventoryClick(InventoryAction.NOTHING, false));
        assertFalse(StaffModeManager.shouldAuditGenericInventoryClick(InventoryAction.PICKUP_ALL, true));
        assertFalse(StaffModeManager.shouldAuditGenericInventoryClick(null, false));
    }
}
