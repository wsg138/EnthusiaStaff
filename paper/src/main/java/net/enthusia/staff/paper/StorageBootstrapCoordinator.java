package net.enthusia.staff.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.auth.StaffRank;

final class StorageBootstrapCoordinator<S> {
    private final WorkerExecutor workers;
    private final GlobalScheduler global;
    private final RetryScheduler cleanupRetry;
    private final StoragePhase<S> storagePhase;
    private final BukkitRecovery<S> recovery;
    private final FollowUp<S> followUp;
    private final BooleanSupplier stopping;
    private final Logger logger;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean terminal = new AtomicBoolean();

    StorageBootstrapCoordinator(
            WorkerExecutor workers,
            GlobalScheduler global,
            RetryScheduler cleanupRetry,
            StoragePhase<S> storagePhase,
            BukkitRecovery<S> recovery,
            FollowUp<S> followUp,
            BooleanSupplier stopping,
            Logger logger
    ) {
        this.workers = Objects.requireNonNull(workers, "workers");
        this.global = Objects.requireNonNull(global, "global");
        this.cleanupRetry = Objects.requireNonNull(cleanupRetry, "cleanupRetry");
        this.storagePhase = Objects.requireNonNull(storagePhase, "storagePhase");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.followUp = Objects.requireNonNull(followUp, "followUp");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    boolean start() {
        return started.compareAndSet(false, true) && workers.submit(this::runStoragePhase);
    }

    private void runStoragePhase() {
        if (shouldStop()) {
            terminal.set(true);
            return;
        }
        S storage = null;
        try {
            storage = storagePhase.openAndPublish();
            if (storage == null) {
                terminal.set(true);
                return;
            }
            if (shouldStop() || !storagePhase.isPublished(storage)) {
                closeFromWorker(storage);
                return;
            }
            S published = storage;
            if (!global.execute(() -> beginRecovery(published))) {
                closeFromWorker(published);
            }
        } catch (RuntimeException exception) {
            if (storage != null) {
                closeFromWorker(storage);
            } else {
                terminal.set(true);
            }
            if (!stopping.getAsBoolean()) {
                logger.log(Level.SEVERE, "MariaDB bootstrap failed; destructive actions are disabled", exception);
                storagePhase.failed(exception);
            }
        }
    }

    private void beginRecovery(S storage) {
        if (shouldAbort(storage)) {
            cleanupFromGlobal(storage, null);
            return;
        }
        final List<UUID> playerIds;
        try {
            playerIds = List.copyOf(recovery.onlinePlayerIds());
        } catch (RuntimeException exception) {
            cleanupFromGlobal(storage, exception);
            return;
        }
        if (playerIds.isEmpty()) {
            finishRecovery(storage, List.of());
            return;
        }

        ConcurrentHashMap<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
        AtomicInteger remaining = new AtomicInteger(playerIds.size());
        for (UUID playerId : playerIds) {
            AtomicBoolean completed = new AtomicBoolean();
            Runnable retired = () -> snapshotCompleted(
                    storage, snapshots, remaining, completed, null);
            try {
                recovery.capturePlayer(
                        playerId,
                        snapshot -> snapshotCompleted(
                                storage, snapshots, remaining, completed, snapshot),
                        retired
                );
            } catch (RuntimeException exception) {
                logger.log(Level.FINE, "Startup player snapshot scheduling failed", exception);
                retired.run();
            }
        }
    }

    private void snapshotCompleted(
            S storage,
            ConcurrentHashMap<UUID, PlayerSnapshot> snapshots,
            AtomicInteger remaining,
            AtomicBoolean completed,
            PlayerSnapshot snapshot
    ) {
        if (!completed.compareAndSet(false, true)) {
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
        if (!global.execute(() -> finishRecovery(storage, List.copyOf(immutable)))) {
            submitWorkerCleanup(storage);
        }
    }

    private void finishRecovery(S storage, List<PlayerSnapshot> snapshots) {
        try {
            if (shouldAbort(storage)) {
                cleanupFromGlobal(storage, null);
                return;
            }
            for (PlayerSnapshot snapshot : snapshots) {
                if (shouldAbort(storage)) {
                    cleanupFromGlobal(storage, null);
                    return;
                }
                recovery.verifyFreeze(snapshot);
            }
            for (PlayerSnapshot snapshot : snapshots) {
                if (shouldAbort(storage)) {
                    cleanupFromGlobal(storage, null);
                    return;
                }
                recovery.recoverStaffMode(snapshot);
            }
            if (shouldAbort(storage)) {
                cleanupFromGlobal(storage, null);
                return;
            }
            recovery.initializeVanish();
            if (shouldAbort(storage)) {
                cleanupFromGlobal(storage, null);
                return;
            }
            recovery.attachAlerts(storage);
            if (shouldAbort(storage)) {
                cleanupFromGlobal(storage, null);
                return;
            }
            recovery.publishOperationalState(storage);
            if (shouldAbort(storage)) {
                cleanupFromGlobal(storage, null);
                return;
            }
            if (!workers.submit(() -> runFollowUp(storage))) {
                cleanupFromGlobal(storage,
                        new IllegalStateException("storage bootstrap follow-up was rejected"));
            }
        } catch (RuntimeException exception) {
            cleanupFromGlobal(storage, exception);
        }
    }

    private void runFollowUp(S storage) {
        if (shouldAbort(storage)) {
            closeFromWorker(storage);
            return;
        }
        try {
            followUp.run(storage);
            terminal.set(true);
        } catch (RuntimeException exception) {
            if (!stopping.getAsBoolean()) {
                logger.log(Level.SEVERE, "Storage bootstrap asynchronous follow-up failed", exception);
                followUp.failed(exception);
            }
            terminal.set(true);
        }
    }

    private void cleanupFromGlobal(S storage, RuntimeException failure) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        try {
            recovery.detachAlerts();
        } catch (RuntimeException detachFailure) {
            if (failure == null) {
                failure = detachFailure;
            } else {
                failure.addSuppressed(detachFailure);
            }
        }
        submitCleanup(storage, failure);
    }

    private void submitWorkerCleanup(S storage) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        submitCleanup(storage, null);
    }

