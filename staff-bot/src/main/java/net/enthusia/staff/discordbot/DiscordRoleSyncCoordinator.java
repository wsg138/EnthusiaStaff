package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.moderation.DiscordUserId;

/** Schedules one bounded role-sync batch at a time and never queues overlapping scans. */
final class DiscordRoleSyncCoordinator implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(DiscordRoleSyncCoordinator.class.getName());
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(20);

    private final DiscordRoleSyncService service;
    private final StaffBotWorkerPool workers;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<DiscordRoleReconciler> reconciler = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean cycleInFlight = new AtomicBoolean();
    private final AtomicBoolean cyclePending = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object cycleMonitor = new Object();
    private volatile Optional<DiscordUserId> cursor = Optional.empty();

    DiscordRoleSyncCoordinator(DiscordRoleSyncService service, StaffBotWorkerPool workers) {
        if (service == null || workers == null) {
            throw new IllegalArgumentException("role-sync coordinator dependencies must be present");
        }
        this.service = service;
        this.workers = workers;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("staff-bot-role-sync-scheduler").factory());
    }

    void enable(DiscordRoleReconciler activeReconciler) {
        if (activeReconciler == null || closed.get()) {
            throw new IllegalArgumentException("active role reconciler must be present");
        }
        DiscordRoleReconciler previous = reconciler.getAndSet(activeReconciler);
        if (previous != null && previous != activeReconciler) {
            previous.cancel();
        }
        scheduleOnce();
        requestImmediateCycle();
    }

    void disable() {
        DiscordRoleReconciler previous = reconciler.getAndSet(null);
        if (previous != null) {
            previous.cancel();
        }
    }

    private void scheduleOnce() {
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        long delay = service.configuration().interval().toSeconds();
        scheduler.scheduleWithFixedDelay(this::submitScheduledCycle, delay, delay, TimeUnit.SECONDS);
    }

    private void requestImmediateCycle() {
        if (closed.get() || reconciler.get() == null) {
            return;
        }
        if (!cycleInFlight.compareAndSet(false, true)) {
            cyclePending.set(true);
            return;
        }
        queueCycle();
    }

    private void submitScheduledCycle() {
        if (closed.get() || reconciler.get() == null || !cycleInFlight.compareAndSet(false, true)) {
            return;
        }
        queueCycle();
    }

    private void queueCycle() {
        if (!workers.tryExecute(this::runCycle)) {
            completeCycle(false);
        }
    }

    private void runCycle() {
        try {
            DiscordRoleReconciler active = reconciler.get();
            if (!isCurrent(active)) {
                return;
            }
            List<DiscordUserId> users = service.nextUsers(cursor);
            if (users.isEmpty()) {
                cursor = Optional.empty();
                return;
            }
            for (DiscordUserId user : users) {
                if (!isCurrent(active)) {
                    return;
                }
                process(active, user);
                if (!isCurrent(active)) {
                    return;
                }
            }
            cursor = Optional.of(users.get(users.size() - 1));
        } catch (RuntimeException exception) {
            log("role_sync_cycle_failed", exception);
        } finally {
            completeCycle(true);
        }
    }

    private void process(DiscordRoleReconciler active, DiscordUserId userId) {
        if (!service.due(userId)) {
            return;
        }
        DiscordRoleSyncService.Evaluation evaluation;
        try {
            evaluation = service.evaluate(userId);
        } catch (RuntimeException exception) {
            if (isCurrent(active)) {
                recordEvaluationRetry(userId, exception);
            }
            return;
        }
        reconcile(active, evaluation);
    }

    private void reconcile(DiscordRoleReconciler active, DiscordRoleSyncService.Evaluation evaluation) {
        try {
            DiscordRoleReconciler.Result result = active.reconcile(evaluation);
            if (isCurrent(active)) {
                service.recordSuccess(evaluation, result.observedRoleIds(), result.state());
            }
        } catch (DiscordRoleReconciler.RetryableException exception) {
            if (isCurrent(active)) {
                recordRetry(evaluation, exception.observedRoleIds(), exception.errorCode(), exception);
            }
        } catch (RuntimeException exception) {
            if (isCurrent(active)) {
                recordRetry(evaluation, Set.of(), "role_sync_failure", exception);
            }
        }
    }

    private boolean isCurrent(DiscordRoleReconciler active) {
        return active != null && !closed.get() && reconciler.get() == active;
    }

    private void completeCycle(boolean allowPending) {
        cycleInFlight.set(false);
        synchronized (cycleMonitor) {
            cycleMonitor.notifyAll();
        }
        if (allowPending && cyclePending.getAndSet(false) && !closed.get()) {
            requestImmediateCycle();
        }
    }

    private void recordEvaluationRetry(DiscordUserId userId, RuntimeException original) {
        try {
            service.recordEvaluationRetry(userId, "eligibility_unavailable");
        } catch (RuntimeException persistenceFailure) {
            original.addSuppressed(persistenceFailure);
        }
        log("eligibility_unavailable", original);
    }

    private void recordRetry(
            DiscordRoleSyncService.Evaluation evaluation,
            Set<String> observed,
            String errorCode,
            RuntimeException original
    ) {
        try {
            service.recordRetry(evaluation.userId(), evaluation.desiredRoleIds(), observed, errorCode);
        } catch (RuntimeException persistenceFailure) {
            original.addSuppressed(persistenceFailure);
        }
        log(errorCode, original);
    }

    private static void log(String code, RuntimeException failure) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            LOGGER.log(System.Logger.Level.WARNING, "{0} type={1}", code, failure.getClass().getSimpleName());
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cyclePending.set(false);
        disable();
        scheduler.shutdownNow();
        awaitCycleQuiescence();
    }

    private void awaitCycleQuiescence() {
        long deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();
        synchronized (cycleMonitor) {
            while (cycleInFlight.get()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException("role-sync cycle did not quiesce before shutdown timeout");
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(cycleMonitor, remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("role-sync shutdown was interrupted", exception);
                }
            }
        }
    }
}
