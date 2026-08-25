package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounded executor for later Discord interaction work. Gateway control callbacks are never routed through this pool.
 */
public final class StaffBotWorkerPool implements AutoCloseable {
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

    private final ThreadPoolExecutor executor;
    private final StaffBotHealth health;

    public StaffBotWorkerPool(int threads, int queueCapacity, StaffBotHealth health) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be positive");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queue capacity must be positive");
        }
        this.health = Objects.requireNonNull(health, "health");
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().daemon(true).name("staff-bot-worker-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Attempts to schedule work without creating an unbounded backlog.
     *
     * @return false when the bounded queue is saturated or shutdown has begun.
     */
    public boolean tryExecute(Runnable task) {
        Objects.requireNonNull(task, "task");
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException exception) {
            health.recordRejectedWork();
            return false;
        }
    }

    int queuedTasks() {
        return executor.getQueue().size();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
