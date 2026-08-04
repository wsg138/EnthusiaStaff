package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityMountEvent;
import org.junit.jupiter.api.Test;

class FreezeMountedMovementTest {
    private static final UUID PLAYER_ID = UUID.fromString("61636ba6-2481-42cf-bf5e-2729f078fe95");

    @Test
    void mountHandlerUsesTheFreezeRestrictionBoundary() throws NoSuchMethodException {
        Method method = FreezeManager.class.getDeclaredMethod("onMount", EntityMountEvent.class);
        EventHandler handler = method.getAnnotation(EventHandler.class);

        assertNotNull(handler);
        assertEquals(EventPriority.HIGHEST, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    @Test
    void restrictedPlayerCannotMount() {
        FreezeManager manager = manager();
        manager.verify(PLAYER_ID, "RestrictedPlayer");
        EntityMountEvent event = new EntityMountEvent(player(), proxy(Entity.class));

        manager.onMount(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void ordinaryPlayerCanMount() {
        FreezeManager manager = manager();
        EntityMountEvent event = new EntityMountEvent(player(), proxy(Entity.class));

        manager.onMount(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void immediateRestrictionLeavesVehicleAndClosesInventory() {
        AtomicInteger vehicleExits = new AtomicInteger();
        AtomicInteger inventoryClosures = new AtomicInteger();
        AtomicInteger messages = new AtomicInteger();
        Player player = proxy(Player.class, (method, arguments) -> {
            return switch (method.getName()) {
                case "getUniqueId" -> PLAYER_ID;
                case "leaveVehicle" -> {
                    vehicleExits.incrementAndGet();
                    yield true;
                }
                case "closeInventory" -> {
                    inventoryClosures.incrementAndGet();
                    yield null;
                }
                case "sendMessage" -> {
                    messages.incrementAndGet();
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            };
        });

        manager().securePlayer(player);

        assertEquals(1, vehicleExits.get());
        assertEquals(1, inventoryClosures.get());
        assertEquals(1, messages.get());
    }

    private static FreezeManager manager() {
        return new FreezeManager(null, Clock.systemUTC(), () -> null, proxy(ExecutorService.class));
    }

    private static Player player() {
        return proxy(Player.class, (method, arguments) -> "getUniqueId".equals(method.getName())
                ? PLAYER_ID
                : defaultValue(method.getReturnType()));
    }

    private static <T> T proxy(Class<T> type) {
        return proxy(type, (method, arguments) -> defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.invoke(method, arguments)
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments);
    }
}
