package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-thread D09 scheduler with explicit pause and quiescence across gateway reconnects. */
final class DiscordInvestigationCoordinator implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(DiscordInvestigationCoordinator.class.getName());
    private static final Duration QUIESCE_TIMEOUT = Duration.ofSeconds(30);

    private final Runnable cycle;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    DiscordInvestigationCoordinator(DiscordInvestigationWorker worker, Duration interval) {
        this(worker == null ? null : worker::runCycle, interval, newExecutor());
    }

    DiscordInvestigationCoordinator(Runnable cycle, Duration interval, ScheduledExecutorService executor) {
        if (cycle == null || interval == null || interval.toMillis() < 1 || executor == null) {
            throw new IllegalArgumentException("investigation coordinator configuration is invalid");
        }
        this.cycle = cycle;
        this.interval = interval;
        this.executor = executor;
    }

    void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            throw new IllegalStateException("investigation coordinator cannot be started");
        }
        executor.scheduleWithFixedDelay(this::runSafely, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    void resume() {
        if (!started.get() || closed.get()) {
            throw new IllegalStateException("investigation coordinator is not available");
        }
        active.set(true);
    }

    void pause() {
        if (closed.get()) {
            return;
        }
        active.set(false);
        if (started.get()) {
            awaitQuiescence();
        }
    }

    private void awaitQuiescence() {
        Future<?> barrier;
        try {
            barrier = executor.submit(() -> { });
        } catch (RejectedExecutionException exception) {
            if (closed.get()) {
                return;
            }
            throw new IllegalStateException("investigation worker rejected quiescence barrier", exception);
        }
        await(barrier);
    }

    private static void await(Future<?> barrier) {
        try {
            barrier.get(QUIESCE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while pausing investigation worker", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("investigation worker quiescence failed", exception.getCause());
        } catch (TimeoutException exception) {
            throw new IllegalStateException("investigation worker did not quiesce", exception);
        }
    }

    private void runSafely() {
        if (closed.get() || !active.get()) {
            return;
        }
        try {
            cycle.run();
        } catch (RuntimeException exception) {
            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "discord_investigation_worker_cycle_failed type={0}", exception.getClass().getSimpleName());
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        active.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(QUIESCE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while stopping investigation worker", exception);
        }
    }

    private static ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "enthusia-discord-investigation-worker");
            thread.setDaemon(true);
            return thread;
        });
    }
}
