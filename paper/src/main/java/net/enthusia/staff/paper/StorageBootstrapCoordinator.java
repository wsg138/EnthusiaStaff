package net.enthusia.staff.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.auth.StaffRank;

final class StorageBootstrapCoordinator<S> {
    private static final long CLEANUP_RETRY_DELAY_MILLIS = 50L;

    private final WorkerExecutor workers;
    private final GlobalScheduler global;
    private final RetryScheduler retries;
    private final StoragePhase<S> storagePhase;
    private final BukkitRecovery<S> recovery;
    private final FollowUp<S> followUp;
    private final BooleanSupplier stopping;
    private final Logger logger;
    private final RetryPolicy retryPolicy;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicBoolean retryScheduled = new AtomicBoolean();
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicReference<Attempt> activeAttempt = new AtomicReference<>();

    StorageBootstrapCoordinator(
            WorkerExecutor workers,
            GlobalScheduler global,
            RetryScheduler retries,
            StoragePhase<S> storagePhase,
            BukkitRecovery<S> recovery,
            FollowUp<S> followUp,
            BooleanSupplier stopping,
            Logger logger
    ) {
        this(
                workers,
                global,
                retries,
                storagePhase,
                recovery,
                followUp,
                stopping,
                logger,
                RetryPolicy.defaults()
        );
    }

