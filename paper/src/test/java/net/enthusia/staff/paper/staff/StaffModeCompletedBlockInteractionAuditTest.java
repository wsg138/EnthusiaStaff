package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

class StaffModeCompletedBlockInteractionAuditTest {
    @Test
    void enforcementStillRunsBeforeCancellationAndOnlySuccessfulEventsReachAudit() throws Exception {
        Method enforce = StaffModeWorldInteractionListener.class.getMethod(
                "onInteract", PlayerInteractEvent.class);
        Method audit = StaffModeWorldInteractionListener.class.getMethod(
                "onCompletedInteract", PlayerInteractEvent.class);
        EventHandler enforcement = enforce.getAnnotation(EventHandler.class);
        EventHandler completed = audit.getAnnotation(EventHandler.class);
        assertNotNull(enforcement);
        assertNotNull(completed);
        assertEquals(EventPriority.HIGHEST, enforcement.priority());
        assertTrue(enforcement.ignoreCancelled());
        assertEquals(EventPriority.MONITOR, completed.priority());
        assertTrue(completed.ignoreCancelled());
    }

    @Test
    void allowedWorldUsesAreAuditedButAirAndDeniedActionsAreNot() {
        for (StaffDutyTier tier : new StaffDutyTier[] {
                StaffDutyTier.MOD, StaffDutyTier.DEVELOPER, StaffDutyTier.ADMIN
        }) {
            assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedBlockInteraction(
                    false, tier, Action.RIGHT_CLICK_BLOCK));
            assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedBlockInteraction(
                    false, tier, Action.LEFT_CLICK_BLOCK));
            assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedBlockInteraction(
                    false, tier, Action.RIGHT_CLICK_AIR));
            assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedBlockInteraction(
                    false, tier, Action.LEFT_CLICK_AIR));
        }
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedBlockInteraction(
                false, StaffDutyTier.HELPER, Action.RIGHT_CLICK_BLOCK));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedBlockInteraction(
                false, null, Action.RIGHT_CLICK_BLOCK));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedBlockInteraction(
                true, StaffDutyTier.HELPER, Action.RIGHT_CLICK_BLOCK));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedBlockInteraction(
                true, StaffDutyTier.ADMIN, Action.RIGHT_CLICK_AIR));
    }

    @Test
    void helperContainerViewsAreAuditedOnlyForPermittedBlocks() {
        assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedContainerView(
                StaffDutyTier.HELPER, Action.RIGHT_CLICK_BLOCK, true));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedContainerView(
                StaffDutyTier.HELPER, Action.RIGHT_CLICK_BLOCK, false));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedContainerView(
                StaffDutyTier.HELPER, Action.LEFT_CLICK_BLOCK, true));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedContainerView(
                StaffDutyTier.MOD, Action.RIGHT_CLICK_BLOCK, true));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedContainerView(
                null, Action.RIGHT_CLICK_BLOCK, true));
    }
}
