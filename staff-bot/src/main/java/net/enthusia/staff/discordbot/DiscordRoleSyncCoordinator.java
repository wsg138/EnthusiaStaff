package net.enthusia.staff.discordbot;

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

    private final DiscordRoleSyncService service;
    private final StaffBotWorkerPool workers;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<DiscordRoleReconciler> reconciler = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean cycleInFlight = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
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
        reconciler.set(activeReconciler);
        scheduleOnce();
        submitCycle();
    }

    void disable() {
        reconciler.set(null);
    }

    private void scheduleOnce() {
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        long delay = service.configuration().interval().toSeconds();
        scheduler.scheduleWithFixedDelay(this::submitCycle, delay, delay, TimeUnit.SECONDS);
    }

    private void submitCycle() {
        if (closed.get() || reconciler.get() == null || !cycleInFlight.compareAndSet(false, true)) {
            return;
        }
        if (!workers.tryExecute(this::runCycle)) {
            cycleInFlight.set(false);
        }
    }

    private void runCycle() {
        try {
            DiscordRoleReconciler active = reconciler.get();
            if (active == null || closed.get()) {
                return;
            }
            List<DiscordUserId> users = service.nextUsers(cursor);
            if (users.isEmpty()) {
                cursor = Optional.empty();
                return;
            }
            for (DiscordUserId user : users) {
                if (reconciler.get() != active || closed.get()) {
                    return;
                }
                process(active, user);
            }
            cursor = Optional.of(users.get(users.size() - 1));
        } catch (RuntimeException exception) {
            log("role_sync_cycle_failed", exception);
        } finally {
            cycleInFlight.set(false);
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
            recordRetry(userId, Set.of(), Set.of(), "eligibility_unavailable", exception);
            return;
        }
        try {
            DiscordRoleReconciler.Result result = active.reconcile(evaluation);
            service.recordSuccess(evaluation, result.observedRoleIds(), result.state());
        } catch (DiscordRoleReconciler.RetryableException exception) {
            recordRetry(
                    userId,
                    evaluation.desiredRoleIds(),
                    exception.observedRoleIds(),
                    exception.errorCode(),
                    exception
            );
        } catch (RuntimeException exception) {
            recordRetry(userId, evaluation.desiredRoleIds(), Set.of(), "role_sync_failure", exception);
        }
    }

    private void recordRetry(
            DiscordUserId userId,
            Set<String> desired,
            Set<String> observed,
            String errorCode,
            RuntimeException original
    ) {
        try {
            service.recordRetry(userId, desired, observed, errorCode);
        } catch (RuntimeException persistenceFailure) {
            original.addSuppressed(persistenceFailure);
        }
        log(errorCode, original);
    }

    private static void log(String code, RuntimeException failure) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "{0} type={1}",
                    code,
                    failure.getClass().getSimpleName()
            );
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        reconciler.set(null);
        scheduler.shutdownNow();
    }
}
