package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.junit.jupiter.api.Test;

class StaffModeCompletedWorldUseAuditTest {
    @Test
    void everySuccessfulWorldUseIsAuditedOnlyAfterOtherCancellationHandlers() throws Exception {
        List<Handler> handlers = List.of(
                new Handler("onCompletedBucketFill", PlayerBucketFillEvent.class),
                new Handler("onCompletedBucketEmpty", PlayerBucketEmptyEvent.class),
                new Handler("onCompletedHarvest", PlayerHarvestBlockEvent.class),
                new Handler("onCompletedShear", PlayerShearEntityEvent.class),
                new Handler("onCompletedConsume", PlayerItemConsumeEvent.class),
                new Handler("onCompletedFish", PlayerFishEvent.class)
        );
        for (Handler h : handlers) {
            Method callback = StaffModeWorldInteractionListener.class.getMethod(h.name(), h.eventClass());
            EventHandler annotation = callback.getAnnotation(EventHandler.class);
            assertNotNull(annotation, h.name());
            assertEquals(EventPriority.MONITOR, annotation.priority(), h.name());
            assertTrue(annotation.ignoreCancelled(), h.name());
        }
    }

    @Test
    void completedAuditPreservesAllExistingDutyTierRestrictions() {
        assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedWorldUse(
                false, StaffDutyTier.ADMIN));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedWorldUse(
                false, StaffDutyTier.DEVELOPER));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedWorldUse(
                false, StaffDutyTier.MOD));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedWorldUse(
                false, StaffDutyTier.HELPER));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedWorldUse(
                false, null));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditCompletedWorldUse(
                true, StaffDutyTier.HELPER));
    }

    private record Handler(String name, Class<?> eventClass) { }
}
