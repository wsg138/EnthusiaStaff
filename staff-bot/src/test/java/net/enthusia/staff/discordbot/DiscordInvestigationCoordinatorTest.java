package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DiscordInvestigationCoordinatorTest {
    @Test
    void forcedShutdownDefersCleanupUntilStuckCycleTerminates() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cleanup = new CountDownLatch(1);
        try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
            DiscordInvestigationCoordinator coordinator = new DiscordInvestigationCoordinator(
                    () -> {
                        entered.countDown();
                        awaitIgnoringInterrupts(release);
                    },
                    Duration.ofMillis(5), scheduler, Duration.ofMillis(25)
            );
            coordinator.start();
            coordinator.resume();
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            assertThrows(IllegalStateException.class, coordinator::close);
            coordinator.runAfterTermination(cleanup::countDown);
            assertFalse(cleanup.await(50, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(cleanup.await(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (latch.getCount() > 0) {
                try {
                    latch.await();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