    StorageBootstrapCoordinator(
            WorkerExecutor workers,
            GlobalScheduler global,
            RetryScheduler retries,
            StoragePhase<S> storagePhase,
            BukkitRecovery<S> recovery,
            FollowUp<S> followUp,
            BooleanSupplier stopping,
            Logger logger,
            RetryPolicy retryPolicy
    ) {
        this.workers = Objects.requireNonNull(workers, "workers");
        this.global = Objects.requireNonNull(global, "global");
        this.retries = Objects.requireNonNull(retries, "retries");
        this.storagePhase = Objects.requireNonNull(storagePhase, "storagePhase");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.followUp = Objects.requireNonNull(followUp, "followUp");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    boolean start() {
        return started.compareAndSet(false, true) && submitAttempt();
    }

    int attempts() {
        return attempts.get();
    }

    boolean retryScheduled() {
        return retryScheduled.get();
    }

    private boolean submitAttempt() {
        if (shouldStop()) {
            terminal.set(true);
            return false;
        }
        int number = attempts.incrementAndGet();
        Attempt attempt = new Attempt(number);
        if (!activeAttempt.compareAndSet(null, attempt)) {
            attempts.decrementAndGet();
            return false;
        }
        if (workers.submit(() -> runStoragePhase(attempt))) {
            return true;
        }
        activeAttempt.compareAndSet(attempt, null);
        scheduleRetry(attempt, new IllegalStateException("storage bootstrap worker submission was rejected"));
        return false;
    }

    private void runStoragePhase(Attempt attempt) {
        if (shouldAbort(attempt)) {
            retire(attempt);
            return;
        }
        S storage = null;
        try {
            storage = storagePhase.openAndPublish();
            if (storage == null) {
                retire(attempt);
                terminal.set(true);
                return;
            }
            if (shouldAbort(attempt) || !storagePhase.isPublished(storage)) {
                closeFromWorker(attempt, storage, null, false);
                return;
            }
            S published = storage;
            if (!global.execute(() -> beginRecovery(attempt, published))) {
                closeFromWorker(
                        attempt,
                        published,
                        new IllegalStateException("global bootstrap recovery submission was rejected"),
                        true
                );
            }
        } catch (RuntimeException exception) {
            if (storage != null) {
                closeFromWorker(attempt, storage, exception, true);
            } else {
                failAttempt(attempt, exception);
            }
        }
    }

    private void beginRecovery(Attempt attempt, S storage) {
        if (shouldAbort(attempt, storage)) {
            cleanupFromGlobal(attempt, storage, null, false);
            return;
        }
        final List<UUID> playerIds;
        try {
            playerIds = List.copyOf(recovery.onlinePlayerIds());
        } catch (RuntimeException exception) {
            cleanupFromGlobal(attempt, storage, exception, true);
            return;
        }
        if (playerIds.isEmpty()) {
            finishRecovery(attempt, storage, List.of());
            return;
        }

        ConcurrentHashMap<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
        AtomicInteger remaining = new AtomicInteger(playerIds.size());
        for (UUID playerId : playerIds) {
            AtomicBoolean completed = new AtomicBoolean();
            Runnable retired = () -> snapshotCompleted(
                    attempt, storage, snapshots, remaining, completed, null);
            try {
                recovery.capturePlayer(
                        playerId,
                        snapshot -> snapshotCompleted(
                                attempt, storage, snapshots, remaining, completed, snapshot),
                        retired
                );
            } catch (RuntimeException exception) {
                logger.log(Level.FINE, "Startup player snapshot scheduling failed", exception);
                retired.run();
            }
        }
    }

    private void snapshotCompleted(
            Attempt attempt,
            S storage,
            ConcurrentHashMap<UUID, PlayerSnapshot> snapshots,
            AtomicInteger remaining,
            AtomicBoolean completed,
            PlayerSnapshot snapshot
    ) {
        if (!completed.compareAndSet(false, true) || !isActive(attempt)) {
            return;
        }
        if (snapshot != null && !shouldStop()) {
            snapshots.put(snapshot.playerId(), snapshot);
        }
        if (remaining.decrementAndGet() != 0) {
            return;
        }
        List<PlayerSnapshot> immutable = new ArrayList<>(snapshots.values());
        immutable.sort((left, right) -> left.playerId().compareTo(right.playerId()));
        if (!global.execute(() -> finishRecovery(attempt, storage, List.copyOf(immutable)))) {
            submitWorkerCleanup(
                    attempt,
                    storage,
                    new IllegalStateException("final global bootstrap recovery submission was rejected"),
                    true
            );
        }
    }

    private void finishRecovery(Attempt attempt, S storage, List<PlayerSnapshot> snapshots) {
        try {
            for (PlayerSnapshot snapshot : snapshots) {
                if (!runRecoveryStep(attempt, storage, () -> recovery.verifyFreeze(snapshot))) {
                    return;
                }
            }
            for (PlayerSnapshot snapshot : snapshots) {
                if (!runRecoveryStep(attempt, storage, () -> recovery.recoverStaffMode(snapshot))) {
                    return;
                }
            }
            if (!runRecoveryStep(attempt, storage, recovery::initializeVanish)
                    || !runRecoveryStep(attempt, storage, () -> recovery.attachAlerts(storage))
                    || !runRecoveryStep(attempt, storage, () -> recovery.publishOperationalState(storage))) {
                return;
            }
            runRecoveryStep(attempt, storage, () -> {
                if (!workers.submit(() -> runFollowUp(attempt, storage))) {
                    throw new IllegalStateException("storage bootstrap follow-up was rejected");
                }
            });
        } catch (RuntimeException exception) {
            cleanupFromGlobal(attempt, storage, exception, true);
        }
    }

    private boolean runRecoveryStep(Attempt attempt, S storage, Runnable step) {
        if (shouldAbort(attempt, storage)) {
            cleanupFromGlobal(attempt, storage, null, false);
            return false;
        }
        step.run();
        return true;
    }

    private void runFollowUp(Attempt attempt, S storage) {
        if (shouldAbort(attempt, storage)) {
            closeFromWorker(attempt, storage, null, false);
            return;
        }
        try {
            followUp.run(storage);
            if (retire(attempt)) {
                terminal.set(true);
                storagePhase.recovered(attempt.number());
            }
        } catch (RuntimeException exception) {
            if (!stopping.getAsBoolean()) {
                logger.log(Level.SEVERE, "Storage bootstrap asynchronous follow-up failed", exception);
                followUp.failed(exception);
            }
            // Follow-up failure does not make the published database unavailable. Keep the
            // runtime open and terminal while reporting the feature-level failure separately.
            if (retire(attempt)) {
                terminal.set(true);
            }
        }
    }

    private void cleanupFromGlobal(
            Attempt attempt,
            S storage,
            RuntimeException failure,
            boolean retryAfterClose
    ) {
        if (!attempt.cleanupStarted().compareAndSet(false, true)) {
            return;
        }
        RuntimeException reported = failure;
        try {
            recovery.detachAlerts();
        } catch (RuntimeException detachFailure) {
            if (reported == null) {
                reported = detachFailure;
            } else {
                reported.addSuppressed(detachFailure);
            }
        }
        submitCleanup(attempt, storage, reported, retryAfterClose);
    }

    private void submitWorkerCleanup(
            Attempt attempt,
            S storage,
            RuntimeException failure,
            boolean retryAfterClose
    ) {
        if (!attempt.cleanupStarted().compareAndSet(false, true)) {
            return;
        }
        submitCleanup(attempt, storage, failure, retryAfterClose);
    }

    private void submitCleanup(
            Attempt attempt,
            S storage,
            RuntimeException failure,
            boolean retryAfterClose
    ) {
        if (workers.submit(() -> closeFromWorker(attempt, storage, failure, retryAfterClose))) {
            return;
        }
        if (stopping.getAsBoolean()) {
            // Keep conditionally published storage visible. Normal shutdown drains accepted work
            // and closes any remaining MariaDB runtime after the worker deadline.
            retire(attempt);
            terminal.set(true);
            return;
        }
        if (!retries.schedule(
                () -> submitCleanup(attempt, storage, failure, retryAfterClose),
                CLEANUP_RETRY_DELAY_MILLIS
        )) {
            logger.log(Level.SEVERE,
                    "MariaDB bootstrap cleanup could not be rescheduled; shutdown will close the runtime",
                    failure);
            retire(attempt);
            terminal.set(true);
            if (failure != null) {
                storagePhase.failed(failure);
            }
        }
    }

    private void closeFromWorker(
            Attempt attempt,
            S storage,
            RuntimeException failure,
            boolean retryAfterClose
    ) {
        if (!isActive(attempt)) {
            return;
        }
        storagePhase.removeAndClose(storage);
        if (retryAfterClose && failure != null) {
            failAttempt(attempt, failure);
        } else {
            retire(attempt);
            if (stopping.getAsBoolean()) {
                terminal.set(true);
            }
        }
    }

    private void failAttempt(Attempt attempt, RuntimeException failure) {
        if (!retire(attempt)) {
            return;
        }
        if (stopping.getAsBoolean()) {
            terminal.set(true);
            return;
        }
        logger.log(Level.WARNING,
                "MariaDB bootstrap attempt " + attempt.number() + " failed; runtime remains unavailable",
                failure);
        scheduleRetry(attempt, failure);
    }

    private void scheduleRetry(Attempt failedAttempt, RuntimeException failure) {
        if (failedAttempt.number() >= retryPolicy.maximumAttempts()) {
            terminal.set(true);
            logger.log(Level.SEVERE,
                    "MariaDB bootstrap retry limit reached; destructive actions remain disabled",
                    failure);
            storagePhase.failed(failure);
            return;
        }
        long delayMillis = retryPolicy.delayAfterFailure(failedAttempt.number());
        int nextAttempt = failedAttempt.number() + 1;
        if (!retryScheduled.compareAndSet(false, true)) {
            terminal.set(true);
            storagePhase.failed(new IllegalStateException(
                    "duplicate storage bootstrap retry scheduling was rejected", failure));
            return;
        }
        boolean scheduled = retries.schedule(() -> {
            retryScheduled.set(false);
            if (shouldStop()) {
                terminal.set(true);
                return;
            }
            submitAttempt();
        }, delayMillis);
        if (!scheduled) {
            retryScheduled.set(false);
            terminal.set(true);
            RuntimeException schedulingFailure = new IllegalStateException(
                    "storage bootstrap retry scheduling was rejected", failure);
            logger.log(Level.SEVERE, "MariaDB bootstrap retry could not be scheduled", schedulingFailure);
            storagePhase.failed(schedulingFailure);
            return;
        }
        storagePhase.retrying(nextAttempt, retryPolicy.maximumAttempts(), delayMillis, failure);
    }

    private boolean shouldAbort(Attempt attempt) {
        return shouldStop() || !isActive(attempt);
    }

    private boolean shouldAbort(Attempt attempt, S storage) {
        return shouldAbort(attempt) || !storagePhase.isPublished(storage);
    }

    private boolean shouldStop() {
        return terminal.get() || stopping.getAsBoolean();
    }

    private boolean isActive(Attempt attempt) {
        return activeAttempt.get() == attempt;
    }

    private boolean retire(Attempt attempt) {
        return activeAttempt.compareAndSet(attempt, null);
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

    record PlayerSnapshot(UUID playerId, String playerName, StaffRank rank) {
        PlayerSnapshot {
            Objects.requireNonNull(playerId, "playerId");
            if (playerName == null || playerName.isBlank()) {
                playerName = playerId.toString();
            }
        }
    }

    private static final class Attempt {
        private final int number;
        private final AtomicBoolean cleanupStarted = new AtomicBoolean();

        private Attempt(int number) {
            this.number = number;
        }

        private int number() {
            return number;
        }

        private AtomicBoolean cleanupStarted() {
            return cleanupStarted;
        }
    }

    @FunctionalInterface
    interface WorkerExecutor {
        boolean submit(Runnable operation);
    }

    @FunctionalInterface
    interface GlobalScheduler {
        boolean execute(Runnable operation);
    }

    @FunctionalInterface
    interface RetryScheduler {
        boolean schedule(Runnable operation, long delayMillis);
    }

    interface StoragePhase<S> {
        S openAndPublish();

        boolean isPublished(S storage);

        void removeAndClose(S storage);

        void failed(RuntimeException failure);

        default void retrying(
                int nextAttempt,
                int maximumAttempts,
                long delayMillis,
                RuntimeException failure
        ) {
        }

        default void recovered(int attempts) {
        }
    }

    interface BukkitRecovery<S> {
        List<UUID> onlinePlayerIds();

        void capturePlayer(UUID playerId, Consumer<PlayerSnapshot> captured, Runnable retired);

        void verifyFreeze(PlayerSnapshot snapshot);

        void recoverStaffMode(PlayerSnapshot snapshot);

        void initializeVanish();

        void attachAlerts(S storage);

        void detachAlerts();

        void publishOperationalState(S storage);
    }

    interface FollowUp<S> {
        void run(S storage);

        void failed(RuntimeException failure);
    }
}
