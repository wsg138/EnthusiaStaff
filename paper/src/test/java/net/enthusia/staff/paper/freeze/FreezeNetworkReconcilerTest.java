package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.enthusia.staff.domain.ports.FreezeStore;
import org.junit.jupiter.api.Test;

class FreezeNetworkReconcilerTest {
    private static final Instant NOW = Instant.parse("2026-09-20T20:00:00Z");
    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Logger LOGGER = Logger.getLogger(FreezeNetworkReconcilerTest.class.getName());

    @Test
    void activeAuthoritativeStateAppliesOnOwningBackend() {
        MutableFreezeStore store = new MutableFreezeStore(true);
        RecordingRestrictions restrictions = new RecordingRestrictions(false);
        FreezeNetworkReconciler reconciler = reconciler(store, restrictions, presentRouter(), Runnable::run);

        assertTrue(reconciler.reconcile(PLAYER));
        assertTrue(restrictions.restricted(PLAYER));
        assertTrue(restrictions.applied);
        assertFalse(restrictions.released);
    }

    @Test
    void releasedAuthoritativeStateReleasesOnOwningBackend() {
        MutableFreezeStore store = new MutableFreezeStore(false);
        RecordingRestrictions restrictions = new RecordingRestrictions(true);
        FreezeNetworkReconciler reconciler = reconciler(store, restrictions, presentRouter(), Runnable::run);

        assertTrue(reconciler.reconcile(PLAYER));
        assertFalse(restrictions.restricted(PLAYER));
        assertFalse(restrictions.applied);
        assertTrue(restrictions.released);
    }

    @Test
    void duplicateAndOutOfOrderSignalsConvergeToCurrentDatabaseState() {
        MutableFreezeStore store = new MutableFreezeStore(true);
        RecordingRestrictions restrictions = new RecordingRestrictions(false);
        FreezeNetworkReconciler reconciler = reconciler(store, restrictions, presentRouter(), Runnable::run);

        assertTrue(reconciler.reconcile(PLAYER));
        restrictions.resetCalls();
        assertTrue(reconciler.reconcile(PLAYER));
        assertFalse(restrictions.applied);

        store.active = false;
        assertTrue(reconciler.reconcile(PLAYER));
        assertTrue(restrictions.released);
        restrictions.resetCalls();

        assertTrue(reconciler.reconcile(PLAYER));
        assertFalse(restrictions.applied);
        assertFalse(restrictions.released);
        assertFalse(restrictions.restricted(PLAYER));
    }

    @Test
    void nonOwningBackendAcknowledgesWithoutReadingStorage() {
        MutableFreezeStore store = new MutableFreezeStore(true);
        RecordingRestrictions restrictions = new RecordingRestrictions(false);
        FreezeNetworkReconciler reconciler = reconciler(store, restrictions, absentRouter(), Runnable::run);

        assertTrue(reconciler.reconcile(PLAYER));
        assertFalse(store.read);
        assertFalse(restrictions.applied);
    }

    @Test
    void ownershipLostAfterLookupDoesNotMutateRuntime() {
        MutableFreezeStore store = new MutableFreezeStore(true);
        RecordingRestrictions restrictions = new RecordingRestrictions(false);
        FreezeNetworkReconciler reconciler = reconciler(
                store, restrictions, presentThenAbsentRouter(), Runnable::run);

        assertTrue(reconciler.reconcile(PLAYER));
        assertTrue(store.read);
        assertFalse(restrictions.applied);
        assertFalse(restrictions.released);
    }

    @Test
    void schedulerFailureBeforeLookupLeavesSignalRetryable() {
        MutableFreezeStore store = new MutableFreezeStore(true);
        RecordingRestrictions restrictions = new RecordingRestrictions(false);
        FreezeNetworkReconciler reconciler = reconciler(store, restrictions, failedRouter(), Runnable::run);

        assertFalse(reconciler.reconcile(PLAYER));
        assertFalse(store.read);
        assertFalse(restrictions.applied);
    }

    @Test
    void schedulerFailureAfterLookupLeavesSignalRetryable() {
        MutableFreezeStore store = new MutableFreezeStore(true);
        RecordingRestrictions restrictions = new RecordingRestrictions(false);
        FreezeNetworkReconciler reconciler = reconciler(
                store, restrictions, presentThenFailedRouter(), Runnable::run);

        assertFalse(reconciler.reconcile(PLAYER));
        assertTrue(store.read);
        assertFalse(restrictions.applied);
        assertFalse(restrictions.released);
    }

    @Test
    void retryAfterSchedulerFailureEventuallyConverges() {
        MutableFreezeStore store = new MutableFreezeStore(true);
        RecordingRestrictions restrictions = new RecordingRestrictions(false);
        AtomicBoolean failFirst = new AtomicBoolean(true);
        FreezeNetworkReconciler.LocalTargetRouter router = (id, present, absent, failed) -> {
            if (failFirst.compareAndSet(true, false)) {
                failed.run();
            } else {
                present.run();
            }
        };
        FreezeNetworkReconciler reconciler = reconciler(store, restrictions, router, Runnable::run);

        assertFalse(reconciler.reconcile(PLAYER));
        assertTrue(reconciler.reconcile(PLAYER));
        assertTrue(restrictions.restricted(PLAYER));
    }

