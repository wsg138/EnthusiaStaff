package net.enthusia.staff.velocity;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

final class VelocityBootstrapCoordinator {
    private final WorkerExecutor workers;
    private final DelayedScheduler scheduler;
    private final Attempt attempt;
    private final Listener listener;
    private final BooleanSupplier externallyStopping;
    private final RetryPolicy retryPolicy;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean attemptRunning = new AtomicBoolean();
    private final AtomicBoolean retryScheduled = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicBoolean exhausted = new AtomicBoolean();
    private final AtomicInteger attempts = new AtomicInteger();

    VelocityBootstrapCoordinator(
            WorkerExecutor workers,
            DelayedScheduler scheduler,
            Attempt attempt,
            Listener listener,
            BooleanSupplier externallyStopping,
            RetryPolicy retryPolicy
    ) {
        this.workers = Objects.requireNonNull(workers, "workers");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.attempt = Objects.requireNonNull(attempt, "attempt");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.externallyStopping = Objects.requireNonNull(externallyStopping, "externallyStopping");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    boolean start() {
        return started.compareAndSet(false, true) && submitAttempt();
    }

    synchronized boolean requestImmediateRetry() {
        if (stopping() || completed.get() || attemptRunning.get() || retryScheduled.get()) {
            return false;
        }
        attempts.set(0);
        exhausted.set(false);
        return submitAttempt();
    }

    void stop() {
        stopped.set(true);
    }

    int attempts() {
        return attempts.get();
    }

    boolean retryScheduled() {
        return retryScheduled.get();
    }

    boolean completed() {
        return completed.get();
    }

    boolean exhausted() {
        return exhausted.get();
    }

    private synchronized boolean submitAttempt() {
        if (stopping() || completed.get() || !attemptRunning.compareAndSet(false, true)) {
            return false;
        }
        int number = attempts.incrementAndGet();
        if (workers.submit(() -> runAttempt(number))) {
            return true;
        }
        return handleFailure(number, new IllegalStateException("Velocity bootstrap worker submission was rejected"));
    }

    private void runAttempt(int number) {
        if (stopping()) {
            synchronized (this) {
                attemptRunning.set(false);
            }
            return;
        }
        listener.attempting(number, retryPolicy.maximumAttempts());
        try {
            attempt.run();
            synchronized (this) {
                if (stopping()) {
                    attemptRunning.set(false);
                    return;
                }
                completed.set(true);
                exhausted.set(false);
                attemptRunning.set(false);
            }
            listener.recovered(number);
        } catch (PermanentFailure failure) {
            synchronized (this) {
                exhausted.set(true);
                attemptRunning.set(false);
                listener.exhausted(number, failure);
            }
        } catch (RuntimeException failure) {
            handleFailure(number, failure);
        }
    }

    private synchronized boolean handleFailure(int failedAttempt, RuntimeException failure) {
        if (stopping()) {
            attemptRunning.set(false);
            return false;
        }
        if (failedAttempt >= retryPolicy.maximumAttempts()) {
            exhausted.set(true);
            attemptRunning.set(false);
            listener.exhausted(failedAttempt, failure);
            return false;
        }
        long delayMillis = retryPolicy.delayAfterFailure(failedAttempt);
        if (!retryScheduled.compareAndSet(false, true)) {
            exhausted.set(true);
            attemptRunning.set(false);
            listener.exhausted(failedAttempt, new IllegalStateException(
                    "Duplicate Velocity bootstrap retry scheduling was rejected", failure));
            return false;
        }
        listener.retrying(failedAttempt + 1, retryPolicy.maximumAttempts(), delayMillis, failure);
        boolean scheduled = scheduler.schedule(this::runScheduledRetry, delayMillis);
        if (!scheduled) {
            retryScheduled.set(false);
            exhausted.set(true);
            attemptRunning.set(false);
            listener.exhausted(failedAttempt, new IllegalStateException(
                    "Velocity bootstrap retry scheduling was rejected", failure));
            return false;
        }
        attemptRunning.set(false);
        return true;
    }

    private synchronized void runScheduledRetry() {
        retryScheduled.set(false);
        if (!stopping()) {
            submitAttempt();
        }
    }

    private boolean stopping() {
        return stopped.get() || externallyStopping.getAsBoolean();
    }

    static final class PermanentFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        PermanentFailure(String message) {
            super(message);
        }

        PermanentFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    record RetryPolicy(int maximumAttempts, long initialDelayMillis, long maximumDelayMillis) {
        RetryPolicy {
            if (maximumAttempts < 1) {
                throw new IllegalArgumentException("maximumAttempts must be at least one");
            }
            if (initialDelayMillis < 1L || maximumDelayMillis < initialDelayMillis) {
                throw new IllegalArgumentException("retry delays must be positive and ordered");
            }
        }

        static RetryPolicy defaults() {
            return new RetryPolicy(6, 1_000L, 30_000L);
        }

        long delayAfterFailure(int failedAttempt) {
            long delay = initialDelayMillis;
            for (int index = 1; index < failedAttempt && delay < maximumDelayMillis; index++) {
                delay = Math.min(delay * 2L, maximumDelayMillis);
            }
            return delay;
        }
    }

    @FunctionalInterface
    interface WorkerExecutor {
        boolean submit(Runnable operation);
    }

    @FunctionalInterface
    interface DelayedScheduler {
        boolean schedule(Runnable operation, long delayMillis);
    }

    @FunctionalInterface
    interface Attempt {
        void run();
    }

    interface Listener {
        void attempting(int attempt, int maximumAttempts);

        void retrying(int nextAttempt, int maximumAttempts, long delayMillis, RuntimeException failure);

        void recovered(int attempts);

        void exhausted(int attempts, RuntimeException failure);
    }
}
