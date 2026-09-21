package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class CommandResponseDispatcherTest {
    @Test
    void playerResponsesRunOnTheSenderEntityScheduler() {
        Plugin plugin = plugin();
        AtomicInteger messages = new AtomicInteger();
        EntityScheduler scheduler = scheduler((seenPlugin, action, retired, delay) -> {
            assertSame(plugin, seenPlugin);
            assertEquals(1L, delay);
            action.run();
            return true;
        });

        new CommandResponseDispatcher(plugin).send(player(scheduler, messages), Component.text("Scheduled"));

        assertEquals(1, messages.get());
    }

    @Test
    void rejectedPlayerResponseDoesNotRunOnTheCallingWorker() {
        AtomicInteger messages = new AtomicInteger();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> false);

        new CommandResponseDispatcher(plugin()).send(player(scheduler, messages), Component.text("Rejected"));

        assertEquals(0, messages.get());
    }

    @Test
    void consoleResponsesRunOnTheGlobalScheduler() {
        AtomicInteger messages = new AtomicInteger();
        AtomicReference<Plugin> expectedPlugin = new AtomicReference<>();
        Plugin plugin = plugin(server(globalScheduler((seenPlugin, action) -> {
            assertSame(expectedPlugin.get(), seenPlugin);
            action.run();
        })));
        expectedPlugin.set(plugin);

        new CommandResponseDispatcher(plugin).send(console(messages), Component.text("Console"));

        assertEquals(1, messages.get());
    }

    private static Plugin plugin() {
        return plugin(null);
    }

    private static Plugin plugin(Server server) {
        return proxy(Plugin.class, (method, arguments) -> {
            if (method.getName().equals("getLogger")) {
                return Logger.getLogger("CommandResponseDispatcherTest");
            }
            if (method.getName().equals("getServer") && server != null) {
                return server;
            }
            return unexpected(method);
        });
    }

    private static Player player(EntityScheduler scheduler, AtomicInteger messages) {
        return proxy(Player.class, (method, arguments) -> {
            if (method.getName().equals("getScheduler")) {
                return scheduler;
            }
            if (method.getName().equals("sendMessage")) {
                messages.incrementAndGet();
                return null;
            }
            return unexpected(method);
        });
    }

    private static ConsoleCommandSender console(AtomicInteger messages) {
        return proxy(ConsoleCommandSender.class, (method, arguments) -> {
            if (method.getName().equals("sendMessage")) {
                messages.incrementAndGet();
                return null;
            }
            return unexpected(method);
        });
    }

    private static Server server(GlobalRegionScheduler scheduler) {
        return proxy(Server.class, (method, arguments) -> {
            if (method.getName().equals("getGlobalRegionScheduler")) {
                return scheduler;
            }
            return unexpected(method);
        });
    }

    private static GlobalRegionScheduler globalScheduler(GlobalSchedulerExecution execution) {
        return proxy(GlobalRegionScheduler.class, (method, arguments) -> {
            if (method.getName().equals("execute")) {
                execution.execute((Plugin) arguments[0], (Runnable) arguments[1]);
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

    @FunctionalInterface
    private interface GlobalSchedulerExecution {
        void execute(Plugin plugin, Runnable action);
    }
}
