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
    private static final Duration DEFAULT_QUIESCE_TIMEOUT = Duration.ofSeconds(30);

    private final Runnable cycle;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final Duration quiesceTimeout;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    DiscordInvestigationCoordinator(DiscordInvestigationWorker worker, Duration interval) {
        this(worker == null ? null : worker::runCycle, interval, newExecutor(), DEFAULT_QUIESCE_TIMEOUT);
    }

    DiscordInvestigationCoordinator(Runnable cycle, Duration interval, ScheduledExecutorService executor) {
        this(cycle, interval, executor, DEFAULT_QUIESCE_TIMEOUT);
    }

    DiscordInvestigationCoordinator(
            Runnable cycle,
            Duration interval,
            ScheduledExecutorService executor,
            Duration quiesceTimeout
    ) {
        if (cycle == null || interval == null || interval.toMillis() < 1 || executor == null
                || quiesceTimeout == null || quiesceTimeout.isZero() || quiesceTimeout.isNegative()) {
            throw new IllegalArgumentException("investigation coordinator configuration is invalid");
        }
        this.cycle = cycle;
        this.interval = interval;
        this.executor = executor;
        this.quiesceTimeout = quiesceTimeout;
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
        await(barrier, quiesceTimeout);
    }

    private static void await(Future<?> barrier, Duration timeout) {
        try {
            barrier.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
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
        if (awaitTermination()) {
            return;
        }
        executor.shutdownNow();
        if (!awaitTermination()) {
            throw new IllegalStateException("investigation worker did not terminate after cancellation");
        }
    }

    void runAfterTermination(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("termination action must be present");
        }
        if (executor.isTerminated()) {
            action.run();
            return;
        }
        Thread.ofPlatform().daemon(true).name("enthusia-discord-investigation-cleanup").start(() -> {
            awaitTerminationUnbounded();
            action.run();
        });
    }

    private boolean awaitTermination() {
        long deadline = System.nanoTime() + quiesceTimeout.toNanos();
        boolean interrupted = false;
        try {
            while (!executor.isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    if (executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                        return true;
                    }
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            return true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void awaitTerminationUnbounded() {
        boolean interrupted = false;
        try {
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1, TimeUnit.DAYS);
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

    private static ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "enthusia-discord-investigation-worker");
            thread.setDaemon(true);
            return thread;
        });
    }
}
