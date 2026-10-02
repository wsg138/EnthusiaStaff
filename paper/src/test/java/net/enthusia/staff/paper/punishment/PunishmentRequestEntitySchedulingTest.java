package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PunishmentRequestEntitySchedulingTest {
    @Test
    void callbackExecutesThroughThePlayersEntityScheduler() {
        AtomicBoolean callback = new AtomicBoolean();
        Plugin plugin = proxy(Plugin.class, (method, arguments) -> unexpected(method));
        EntityScheduler scheduler = scheduler((seenPlugin, action, retired, delay) -> {
            assertSame(plugin, seenPlugin);
            assertEquals(1L, delay);
            action.run();
            return true;
        });

        boolean scheduled = PunishmentRequestGuiController.scheduleOnOwner(
                plugin,
                player(scheduler),
                () -> callback.set(true),
                () -> {
                }
        );

        assertTrue(scheduled);
        assertTrue(callback.get());
    }

    @Test
    void retirementDropsThePlayerCallbackAndRunsRetirementOnce() {
        AtomicBoolean callback = new AtomicBoolean();
        AtomicInteger retirements = new AtomicInteger();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> {
            retired.run();
            return true;
        });

        boolean scheduled = PunishmentRequestGuiController.scheduleOnOwner(
                plugin(),
                player(scheduler),
                () -> callback.set(true),
                retirements::incrementAndGet
        );

        assertTrue(scheduled);
        assertFalse(callback.get());
        assertEquals(1, retirements.get());
    }

    @Test
    void rejectedSchedulingDropsThePlayerCallbackAndRetiresOnce() {
        AtomicBoolean callback = new AtomicBoolean();
        AtomicInteger retirements = new AtomicInteger();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> false);

        boolean scheduled = PunishmentRequestGuiController.scheduleOnOwner(
                plugin(),
                player(scheduler),
                () -> callback.set(true),
                retirements::incrementAndGet
        );

        assertFalse(scheduled);
        assertFalse(callback.get());
        assertEquals(1, retirements.get());
    }

    @Test
    void rejectedSchedulingCannotDoubleRunRetirement() {
        AtomicInteger retirements = new AtomicInteger();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> {
            retired.run();
            return false;
        });

        PunishmentRequestGuiController.scheduleOnOwner(
                plugin(),
                player(scheduler),
                () -> {
                },
                retirements::incrementAndGet
        );

        assertEquals(1, retirements.get());
    }

    private static Plugin plugin() {
        return proxy(Plugin.class, (method, arguments) -> unexpected(method));
    }

    private static Player player(EntityScheduler scheduler) {
        return proxy(Player.class, (method, arguments) -> {
            if (method.getName().equals("getScheduler")) {
                return scheduler;
            }
            return unexpected(method);
        });
    }

    private static EntityScheduler scheduler(SchedulerExecution execution) {
        return proxy(EntityScheduler.class, (method, arguments) -> {
            if (method.getName().equals("execute")) {
                return execution.execute(
                        (Plugin) arguments[0],
                        (Runnable) arguments[1],
                        (Runnable) arguments[2],
                        (Long) arguments[3]
                );
            }
            return unexpected(method);
        });
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.invoke(method, arguments == null ? new Object[0] : arguments)
        ));
    }

    private static Object unexpected(Method method) {
        throw new AssertionError("Unexpected call: " + method.getName());
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }

    @FunctionalInterface
    private interface SchedulerExecution {
        boolean execute(Plugin plugin, Runnable action, Runnable retired, long delay);
    }
}
