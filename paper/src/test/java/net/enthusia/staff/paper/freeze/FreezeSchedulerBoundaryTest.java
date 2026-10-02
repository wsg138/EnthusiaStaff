package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class FreezeSchedulerBoundaryTest {
    @Test
    void recipientOperationRunsOnlyInsideEntityOwnedCallback() {
        AtomicBoolean executed = new AtomicBoolean();
        Runnable[] owned = new Runnable[1];
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> {
            owned[0] = action;
            return true;
        });

        boolean scheduled = FreezeManager.scheduleRecipient(plugin(), player(scheduler), () -> executed.set(true));

        assertTrue(scheduled);
        assertFalse(executed.get());
        owned[0].run();
        assertTrue(executed.get());
    }

    @Test
    void retiredRecipientRejectsWithoutRunningPlayerOperation() {
        AtomicBoolean executed = new AtomicBoolean();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> false);

        boolean scheduled = FreezeManager.scheduleRecipient(plugin(), player(scheduler), () -> executed.set(true));

        assertFalse(scheduled);
        assertFalse(executed.get());
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
                        (Plugin) arguments[0], (Runnable) arguments[1],
                        (Runnable) arguments[2], (Long) arguments[3]
                );
            }
            return unexpected(method);
        });
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(), new Class<?>[]{type},
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
