package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
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
    void actualPaperFrameMutationOwnsSuccessInsteadOfGenericOrPreciseRightClick() throws Exception {
        assertHandler("onCompletedItemFrameChange", PlayerItemFrameChangeEvent.class, EventPriority.MONITOR);
        // The dedicated Paper event describes a frame mutation; generic right-click
        // events can also happen when no item is present and the frame does not change.
        var actions = java.util.EnumSet.allOf(
                PlayerItemFrameChangeEvent.ItemFrameChangeAction.class);
        assertEquals(3, actions.size());
        assertTrue(actions.contains(PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE));
        assertTrue(actions.contains(PlayerItemFrameChangeEvent.ItemFrameChangeAction.ROTATE));
        assertTrue(actions.contains(PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE));
    }
}
