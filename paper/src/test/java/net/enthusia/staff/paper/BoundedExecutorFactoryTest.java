package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class BoundedExecutorFactoryTest {
    @Test
    void forcedShutdownWaitsForRunningWorkersAndCompletesQueuedWork() throws Exception {
        ExecutorService workers = BoundedExecutorFactory.create(1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean queuedWorkCompleted = new AtomicBoolean();
        AtomicBoolean shutdownReturned = new AtomicBoolean();

        workers.execute(() -> {
            started.countDown();
            boolean released = false;
            boolean interrupted = false;
            while (!released) {
                try {
                    released = release.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        workers.execute(() -> queuedWorkCompleted.set(true));

        Thread shutdown = new Thread(() -> {
            workers.shutdownNow();
            shutdownReturned.set(true);
        });
        shutdown.setDaemon(true);
        shutdown.start();

        assertTrue(waitFor(workers::isShutdown, Duration.ofSeconds(2)));
        assertFalse(waitFor(shutdownReturned::get, Duration.ofMillis(150)));
        assertFalse(queuedWorkCompleted.get());
        release.countDown();
        shutdown.join(2_000);

        assertTrue(shutdownReturned.get());
        assertTrue(queuedWorkCompleted.get());
        assertTrue(workers.isTerminated());
    }

    private static boolean waitFor(BooleanSupplier condition, Duration duration) throws InterruptedException {
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }
}
