package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StaffBotWorkerPoolTest {
    @Test
    void boundedQueueRejectsInsteadOfGrowingWithoutLimit() throws Exception {
        StaffBotHealth health = new StaffBotHealth(StaffBotEnvironment.STAGING);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (StaffBotWorkerPool pool = new StaffBotWorkerPool(1, 1, health)) {
            assertTrue(pool.tryExecute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(started.await(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS));
            assertTrue(pool.tryExecute(() -> { }));
            assertEquals(1, pool.queuedTasks());
            assertFalse(pool.tryExecute(() -> { }));
            assertEquals(1L, health.snapshot().rejectedWork());
            release.countDown();
        } finally {
            release.countDown();
        }
    }
}
