package net.enthusia.staff.paper.command;

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

class ReportCommandFoliaSchedulingTest {
    @Test
    void targetCaptureRunsThroughTheTargetsEntityScheduler() {
        AtomicBoolean captured = new AtomicBoolean();
        Plugin plugin = plugin();
        EntityScheduler scheduler = scheduler((seenPlugin, action, retired, delay) -> {
            assertSame(plugin, seenPlugin);
            assertEquals(1L, delay);
            action.run();
            return true;
        });

        boolean scheduled = ReportCommand.scheduleOnTarget(
                plugin, player(scheduler), () -> captured.set(true), () -> {
                }
        );

        assertTrue(scheduled);
        assertTrue(captured.get());
    }

    @Test
    void retirementRunsTheFallbackOnceWithoutCapturingTargetEvidence() {
        AtomicBoolean captured = new AtomicBoolean();
        AtomicInteger fallbacks = new AtomicInteger();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> {
            retired.run();
            return true;
        });

        boolean scheduled = ReportCommand.scheduleOnTarget(
                plugin(), player(scheduler), () -> captured.set(true), fallbacks::incrementAndGet
        );

        assertTrue(scheduled);
        assertFalse(captured.get());
        assertEquals(1, fallbacks.get());
    }

    @Test
    void rejectedSchedulingRunsTheFallbackOnce() {
        AtomicBoolean captured = new AtomicBoolean();
        AtomicInteger fallbacks = new AtomicInteger();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> false);

        boolean scheduled = ReportCommand.scheduleOnTarget(
                plugin(), player(scheduler), () -> captured.set(true), fallbacks::incrementAndGet
        );

        assertFalse(scheduled);
        assertFalse(captured.get());
        assertEquals(1, fallbacks.get());
    }

    @Test
    void rejectedSchedulingCannotRunTheFallbackTwiceWhenRetiredAlsoFires() {
        AtomicInteger fallbacks = new AtomicInteger();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> {
            retired.run();
            return false;
        });

        ReportCommand.scheduleOnTarget(plugin(), player(scheduler), () -> {
        }, fallbacks::incrementAndGet);

        assertEquals(1, fallbacks.get());
    }

    @Test
    void schedulerFailureRunsTheFallbackInsteadOfDroppingTheReport() {
        AtomicInteger fallbacks = new AtomicInteger();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> {
            throw new IllegalStateException("scheduler unavailable");
        });

        boolean scheduled = ReportCommand.scheduleOnTarget(
                plugin(), player(scheduler), () -> {
                }, fallbacks::incrementAndGet
        );

        assertFalse(scheduled);
        assertEquals(1, fallbacks.get());
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
                (instance, method, arguments) -> invocation.invoke(
                        method, arguments == null ? new Object[0] : arguments
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

    @FunctionalInterface
    private interface SchedulerExecution {
        boolean execute(Plugin plugin, Runnable action, Runnable retired, long delay);
    }
}
