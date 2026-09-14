package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-thread worker scheduler with explicit pause and cycle quiescence on shutdown. */
final class DiscordPunishmentCoordinator implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(DiscordPunishmentCoordinator.class.getName());

    private final DiscordPunishmentWorker worker;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    DiscordPunishmentCoordinator(DiscordPunishmentWorker worker, Duration interval) {
        this(worker, interval, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "enthusia-discord-punishment-worker");
            thread.setDaemon(true);
            return thread;
        }));
    }

    DiscordPunishmentCoordinator(
            DiscordPunishmentWorker worker,
            Duration interval,
            ScheduledExecutorService executor
    ) {
        if (worker == null || interval == null || interval.toMillis() < 1 || executor == null) {
            throw new IllegalArgumentException("punishment coordinator configuration is invalid");
        }
        this.worker = worker;
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
        active.set(false);
    }

    private void runSafely() {
        if (closed.get() || !active.get()) {
            return;
        }
        try {
            worker.runCycle();
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
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
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
}
