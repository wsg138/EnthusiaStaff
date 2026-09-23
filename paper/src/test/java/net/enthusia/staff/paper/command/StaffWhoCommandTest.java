package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class StaffWhoCommandTest {
    @Test
    void rendersOnlyAllowlistedOperationalFields() {
        List<String> lines = StaffWhoCommand.render(List.of(
                new StaffWhoCommand.Entry("Alpha", StaffRank.MOD, true, true),
                new StaffWhoCommand.Entry("Bravo", StaffRank.ADMIN, false, false)
        ), "3");

        assertEquals("Online staff: 2 | pending punishment requests: 3", lines.get(0));
        assertEquals("- Alpha [MOD] staff-mode=on vanished=yes", lines.get(1));
        assertEquals("- Bravo [ADMIN] staff-mode=off vanished=no", lines.get(2));
    }

    @Test
    void emptyRosterHasExplicitMessage() {
        assertEquals(List.of(
                "Online staff: 0 | pending punishment requests: unavailable",
                "- No staff are currently online."
        ), StaffWhoCommand.render(List.of(), "unavailable"));
    }

    @Test
    void pendingCountDoesNotPretendFiveHundredIsExhaustive() {
        assertEquals("499", StaffWhoCommand.pendingLabel(499));
        assertEquals("500+", StaffWhoCommand.pendingLabel(500));
        assertEquals("500+", StaffWhoCommand.pendingLabel(501));
    }

    @Test
    void snapshotRunsOnlyInsideEntityOwnedCallback() {
        AtomicBoolean snapshot = new AtomicBoolean();
        AtomicBoolean accepted = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();
        Runnable[] owned = new Runnable[1];
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> {
            assertEquals(1L, delay);
            owned[0] = action;
            return true;
        });

        boolean scheduled = StaffWhoCommand.scheduleSnapshot(
                plugin(),
                player(scheduler),
                () -> {
                    snapshot.set(true);
                    return new StaffWhoCommand.Entry("Alpha", StaffRank.MOD, false, false);
                },
                ignored -> accepted.set(true),
                () -> finished.set(true)
        );

        assertTrue(scheduled);
        assertFalse(snapshot.get());
        assertFalse(finished.get());
        owned[0].run();
        assertTrue(snapshot.get());
        assertTrue(accepted.get());
        assertTrue(finished.get());
    }

    @Test
    void retiredPlayerSkipsSnapshotAndCompletesOnce() {
        AtomicBoolean snapshot = new AtomicBoolean();
        AtomicInteger completions = new AtomicInteger();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> {
            retired.run();
            return true;
        });

        boolean scheduled = StaffWhoCommand.scheduleSnapshot(
                plugin(),
                player(scheduler),
                () -> {
                    snapshot.set(true);
                    return null;
                },
                ignored -> {
                },
                completions::incrementAndGet
        );

        assertTrue(scheduled);
        assertFalse(snapshot.get());
        assertEquals(1, completions.get());
    }

    @Test
    void rejectedSchedulingCompletesExactlyOnce() {
        AtomicInteger completions = new AtomicInteger();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> {
            retired.run();
            return false;
        });

        boolean scheduled = StaffWhoCommand.scheduleSnapshot(
                plugin(),
                player(scheduler),
                () -> null,
                ignored -> {
                },
                completions::incrementAndGet
        );

        assertFalse(scheduled);
        assertEquals(1, completions.get());
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
