package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

class FreezeMovementAndCommandTest {
    private static final UUID PLAYER_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");

    @Test
    void positionalMovementKeepsRequestedOrientationAtOriginalPosition() {
        FreezeManager manager = restrictedManager();
        Player player = player(new ArrayList<>());
        PlayerMoveEvent event = new PlayerMoveEvent(
                player,
                new Location(world(), 10.0, 64.0, 10.0, 0.0f, 0.0f),
                new Location(world(), 10.5, 64.0, 10.0, 45.0f, 15.0f)
        );

        manager.onMove(event);

        assertFalse(event.isCancelled());
        assertEquals(10.0, event.getTo().getX());
        assertEquals(64.0, event.getTo().getY());
        assertEquals(10.0, event.getTo().getZ());
        assertEquals(45.0f, event.getTo().getYaw());
        assertEquals(15.0f, event.getTo().getPitch());
    }

    @Test
    void rotationOnlyMovementRemainsAllowed() {
        FreezeManager manager = restrictedManager();
        Player player = player(new ArrayList<>());
        PlayerMoveEvent event = new PlayerMoveEvent(
                player,
                new Location(world(), 10.0, 64.0, 10.0, 0.0f, 0.0f),
                new Location(world(), 10.0, 64.0, 10.0, 45.0f, 15.0f)
        );

        manager.onMove(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void blockedCommandExplainsWhyItWasRejected() {
        FreezeManager manager = restrictedManager();
        List<Component> messages = new ArrayList<>();
        Player player = player(messages);
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/spawn", Set.of(player));

        manager.onCommand(event);

        assertTrue(event.isCancelled());
        assertTrue(messages.stream().anyMatch(message -> message.equals(Component.text(
                "You are frozen; commands are unavailable until staff releases the freeze."
        ))));
    }

    private static FreezeManager restrictedManager() {
        FreezeManager manager = new FreezeManager(
                null,
                Clock.systemUTC(),
                () -> null,
                proxy(ExecutorService.class)
        );
        manager.verify(PLAYER_ID, "FrozenPlayer");
        return manager;
    }

    private static World world() {
        return proxy(World.class);
    }

    private static Player player(List<Component> messages) {
        return proxy(Player.class, (method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> PLAYER_ID;
            case "sendMessage" -> {
                if (arguments != null && arguments.length > 0 && arguments[0] instanceof Component component) {
                    messages.add(component);
                }
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
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
        Object invoke(java.lang.reflect.Method method, Object[] arguments);
    }
}
