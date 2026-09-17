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

/** Single-thread worker scheduler with explicit pause and cycle quiescence. */
final class DiscordPunishmentCoordinator implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(DiscordPunishmentCoordinator.class.getName());
    private static final Duration QUIESCE_TIMEOUT = Duration.ofSeconds(30);

    private final Runnable cycle;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    DiscordPunishmentCoordinator(DiscordPunishmentWorker worker, Duration interval) {
        this(worker == null ? null : worker::runCycle, interval, newExecutor());
    }

    DiscordPunishmentCoordinator(
            Runnable cycle,
            Duration interval,
            ScheduledExecutorService executor
    ) {
        if (cycle == null || interval == null || interval.toMillis() < 1 || executor == null) {
            throw new IllegalArgumentException("punishment coordinator configuration is invalid");
        }
        this.cycle = cycle;
        this.interval = interval;
        this.executor = executor;
    }

    void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            throw new IllegalStateException("punishment coordinator cannot be started");
        }
        executor.scheduleWithFixedDelay(this::runSafely, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    void resume() {
        if (!started.get() || closed.get()) {
            throw new IllegalStateException("punishment coordinator is not available");
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
            throw new IllegalStateException("Discord punishment worker rejected quiescence barrier", exception);
        }
        try {
            barrier.get(QUIESCE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pausing Discord punishment worker", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Discord punishment worker quiescence barrier failed", exception.getCause());
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Discord punishment worker did not quiesce while pausing", exception);
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
                        "discord_punishment_worker_cycle_failed type={0}", exception.getClass().getSimpleName());
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
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Discord punishment worker did not quiesce");
                }
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping Discord punishment worker", exception);
        }
    }

    private static ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "enthusia-discord-punishment-worker");
            thread.setDaemon(true);
            return thread;
        });
    }
}
