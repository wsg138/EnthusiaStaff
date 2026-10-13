package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

class StaffModeCompletedContainerEntityAuditTest {
    @Test
    void itemFrameAllowedSuccessIsOwnedByFinalCancellationAwareHandlers() throws Exception {
        assertHandler("onInteractEntity", PlayerInteractEntityEvent.class, EventPriority.HIGHEST);
        assertHandler("onInteractAtEntity", PlayerInteractAtEntityEvent.class, EventPriority.HIGHEST);
        assertHandler("onCompletedInteractEntity", PlayerInteractEntityEvent.class, EventPriority.MONITOR);
        assertHandler("onCompletedInteractAtEntity", PlayerInteractAtEntityEvent.class, EventPriority.MONITOR);
    }

    private static void assertHandler(String name, Class<?> event, EventPriority priority) throws Exception {
        Method method = StaffModeWorldInteractionListener.class.getMethod(name, event);
        EventHandler annotation = method.getAnnotation(EventHandler.class);
        assertNotNull(annotation);
        assertEquals(priority, annotation.priority());
        assertTrue(annotation.ignoreCancelled(), "Cancelled interactions cannot create successful edit records");
    }

    @Test
    void completedFramePolicyKeepsRankAndUnrestrictedSemantics() {
        for (StaffDutyTier tier : new StaffDutyTier[] {
                StaffDutyTier.MOD, StaffDutyTier.DEVELOPER, StaffDutyTier.ADMIN
        }) {
            assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedContainerEntityEdit(false, tier));
        }
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedContainerEntityEdit(
                false, StaffDutyTier.HELPER));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedContainerEntityEdit(false, null));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedContainerEntityEdit(
                true, StaffDutyTier.HELPER));
    }

    @Test
    void acceptedFrameVariantsShareTheBoundedCrossVariantDeduplication() {
        var audit = new StaffModeWorldInteractionListener.PairedEntityInteractionAudit();
        UUID actor = UUID.randomUUID();
        UUID itemFrame = UUID.randomUUID();
        assertTrue(audit.allow(actor, itemFrame, EquipmentSlot.HAND, false, 1_000_000L));
        assertFalse(audit.allow(actor, itemFrame, EquipmentSlot.HAND, true, 2_000_000L));
        assertTrue(audit.allow(actor, itemFrame, EquipmentSlot.HAND, false, 3_000_000L),
                "A second distinct same-variant click must still log");
        assertTrue(audit.allow(actor, itemFrame, EquipmentSlot.OFF_HAND, true, 4_000_000L),
                "Offhand events are independent");
        assertTrue(audit.allow(actor, itemFrame, EquipmentSlot.HAND, true, 20_000_000L),
                "Interactions outside the coalescing window are independent");
    }
}
