package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.junit.jupiter.api.Test;

class FreezeInteractionCoverageTest {
    private static final UUID PLAYER_ID = UUID.fromString("66702d67-a72e-4485-aa83-14ce402878e6");

    @Test
    void declaresExplicitHandlersForPreciseAndResourceInteractions() {
        assertHighestPriorityCancellationHandler(PlayerInteractAtEntityEvent.class);
        assertHighestPriorityCancellationHandler(PlayerArmorStandManipulateEvent.class);
        assertHighestPriorityCancellationHandler(PlayerHarvestBlockEvent.class);
        assertHighestPriorityCancellationHandler(PlayerShearEntityEvent.class);
        assertHighestPriorityCancellationHandler(PlayerFishEvent.class);
    }

    @Test
    void restrictedPlayersCannotUseAnyCoveredInteraction() {
        FreezeManager manager = manager();
        manager.verify(PLAYER_ID, "RestrictedPlayer");

        for (InteractionCase interaction : interactions(player())) {
            assertFalse(interaction.event().isCancelled());
            interaction.dispatch().accept(manager);
            assertTrue(interaction.event().isCancelled(), interaction.name());
        }
    }

    @Test
    void ordinaryPlayersKeepAllCoveredInteractions() {
        FreezeManager manager = manager();

        for (InteractionCase interaction : interactions(player())) {
            assertFalse(interaction.event().isCancelled());
            interaction.dispatch().accept(manager);
            assertFalse(interaction.event().isCancelled(), interaction.name());
        }
    }

    private static FreezeManager manager() {
        return new FreezeManager(null, Clock.systemUTC(), () -> null, proxy(ExecutorService.class));
    }

    private static Player player() {
        return proxy(Player.class);
    }

    private static List<InteractionCase> interactions(Player player) {
        PlayerInteractAtEntityEvent precise = event(PlayerInteractAtEntityEvent.class, player);
        PlayerArmorStandManipulateEvent armorStand = event(PlayerArmorStandManipulateEvent.class, player);
        PlayerHarvestBlockEvent harvest = event(PlayerHarvestBlockEvent.class, player);
        PlayerShearEntityEvent shear = event(PlayerShearEntityEvent.class, player);
        PlayerFishEvent fish = event(PlayerFishEvent.class, player);
        return List.of(
                new InteractionCase("precise entity interaction", precise, manager -> manager.onInteractAtEntity(precise)),
                new InteractionCase("armor-stand manipulation", armorStand, manager -> manager.onArmorStandManipulate(armorStand)),
                new InteractionCase("block harvesting", harvest, manager -> manager.onHarvest(harvest)),
                new InteractionCase("entity shearing", shear, manager -> manager.onShear(shear)),
                new InteractionCase("fishing", fish, manager -> manager.onFish(fish))
        );
    }

    private static <T extends PlayerEvent> T event(Class<T> type, Player player) {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field instanceField = unsafeType.getDeclaredField("theUnsafe");
            instanceField.setAccessible(true);
            Object unsafe = instanceField.get(null);
            T event = type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
            Field playerField = PlayerEvent.class.getDeclaredField("player");
            playerField.setAccessible(true);
            playerField.set(event, player);
            return event;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to create event fixture for " + type.getSimpleName(), exception);
        }
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

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> {
                    if ("getUniqueId".equals(method.getName())) {
                        return PLAYER_ID;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> instance == arguments[0];
                            case "hashCode" -> System.identityHashCode(instance);
                            case "toString" -> type.getSimpleName() + " test proxy";
                            default -> null;
                        };
                    }
                    Class<?> returnType = method.getReturnType();
                    if (!returnType.isPrimitive()) {
                        return null;
                    }
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == char.class) {
                        return '\0';
                    }
                    return 0;
                }
        );
    }

    private record InteractionCase(
            String name,
            Cancellable event,
            Consumer<FreezeManager> dispatch
    ) {
    }
}
