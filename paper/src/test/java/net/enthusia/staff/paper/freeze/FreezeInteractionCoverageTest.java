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

    @SuppressWarnings("removal")
    private static List<InteractionCase> interactions(Player player) {
        Entity entity = proxy(Entity.class);
        ItemStack item = new ServerFreeItemStack();
        PlayerInteractAtEntityEvent precise = new PlayerInteractAtEntityEvent(
                player,
                entity,
                new Vector(),
                EquipmentSlot.HAND
        );
        PlayerArmorStandManipulateEvent armorStand = new PlayerArmorStandManipulateEvent(
                player,
                proxy(ArmorStand.class),
                item,
                item,
                EquipmentSlot.HAND,
                EquipmentSlot.HAND
        );
        PlayerHarvestBlockEvent harvest = new PlayerHarvestBlockEvent(
                player,
                proxy(Block.class),
                EquipmentSlot.HAND,
                List.of()
        );
        PlayerShearEntityEvent shear = new PlayerShearEntityEvent(
                player,
                entity,
                item,
                EquipmentSlot.HAND,
                List.of()
        );
        PlayerFishEvent fish = new PlayerFishEvent(
                player,
                null,
                proxy(FishHook.class),
                EquipmentSlot.HAND,
                PlayerFishEvent.State.FISHING
        );
        return List.of(
                new InteractionCase("precise entity interaction", precise, manager -> manager.onInteractAtEntity(precise)),
                new InteractionCase("armor-stand manipulation", armorStand, manager -> manager.onArmorStandManipulate(armorStand)),
                new InteractionCase("block harvesting", harvest, manager -> manager.onHarvest(harvest)),
                new InteractionCase("entity shearing", shear, manager -> manager.onShear(shear)),
                new InteractionCase("fishing", fish, manager -> manager.onFish(fish))
        );
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
                (instance, method, arguments) -> "getUniqueId".equals(method.getName())
                        ? PLAYER_ID
                        : defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> type) {
        return type.isPrimitive() ? Array.get(Array.newInstance(type, 1), 0) : null;
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
