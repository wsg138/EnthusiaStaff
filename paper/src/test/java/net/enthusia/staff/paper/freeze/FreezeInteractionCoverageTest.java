package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.junit.jupiter.api.Test;

class FreezeInteractionCoverageTest {
    @Test
    void declaresExplicitHandlersForPreciseAndResourceInteractions() {
        assertHighestPriorityCancellationHandler(PlayerInteractAtEntityEvent.class);
        assertHighestPriorityCancellationHandler(PlayerArmorStandManipulateEvent.class);
        assertHighestPriorityCancellationHandler(PlayerHarvestBlockEvent.class);
        assertHighestPriorityCancellationHandler(PlayerShearEntityEvent.class);
        assertHighestPriorityCancellationHandler(PlayerFishEvent.class);
    }

    private static void assertHighestPriorityCancellationHandler(Class<? extends Event> eventType) {
        Method method = Arrays.stream(FreezeManager.class.getDeclaredMethods())
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{eventType}))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing explicit handler for " + eventType.getSimpleName()));
        EventHandler handler = method.getAnnotation(EventHandler.class);
        assertNotNull(handler, () -> method.getName() + " must be registered as an event handler");
        assertEquals(EventPriority.HIGHEST, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }
}
