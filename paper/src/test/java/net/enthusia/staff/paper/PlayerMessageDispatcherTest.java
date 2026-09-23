package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PlayerMessageDispatcherTest {
    @Test
    void sendsMessagesOnTheRecipientEntityScheduler() {
        Plugin plugin = plugin();
        AtomicBoolean onEntityScheduler = new AtomicBoolean();
        AtomicInteger messages = new AtomicInteger();
        EntityScheduler scheduler = scheduler((seenPlugin, action, retired, delay) -> {
            assertSame(plugin, seenPlugin);
            assertEquals(1L, delay);
            onEntityScheduler.set(true);
            action.run();
            onEntityScheduler.set(false);
            return true;
        });
        Player player = player(scheduler, messages, onEntityScheduler);

        new PlayerMessageDispatcher(plugin).send(player, Component.text("Scheduled"));

        assertEquals(1, messages.get());
    }

    @Test
    void rejectedEntitySchedulingDoesNotSendDirectly() {
        AtomicBoolean onEntityScheduler = new AtomicBoolean();
        AtomicInteger messages = new AtomicInteger();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> false);
        Player player = player(scheduler, messages, onEntityScheduler);

        new PlayerMessageDispatcher(plugin()).send(player, Component.text("Rejected"));

        assertEquals(0, messages.get());
    }

    private static Plugin plugin() {
        return proxy(Plugin.class, (method, arguments) -> {
            if (method.getName().equals("getLogger")) {
                return Logger.getLogger("PlayerMessageDispatcherTest");
            }
            return unexpected(method);
        });
    }

    private static Player player(
            EntityScheduler scheduler,
            AtomicInteger messages,
            AtomicBoolean onEntityScheduler
    ) {
        return proxy(Player.class, (method, arguments) -> {
            if (method.getName().equals("getScheduler")) {
                return scheduler;
            }
            if (method.getName().equals("sendMessage")) {
                assertTrue(onEntityScheduler.get());
                messages.incrementAndGet();
                return null;
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

    @FunctionalInterface
    private interface SchedulerExecution {
        boolean execute(Plugin plugin, Runnable action, Runnable retired, long delay);
    }
}
