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

class StaffModePairedEntityInteractionAuditTest {
    @Test
    void enforcesAtHighestButRecordsSuccessOnlyAfterCancellation() throws Exception {
        assertHandler("onInteractEntity", PlayerInteractEntityEvent.class, EventPriority.HIGHEST);
        assertHandler("onInteractAtEntity", PlayerInteractAtEntityEvent.class, EventPriority.HIGHEST);
        assertHandler("onCompletedInteractEntity", PlayerInteractEntityEvent.class, EventPriority.MONITOR);
        assertHandler("onCompletedInteractAtEntity", PlayerInteractAtEntityEvent.class, EventPriority.MONITOR);
    }

    private static void assertHandler(String method, Class<?> type, EventPriority priority) throws Exception {
        Method callback = StaffModeWorldInteractionListener.class.getMethod(method, type);
        EventHandler annotation = callback.getAnnotation(EventHandler.class);
        assertNotNull(annotation, method);
        assertEquals(priority, annotation.priority(), method);
        assertTrue(annotation.ignoreCancelled(), method);
    }

    @Test
    void oneGenericAndOnePreciseVariantAreAuditedOnceInEitherOrder() {
        var audit = new StaffModeWorldInteractionListener.PairedEntityInteractionAudit();
        UUID player = UUID.randomUUID(), entity = UUID.randomUUID();
        assertTrue(audit.allow(player, entity, EquipmentSlot.HAND, false, 1_000_000L));
        assertFalse(audit.allow(player, entity, EquipmentSlot.HAND, true, 2_000_000L));
        assertTrue(audit.allow(player, entity, EquipmentSlot.HAND, false, 3_000_000L),
                "A second genuine generic action must not be suppressed by a prior generic action");

        var reversed = new StaffModeWorldInteractionListener.PairedEntityInteractionAudit();
        assertTrue(reversed.allow(player, entity, EquipmentSlot.HAND, true, 1_000_000L));
        assertFalse(reversed.allow(player, entity, EquipmentSlot.HAND, false, 2_000_000L));
    }

    @Test
    void standaloneOrDistinctInteractionsRetainIndividualAuditRecords() {
        var audit = new StaffModeWorldInteractionListener.PairedEntityInteractionAudit();
        UUID player = UUID.randomUUID(), entity = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(audit.allow(player, entity, EquipmentSlot.HAND, true, 1_000_000L),
                "A standalone precise-only interaction is not lost");
        assertTrue(audit.allow(player, entity, EquipmentSlot.HAND, true, 2_000_000L),
                "Two distinct precise actions are still recorded");
        assertTrue(audit.allow(player, other, EquipmentSlot.HAND, false, 3_000_000L));
        assertTrue(audit.allow(player, other, EquipmentSlot.OFF_HAND, true, 4_000_000L));
        assertTrue(audit.allow(player, other, EquipmentSlot.HAND, true, 20_000_000L),
                "A later click outside the pairing window is not suppressed");
        assertTrue(audit.allow(other, other, EquipmentSlot.HAND, false, 21_000_000L),
                "Separate players cannot suppress each other's records");
    }

    @Test
    void timeWindowHandlesClockResetAndOnlyCoalescesImmediateCrossVariants() {
        var audit = new StaffModeWorldInteractionListener.PairedEntityInteractionAudit();
        UUID player = UUID.randomUUID(), entity = UUID.randomUUID();
        assertTrue(audit.allow(player, entity, EquipmentSlot.HAND, false, 10_000_000L));
        assertTrue(audit.allow(player, entity, EquipmentSlot.HAND, true, 5_000_000L),
                "A clock rollback does not erase another independent event");
        assertFalse(audit.allow(player, entity, EquipmentSlot.HAND, false, 6_000_000L));
        assertTrue(audit.allow(player, entity, EquipmentSlot.HAND, false, 30_000_000L));
    }

    @Test
    void actorCacheHasAHardBoundAndLogoutForgetsIdentity() {
        var audit = new StaffModeWorldInteractionListener.PairedEntityInteractionAudit();
        UUID first = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        assertTrue(audit.allow(first, entity, EquipmentSlot.HAND, false, 0));
        for (int i = 0; i < 270; i++) {
            assertTrue(audit.allow(UUID.randomUUID(), entity, EquipmentSlot.HAND, false, i));
        }
        assertEquals(256, audit.trackedActors());
        audit.forget(first);
        assertTrue(audit.trackedActors() <= 256);
        audit.forget(UUID.randomUUID());
        assertTrue(audit.trackedActors() <= 256);
    }
}