    @Test
    void storageOutageDoesNotAcknowledgeOrMutateRuntime() {
        MutableFreezeStore store = new MutableFreezeStore(true);
        store.failure = new IllegalStateException("database unavailable");
        RecordingRestrictions restrictions = new RecordingRestrictions(false);
        FreezeNetworkReconciler reconciler = reconciler(store, restrictions, presentRouter(), Runnable::run);

        assertFalse(reconciler.reconcile(PLAYER));
        assertFalse(restrictions.applied);
        assertFalse(restrictions.released);
    }

    @Test
    void missingStorageDoesNotAcknowledgeOrMutateRuntime() {
        RecordingRestrictions restrictions = new RecordingRestrictions(false);
        FreezeNetworkReconciler reconciler = new FreezeNetworkReconciler(
                CLOCK,
                () -> null,
                Runnable::run,
                restrictions,
                presentRouter(),
                LOGGER
        );

        assertFalse(reconciler.reconcile(PLAYER));
        assertFalse(restrictions.applied);
        assertFalse(restrictions.released);
    }

    @Test
    void workerRejectionLeavesSignalRetryable() {
        MutableFreezeStore store = new MutableFreezeStore(true);
        RecordingRestrictions restrictions = new RecordingRestrictions(false);
        Executor rejecting = operation -> {
            throw new RejectedExecutionException("full");
        };
        FreezeNetworkReconciler reconciler = reconciler(store, restrictions, presentRouter(), rejecting);

        assertFalse(reconciler.reconcile(PLAYER));
        assertFalse(store.read);
        assertFalse(restrictions.applied);
    }

    @Test
    void serverSwitchCanBeReconciledByNewOwningBackend() {
        MutableFreezeStore store = new MutableFreezeStore(true);
        RecordingRestrictions restrictions = new RecordingRestrictions(false);
        FreezeNetworkReconciler first = reconciler(store, restrictions, absentRouter(), Runnable::run);
        FreezeNetworkReconciler second = reconciler(store, restrictions, presentRouter(), Runnable::run);

        assertTrue(first.reconcile(PLAYER));
        assertFalse(restrictions.restricted(PLAYER));
        assertTrue(second.reconcile(PLAYER));
        assertTrue(restrictions.restricted(PLAYER));
    }

    private static FreezeNetworkReconciler reconciler(
            MutableFreezeStore store,
            RecordingRestrictions restrictions,
            FreezeNetworkReconciler.LocalTargetRouter router,
            Executor executor
    ) {
        return new FreezeNetworkReconciler(CLOCK, () -> store, executor, restrictions, router, LOGGER);
    }

    private static FreezeNetworkReconciler.LocalTargetRouter presentRouter() {
        return (playerId, present, absent, failed) -> present.run();
    }

    private static FreezeNetworkReconciler.LocalTargetRouter absentRouter() {
        return (playerId, present, absent, failed) -> absent.run();
    }

    private static FreezeNetworkReconciler.LocalTargetRouter failedRouter() {
        return (playerId, present, absent, failed) -> failed.run();
    }

    private static FreezeNetworkReconciler.LocalTargetRouter presentThenAbsentRouter() {
        return stagedRouter(false);
    }

    private static FreezeNetworkReconciler.LocalTargetRouter presentThenFailedRouter() {
        return stagedRouter(true);
    }

    private static FreezeNetworkReconciler.LocalTargetRouter stagedRouter(boolean failSecond) {
        AtomicBoolean firstDispatch = new AtomicBoolean(true);
        return (playerId, present, absent, failed) -> {
            if (firstDispatch.compareAndSet(true, false)) {
                present.run();
            } else if (failSecond) {
                failed.run();
            } else {
                absent.run();
            }
        };
    }

    private static final class RecordingRestrictions implements FreezeNetworkReconciler.RestrictionController {
        private boolean restricted;
        private boolean applied;
        private boolean released;

        private RecordingRestrictions(boolean restricted) {
            this.restricted = restricted;
        }

        @Override
        public boolean restricted(UUID playerId) {
            return restricted;
        }

        @Override
        public void apply(UUID playerId) {
            restricted = true;
            applied = true;
        }

        @Override
        public void release(UUID playerId) {
            restricted = false;
            released = true;
        }

        private void resetCalls() {
            applied = false;
            released = false;
        }
    }

    private static final class MutableFreezeStore implements FreezeStore {
        private boolean active;
        private boolean read;
        private RuntimeException failure;

        private MutableFreezeStore(boolean active) {
            this.active = active;
        }

        @Override
        public Optional<FreezeRecord> active(UUID playerId, Instant now) {
            read = true;
            if (failure != null) {
                throw failure;
            }
            return active ? Optional.of(record()) : Optional.empty();
        }

        private FreezeRecord record() {
            return new FreezeRecord(PLAYER, ACTOR, "investigation", NOW, Optional.empty(), false, 1L);
        }

        @Override
        public FreezeRecord apply(UUID playerId, UUID actorId, String reason, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean release(UUID playerId, UUID actorId, String reason, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean keepActive(UUID playerId, UUID actorId, String reason, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disconnected(UUID playerId, Instant offlineExpiration, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<FreezeRecord> readActive(UUID playerId, Instant now) {
            return active(playerId, now);
        }

        @Override
        public List<FreezeRecord> listActive(Instant now, int limit) {
            return active ? List.of(record()) : List.of();
        }
    }
}
