package net.enthusia.staff.paper.commandbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.ports.CommandBridgeExecutor;
import org.junit.jupiter.api.Test;

class BukkitConsoleCommandBridgeExecutorTest {
    @Test
    void schedulesAndDispatchesExactlyOnce() {
        AtomicInteger schedules = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        BukkitConsoleCommandBridgeExecutor executor = new BukkitConsoleCommandBridgeExecutor(
                operation -> {
                    schedules.incrementAndGet();
                    operation.run();
                },
                command -> {
                    dispatches.incrementAndGet();
                    return CommandBridgeExecutor.Execution.accepted("ok");
                },
                Duration.ofSeconds(1)
        );

        CommandBridgeExecutor.Execution result = executor.execute("list");
        assertEquals("ok", result.output());
        assertEquals(1, schedules.get());
        assertEquals(1, dispatches.get());
    }

    @Test
    void dispatcherFailureIsAmbiguousAndNeverRetried() {
        AtomicInteger dispatches = new AtomicInteger();
        BukkitConsoleCommandBridgeExecutor executor = new BukkitConsoleCommandBridgeExecutor(
                Runnable::run,
                command -> {
                    dispatches.incrementAndGet();
                    throw new IllegalStateException("unknown state after dispatch");
                },
                Duration.ofSeconds(1)
        );

        assertThrows(IllegalStateException.class, () -> executor.execute("list"));
        assertEquals(1, dispatches.get());
    }

    @Test
    void schedulerTimeoutIsAmbiguousWithoutSecondSchedule() {
        AtomicInteger schedules = new AtomicInteger();
        BukkitConsoleCommandBridgeExecutor executor = new BukkitConsoleCommandBridgeExecutor(
                operation -> schedules.incrementAndGet(),
                command -> CommandBridgeExecutor.Execution.accepted("late"),
                Duration.ofMillis(1)
        );

        assertThrows(IllegalStateException.class, () -> executor.execute("list"));
        assertEquals(1, schedules.get());
    }
}
