package net.enthusia.staff.paper.commandbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.ports.CommandBridgeExecutor;
import org.junit.jupiter.api.Test;

class BukkitConsoleCommandBridgeExecutorTest {
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333333");
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
                Duration.ofSeconds(1),
                actor -> true
        );

        CommandBridgeExecutor.Execution result = executor.execute(ACTOR, "list");
        assertEquals("ok", result.output());
        assertEquals(1, schedules.get());
        assertEquals(1, dispatches.get());
    }

    @Test
    void revokedDutyAtGlobalSchedulerDoesNotDispatchConsole() throws Exception {
        AtomicBoolean duty = new AtomicBoolean(true);
        AtomicInteger dispatches = new AtomicInteger();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        BukkitConsoleCommandBridgeExecutor executor = new BukkitConsoleCommandBridgeExecutor(
                operation -> {
                    scheduled.set(operation);
                    ready.countDown();
                },
                command -> {
                    dispatches.incrementAndGet();
                    return CommandBridgeExecutor.Execution.accepted("must not run");
                },
                Duration.ofSeconds(2),
                actor -> duty.get()
        );
        CompletableFuture<CommandBridgeExecutor.Execution> result =
                CompletableFuture.supplyAsync(() -> executor.execute(ACTOR, "list"));
        org.junit.jupiter.api.Assertions.assertTrue(ready.await(1, TimeUnit.SECONDS));
        duty.set(false);
        scheduled.get().run();

        org.junit.jupiter.api.Assertions.assertFalse(result.get(1, TimeUnit.SECONDS).accepted());
        assertEquals(0, dispatches.get());
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
                Duration.ofSeconds(1),
                actor -> true
        );

        assertThrows(IllegalStateException.class, () -> executor.execute(ACTOR, "list"));
        assertEquals(1, dispatches.get());
    }

    @Test
    void schedulerTimeoutIsAmbiguousWithoutSecondSchedule() {
        AtomicInteger schedules = new AtomicInteger();
        BukkitConsoleCommandBridgeExecutor executor = new BukkitConsoleCommandBridgeExecutor(
                operation -> schedules.incrementAndGet(),
                command -> CommandBridgeExecutor.Execution.accepted("late"),
                Duration.ofMillis(1),
                actor -> true
        );

        assertThrows(IllegalStateException.class, () -> executor.execute(ACTOR, "list"));
        assertEquals(1, schedules.get());
    }
}
