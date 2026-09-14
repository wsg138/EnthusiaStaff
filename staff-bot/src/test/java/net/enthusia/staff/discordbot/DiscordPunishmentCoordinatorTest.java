package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DiscordPunishmentCoordinatorTest {
    @Test
    void pauseWaitsForInflightCycleAndRuntimeCanResume() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        AtomicInteger cycles = new AtomicInteger();
        Runnable cycle = blockingFirstCycle(cycles, firstEntered, releaseFirst, secondEntered);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService control = Executors.newSingleThreadExecutor();
        DiscordPunishmentCoordinator coordinator = new DiscordPunishmentCoordinator(
                cycle, Duration.ofMillis(5), scheduler
        );
        try {
            coordinator.start();
            coordinator.resume();
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS));

            Future<?> paused = control.submit(coordinator::pause);
            assertThrows(TimeoutException.class, () -> paused.get(100, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            paused.get(2, TimeUnit.SECONDS);

            coordinator.resume();
            assertTrue(secondEntered.await(2, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            coordinator.close();
            control.shutdownNow();
        }
    }

    @Test
    void closeWaitsForInflightCycleAndPermanentlyStopsCoordinator() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService control = Executors.newSingleThreadExecutor();
        DiscordPunishmentCoordinator coordinator = new DiscordPunishmentCoordinator(
                () -> {
                    entered.countDown();
                    await(release);
                },
                Duration.ofMillis(5),
                scheduler
        );
        coordinator.start();
        coordinator.resume();
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        Future<?> closed = control.submit(coordinator::close);
        assertThrows(TimeoutException.class, () -> closed.get(100, TimeUnit.MILLISECONDS));
        release.countDown();
        closed.get(2, TimeUnit.SECONDS);

        assertThrows(IllegalStateException.class, coordinator::resume);
        control.shutdownNow();
    }

    private static Runnable blockingFirstCycle(
            AtomicInteger cycles,
            CountDownLatch firstEntered,
            CountDownLatch releaseFirst,
            CountDownLatch secondEntered
    ) {
        return () -> {
            int call = cycles.incrementAndGet();
            if (call == 1) {
                firstEntered.countDown();
                await(releaseFirst);
                return;
            }
            secondEntered.countDown();
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
