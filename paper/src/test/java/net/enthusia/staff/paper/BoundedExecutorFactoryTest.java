package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BoundedExecutorFactoryTest {
    @Test
    void forcedShutdownDoesNotReturnUntilRunningWorkersTerminate() throws Exception {
        ExecutorService workers = BoundedExecutorFactory.create(1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean shutdownReturned = new AtomicBoolean();

        workers.execute(() -> {
            started.countDown();
            boolean released = false;
            while (!released) {
                try {
                    released = release.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    // Simulate cleanup that must finish before the recovery transaction can run.
                }
            }
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));

        Thread shutdown = new Thread(() -> {
            workers.shutdownNow();
            shutdownReturned.set(true);
        });
        shutdown.start();

        assertFalse(waitFor(shutdownReturned, Duration.ofMillis(150)));
        release.countDown();
        shutdown.join(2_000);

        assertTrue(shutdownReturned.get());
        assertTrue(workers.isTerminated());
    }

    private static boolean waitFor(AtomicBoolean condition, Duration duration) throws InterruptedException {
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.get();
    }
}
