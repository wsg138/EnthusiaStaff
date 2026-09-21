package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.enthusia.staff.domain.ports.FreezeStore;
import org.junit.jupiter.api.Test;

class FreezeNetworkSchedulerBoundaryTest {
    private static final Instant NOW = Instant.parse("2026-09-20T20:00:00Z");
    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000091");
    private static final UUID ACTOR = UUID.fromString("20000000-0000-0000-0000-000000000091");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void genuinelyAbsentPlayerIsAValidNonOwnerOutcome() {
        FreezeSchedulerBoundaryHarness scheduler = new FreezeSchedulerBoundaryHarness().absent();
        RecordingStore store = new RecordingStore(true);
        AtomicBoolean restricted = new AtomicBoolean();

        assertTrue(reconciler(scheduler, store, restricted).reconcile(PLAYER));
        assertFalse(store.read);
        assertFalse(restricted.get());
        assertEquals(1, scheduler.globalDispatchCount());
        assertEquals(0, scheduler.entityDispatchCount());
    }

    @Test
    void entityRetirementIsFailureRatherThanAbsence() {
        assertFailureBeforeLookup(new FreezeSchedulerBoundaryHarness().retired());
    }

    @Test
    void entityExecuteFalseIsFailureRatherThanAbsence() {
        assertFailureBeforeLookup(new FreezeSchedulerBoundaryHarness().rejected());
    }

    @Test
    void entitySubmissionExceptionIsFailureRatherThanAbsence() {
        assertFailureBeforeLookup(new FreezeSchedulerBoundaryHarness().entityException());
    }

    @Test
    void globalDispatchExceptionIsFailureRatherThanAbsence() {
        FreezeSchedulerBoundaryHarness scheduler = new FreezeSchedulerBoundaryHarness().globalException();
        RecordingStore store = new RecordingStore(true);
        AtomicBoolean restricted = new AtomicBoolean();

        assertFalse(reconciler(scheduler, store, restricted).reconcile(PLAYER));
        assertFalse(store.read);
        assertFalse(restricted.get());
        assertEquals(1, scheduler.globalDispatchCount());
        assertEquals(0, scheduler.entityDispatchCount());
    }

    @Test
    void postLookupRetirementFailsWithoutMutatingStalePlayerState() {
        FreezeSchedulerBoundaryHarness scheduler = new FreezeSchedulerBoundaryHarness().owned().retired();
        RecordingStore store = new RecordingStore(true);
        AtomicBoolean restricted = new AtomicBoolean();

        assertFalse(reconciler(scheduler, store, restricted).reconcile(PLAYER));
        assertTrue(store.read);
        assertFalse(restricted.get());
        assertEquals(2, scheduler.globalDispatchCount());
        assertEquals(2, scheduler.entityDispatchCount());
    }

    @Test
    void ownershipDisappearingAfterLookupDoesNotMutateStalePlayerState() {
        FreezeSchedulerBoundaryHarness scheduler = new FreezeSchedulerBoundaryHarness().owned().absent();
        RecordingStore store = new RecordingStore(true);
        AtomicBoolean restricted = new AtomicBoolean();

        assertTrue(reconciler(scheduler, store, restricted).reconcile(PLAYER));
        assertTrue(store.read);
        assertFalse(restricted.get());
        assertEquals(2, scheduler.globalDispatchCount());
        assertEquals(1, scheduler.entityDispatchCount());
    }

    @Test
    void retryAfterPreLookupSchedulerFailureCanConverge() {
        FreezeSchedulerBoundaryHarness scheduler = new FreezeSchedulerBoundaryHarness()
                .rejected()
                .owned()
                .owned();
        RecordingStore store = new RecordingStore(true);
        AtomicBoolean restricted = new AtomicBoolean();
        FreezeNetworkReconciler reconciler = reconciler(scheduler, store, restricted);

        assertFalse(reconciler.reconcile(PLAYER));
        assertFalse(store.read);
        assertTrue(reconciler.reconcile(PLAYER));
        assertTrue(store.read);
        assertTrue(restricted.get());
    }

    @Test
    void retryAfterPostLookupSchedulerFailureCanConverge() {
        FreezeSchedulerBoundaryHarness scheduler = new FreezeSchedulerBoundaryHarness()
                .owned()
                .entityException()
                .owned()
                .owned();
        RecordingStore store = new RecordingStore(true);
        AtomicBoolean restricted = new AtomicBoolean();
        FreezeNetworkReconciler reconciler = reconciler(scheduler, store, restricted);

        assertFalse(reconciler.reconcile(PLAYER));
        assertTrue(store.read);
        assertFalse(restricted.get());
        store.read = false;
        assertTrue(reconciler.reconcile(PLAYER));
        assertTrue(store.read);
        assertTrue(restricted.get());
    }

    private static void assertFailureBeforeLookup(FreezeSchedulerBoundaryHarness scheduler) {
        RecordingStore store = new RecordingStore(true);
        AtomicBoolean restricted = new AtomicBoolean();

        assertFalse(reconciler(scheduler, store, restricted).reconcile(PLAYER));
        assertFalse(store.read);
        assertFalse(restricted.get());
        assertEquals(1, scheduler.globalDispatchCount());
        assertEquals(1, scheduler.entityDispatchCount());
    }

    private static FreezeNetworkReconciler reconciler(
            FreezeSchedulerBoundaryHarness scheduler,
            RecordingStore store,
            AtomicBoolean restricted
    ) {
        return scheduler.reconciler(CLOCK, store, restricted);
    }

    private static final class RecordingStore implements FreezeStore {
        private final boolean active;
        private boolean read;

        private RecordingStore(boolean active) {
            this.active = active;
        }

        @Override
        public Optional<FreezeRecord> active(UUID playerId, Instant now) {
            read = true;
            return active ? Optional.of(record()) : Optional.empty();
        }

        private FreezeRecord record() {
            return new FreezeRecord(PLAYER, ACTOR, "authoritative", NOW, Optional.empty(), false, 1L);
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
