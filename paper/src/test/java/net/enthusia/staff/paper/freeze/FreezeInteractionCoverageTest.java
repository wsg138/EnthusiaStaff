package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
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
        return List.of(
                preciseInteraction(player),
                armorStandInteraction(player),
                harvestInteraction(player),
                shearInteraction(player),
                fishInteraction(player)
        );
    }

    private static InteractionCase preciseInteraction(Player player) {
        PlayerInteractAtEntityEvent event = new PlayerInteractAtEntityEvent(
                player,
                proxy(Entity.class),
                new Vector(),
                EquipmentSlot.HAND
        );
        return new InteractionCase(
                "precise entity interaction",
                event,
                manager -> manager.onInteractAtEntity(event)
        );
    }

    private static InteractionCase armorStandInteraction(Player player) {
        ItemStack item = new ServerFreeItemStack();
        PlayerArmorStandManipulateEvent event = new PlayerArmorStandManipulateEvent(
                player,
                proxy(ArmorStand.class),
                item,
                item,
                EquipmentSlot.HAND,
                EquipmentSlot.HAND
        );
        return new InteractionCase(
                "armor-stand manipulation",
                event,
                manager -> manager.onArmorStandManipulate(event)
        );
    }

    @SuppressWarnings("removal")
    private static InteractionCase harvestInteraction(Player player) {
        PlayerHarvestBlockEvent event = new PlayerHarvestBlockEvent(
                player,
                proxy(Block.class),
                EquipmentSlot.HAND,
                List.of()
        );
        return new InteractionCase("block harvesting", event, manager -> manager.onHarvest(event));
    }

    private static InteractionCase shearInteraction(Player player) {
        PlayerShearEntityEvent event = new PlayerShearEntityEvent(
                player,
                proxy(Entity.class),
                new ServerFreeItemStack(),
                EquipmentSlot.HAND,
                List.of()
        );
        return new InteractionCase("entity shearing", event, manager -> manager.onShear(event));
    }

    private static InteractionCase fishInteraction(Player player) {
        PlayerFishEvent event = new PlayerFishEvent(
                player,
                null,
                proxy(FishHook.class),
                EquipmentSlot.HAND,
                PlayerFishEvent.State.FISHING
        );
        return new InteractionCase("fishing", event, manager -> manager.onFish(event));
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
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> "getUniqueId".equals(method.getName())
                        ? PLAYER_ID
                        : defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }

    private record InteractionCase(
            String name,
            Cancellable event,
            Consumer<FreezeManager> dispatch
    ) {
    }

    private static final class ServerFreeItemStack extends ItemStack {
        private ServerFreeItemStack() {
            super();
        }
    }
}
