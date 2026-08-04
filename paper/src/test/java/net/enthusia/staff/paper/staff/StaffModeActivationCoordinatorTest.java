package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import org.junit.jupiter.api.Test;

class StaffModeActivationCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-01T20:00:00Z");
    private static final UUID PLAYER_ID = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("71000000-0000-0000-0000-000000000002");

    @Test
    void initialEntryFailureBeforeMutationQueuesDurableRecoveryWithoutPublishing() {
        Fixture fixture = new Fixture(new QueuedExecutor());
        AtomicBoolean recovery = new AtomicBoolean();

        boolean activated = fixture.activate(
                StaffModeActivationCoordinator.ActivationPath.INITIAL_ENTRY,
                () -> { throw new IllegalStateException("before mutation"); },
                () -> recovery.set(true)
        );

        assertFalse(activated);
        fixture.assertUnpublished();
        assertTrue(fixture.transitions.contains(PLAYER_ID));
        assertEquals(0, fixture.store.recoveryMarks);
        fixture.executor.runOnly();
        assertEquals(1, fixture.store.recoveryMarks);
        assertEquals(SESSION_ID, fixture.store.markedSessionId);
        assertTrue(recovery.get());
        assertTrue(fixture.messages.stream().anyMatch(message -> message.contains("recovery is pending")));
        fixture.assertLogContainsIdentifiers();
    }

    @Test
    void activeRecoveryFailureAfterDestructiveMutationQueuesDurableRecoveryWithoutPublishing() {
        Fixture fixture = new Fixture(new QueuedExecutor());
        AtomicBoolean inventoryCleared = new AtomicBoolean();

        boolean activated = fixture.activate(
                StaffModeActivationCoordinator.ActivationPath.ACTIVE_RECOVERY,
                () -> {
                    inventoryCleared.set(true);
                    throw new IllegalStateException("after inventory clear");
                },
                () -> fixture.transitions.remove(PLAYER_ID)
        );

        assertFalse(activated);
        assertTrue(inventoryCleared.get());
        fixture.assertUnpublished();
        fixture.executor.runOnly();
        assertEquals(1, fixture.store.recoveryMarks);
        assertFalse(fixture.transitions.contains(PLAYER_ID));
        fixture.assertLogContainsIdentifiers();
    }

    @Test
    void publicationFailureRollsBackPartialInMemoryPublicationAndMarksRecovery() {
        QueuedExecutor executor = new QueuedExecutor();
        Map<UUID, StaffSessionSnapshot> active = new HashMap<>();
        Map<UUID, StaffRank> ranks = new FailingRankMap();
        Fixture fixture = new Fixture(executor, active, ranks);

        assertFalse(fixture.activate(
                StaffModeActivationCoordinator.ActivationPath.INITIAL_ENTRY,
                () -> { },
                () -> fixture.transitions.remove(PLAYER_ID)
        ));

        fixture.assertUnpublished();
        executor.runOnly();
        assertEquals(1, fixture.store.recoveryMarks);
        assertFalse(fixture.transitions.contains(PLAYER_ID));
    }

    @Test
    void rejectedRecoveryQueueKeepsTransitionAndDoesNotPretendMarkerPersisted() {
        Fixture fixture = new Fixture(operation -> {
            throw new RejectedExecutionException("full");
        });

        assertFalse(fixture.activate(
                StaffModeActivationCoordinator.ActivationPath.ACTIVE_RECOVERY,
                () -> { throw new IllegalStateException("apply failed"); },
                () -> { throw new AssertionError("recovery must not run"); }
        ));

        fixture.assertUnpublished();
        assertEquals(0, fixture.store.recoveryMarks);
        assertTrue(fixture.transitions.contains(PLAYER_ID));
        assertTrue(fixture.messages.stream().anyMatch(message -> message.contains("could not be queued")));
        assertTrue(fixture.messages.stream().anyMatch(message -> message.contains("remains blocked")));
        assertFalse(fixture.messages.stream().anyMatch(message -> message.contains("recovery is pending")));
        fixture.assertLogContainsIdentifiers();
    }

    @Test
    void recoveryPersistenceFailureKeepsTransitionUntilDisconnectOrVerifiedRestore() {
        Fixture fixture = new Fixture(new QueuedExecutor());
        fixture.store.failRecovery = true;

        assertFalse(fixture.activate(
                StaffModeActivationCoordinator.ActivationPath.ACTIVE_RECOVERY,
                () -> { throw new IllegalStateException("apply failed"); },
                () -> fixture.transitions.remove(PLAYER_ID)
        ));

        fixture.executor.runOnly();

        fixture.assertUnpublished();
        assertEquals(0, fixture.store.recoveryMarks);
        assertTrue(fixture.transitions.contains(PLAYER_ID));
        assertTrue(fixture.messages.stream().anyMatch(message -> message.contains("administrator recovery")));
        assertTrue(fixture.messages.stream().anyMatch(message -> message.contains("remains blocked")));
        fixture.assertLogContainsIdentifiers();
    }

    @Test
    void successfulActivationPublishesAfterMutationAndClearsTransition() {
        Fixture fixture = new Fixture(Runnable::run);
        AtomicBoolean applied = new AtomicBoolean();

        assertTrue(fixture.activate(
                StaffModeActivationCoordinator.ActivationPath.INITIAL_ENTRY,
                () -> applied.set(true),
                () -> { throw new AssertionError("recovery must not run"); }
        ));

        assertTrue(applied.get());
        assertSame(fixture.session, fixture.active.get(PLAYER_ID));
        assertEquals(StaffRank.MOD, fixture.ranks.get(PLAYER_ID));
        assertFalse(fixture.transitions.contains(PLAYER_ID));
        assertEquals(List.of("success"), fixture.messages);
        assertEquals(0, fixture.store.recoveryMarks);
    }

    private static final class Fixture {
        private final RecordingStore store = new RecordingStore();
        private final RecordingHandler logs = new RecordingHandler();
        private final Logger logger = Logger.getAnonymousLogger();
        private final Map<UUID, StaffSessionSnapshot> active;
        private final Map<UUID, StaffRank> ranks;
        private final Set<UUID> transitions = new HashSet<>();
        private final List<String> messages = new ArrayList<>();
        private final StaffSessionSnapshot session = session();
        private final QueuedExecutor executor;
        private final StaffModeActivationCoordinator coordinator;

        private Fixture(Executor executor) {
            this(executor, new HashMap<>(), new HashMap<>());
        }

        private Fixture(
                Executor executor,
                Map<UUID, StaffSessionSnapshot> active,
                Map<UUID, StaffRank> ranks
        ) {
            this.active = active;
            this.ranks = ranks;
            this.executor = executor instanceof QueuedExecutor queued ? queued : null;
            logger.setUseParentHandlers(false);
            logger.addHandler(logs);
            transitions.add(PLAYER_ID);
            coordinator = new StaffModeActivationCoordinator(
                    Clock.fixed(NOW, ZoneOffset.UTC), executor, logger, active, ranks, transitions);
        }

        private boolean activate(
                StaffModeActivationCoordinator.ActivationPath path,
                Runnable apply,
                Runnable recovery
        ) {
            return coordinator.activate(
                    PLAYER_ID,
                    session,
                    store,
                    StaffRank.MOD,
                    path,
                    apply,
                    recovery,
                    messages::add,
                    "success"
            );
        }

        private void assertUnpublished() {
            assertFalse(active.containsKey(PLAYER_ID));
            assertFalse(ranks.containsKey(PLAYER_ID));
        }

        private void assertLogContainsIdentifiers() {
            assertTrue(logs.messages.stream().anyMatch(message ->
                    message.contains(PLAYER_ID.toString()) && message.contains(SESSION_ID.toString())));
        }
    }

    private static final class FailingRankMap extends AbstractMap<UUID, StaffRank> {
        private final Map<UUID, StaffRank> delegate = new HashMap<>();

        @Override
        public Set<Entry<UUID, StaffRank>> entrySet() {
            return delegate.entrySet();
        }

        @Override
        public StaffRank put(UUID key, StaffRank value) {
            throw new IllegalStateException("rank publication failed");
        }

        @Override
        public StaffRank remove(Object key) {
            return delegate.remove(key);
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final List<Runnable> operations = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            operations.add(command);
        }

        private void runOnly() {
            assertEquals(1, operations.size());
            operations.removeFirst().run();
        }
    }

    private static final class RecordingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingStore implements StaffSessionStore {
        private int recoveryMarks;
        private UUID markedSessionId;
        private boolean failRecovery;

        @Override
        public StaffSessionSnapshot begin(
                UUID staffId,
                String serverId,
                int schemaVersion,
                String checksum,
                byte[] snapshot,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StaffSessionSnapshot> active(UUID staffId) {
            return Optional.empty();
        }

        @Override
        public Optional<StaffSessionSnapshot> beginExit(UUID staffId, Instant now) {
            return Optional.empty();
        }

        @Override
        public boolean completeExit(UUID sessionId, String restoredChecksum, Instant now) {
            return false;
        }

        @Override
        public void recoveryRequired(UUID sessionId, String reason, Instant now) {
            if (failRecovery) {
                throw new IllegalStateException("recovery persistence unavailable");
            }
            recoveryMarks++;
            markedSessionId = sessionId;
        }

        @Override
        public boolean setVanish(UUID staffId, boolean vanished, Instant now) {
            return false;
        }
    }

    private static StaffSessionSnapshot session() {
        return new StaffSessionSnapshot(
                SESSION_ID,
                PLAYER_ID,
                "paper:test",
                StaffSessionState.ACTIVE,
                false,
                1,
                "0".repeat(64),
                new byte[]{1},
                NOW,
                1
        );
    }
}