    private void submitCleanup(S storage, RuntimeException failure) {
        if (workers.submit(() -> {
            storagePhase.removeAndClose(storage);
            if (failure != null && !stopping.getAsBoolean()) {
                storagePhase.failed(failure);
            }
        })) {
            return;
        }
        if (stopping.getAsBoolean()) {
            // Keep the conditionally published storage visible. Normal plugin shutdown drains
            // accepted workers and closes the remaining MariaDB runtime afterward.
            return;
        }
        if (!cleanupRetry.schedule(() -> submitCleanup(storage, failure))) {
            logger.log(Level.SEVERE,
                    "MariaDB bootstrap cleanup could not be rescheduled; shutdown will close the runtime",
                    failure);
            if (failure != null) {
                storagePhase.failed(failure);
            }
        }
    }

    private void closeFromWorker(S storage) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        storagePhase.removeAndClose(storage);
    }

    private boolean shouldAbort(S storage) {
        return shouldStop() || !storagePhase.isPublished(storage);
    }

    private boolean shouldStop() {
        return terminal.get() || stopping.getAsBoolean();
    }

    record PlayerSnapshot(UUID playerId, String playerName, StaffRank rank) {
        PlayerSnapshot {
            Objects.requireNonNull(playerId, "playerId");
            if (playerName == null || playerName.isBlank()) {
                playerName = playerId.toString();
            }
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
        boolean schedule(Runnable operation);
    }

    interface StoragePhase<S> {
        S openAndPublish();

        boolean isPublished(S storage);

        void removeAndClose(S storage);

        void failed(RuntimeException failure);
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
