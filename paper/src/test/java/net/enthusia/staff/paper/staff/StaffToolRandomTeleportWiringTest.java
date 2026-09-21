package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

final class StaffToolRandomTeleportWiringTest {
    private static final UUID PLAYER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void bukkitPlatformDispatchesPlayerWorkThroughEntityScheduler() {
        AtomicBoolean owned = new AtomicBoolean();
        Plugin plugin = plugin(null);
        EntityScheduler scheduler = proxy(EntityScheduler.class, (method, arguments) -> {
            if (!method.getName().equals("execute")) {
                return unexpected(method);
            }
            assertSame(plugin, arguments[0]);
            assertEquals(1L, arguments[3]);
            ((Runnable) arguments[1]).run();
            return true;
        });
        Player player = proxy(Player.class, (method, arguments) -> {
            if (method.getName().equals("getScheduler")) {
                return scheduler;
            }
            return unexpected(method);
        });

        StaffToolRandomTeleportService.BukkitPlatform platform =
                new StaffToolRandomTeleportService.BukkitPlatform(plugin);

        assertTrue(platform.executeEntity(player, () -> owned.set(true), () -> {
        }));
        assertTrue(owned.get());
    }

    @Test
    void bukkitPlatformUsesGlobalSchedulerForResolution() {
        AtomicBoolean global = new AtomicBoolean();
        Player player = proxy(Player.class, (method, arguments) -> unexpected(method));
        GlobalRegionScheduler scheduler = proxy(GlobalRegionScheduler.class, (method, arguments) -> {
            if (!method.getName().equals("execute")) {
                return unexpected(method);
            }
            ((Runnable) arguments[1]).run();
            return null;
        });
        Server server = proxy(Server.class, (method, arguments) -> switch (method.getName()) {
            case "getGlobalRegionScheduler" -> scheduler;
            case "getPlayer" -> {
                assertEquals(PLAYER_ID, arguments[0]);
                yield player;
            }
            default -> unexpected(method);
        });
        Plugin plugin = plugin(server);
        StaffToolRandomTeleportService.BukkitPlatform platform =
                new StaffToolRandomTeleportService.BukkitPlatform(plugin);

        platform.executeGlobal(() -> global.set(true));

        assertTrue(global.get());
        assertSame(player, platform.player(PLAYER_ID));
    }

    private static Plugin plugin(Server server) {
        return proxy(Plugin.class, (method, arguments) -> {
            if (method.getName().equals("getServer")) {
                return server;
            }
            return unexpected(method);
        });
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.invoke(
                        method,
                        arguments == null ? new Object[0] : arguments
                )
        ));
    }

    private static Object unexpected(Method method) {
        throw new AssertionError("Unexpected call: " + method.getName());
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }
}
