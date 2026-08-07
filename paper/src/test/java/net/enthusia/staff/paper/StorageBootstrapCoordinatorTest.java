package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class StorageBootstrapCoordinatorTest {
    private static final UUID FIRST = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("71000000-0000-0000-0000-000000000002");

    @Test
    void storageWorkerGlobalEntityAndFollowUpPhasesRemainExplicitlySeparated() {
        Harness harness = new Harness();

        assertTrue(harness.coordinator.start());
        assertEquals(1, harness.workers.size());
        assertTrue(harness.events.isEmpty());

        harness.workers.runNext();
        assertEquals(List.of("WORKER:open:1"), harness.events);
        assertEquals(1, harness.global.size());

        harness.global.runNext();
        assertEquals(List.of(
                "WORKER:open:1",
                "GLOBAL:online-ids",
                "GLOBAL:capture:" + FIRST,
                "GLOBAL:capture:" + SECOND
        ), harness.events);
        assertEquals(2, harness.entities.size());
        assertFalse(harness.recoveryPublished.get());

        harness.entities.runNext();
        harness.entities.runNext();
        assertEquals(1, harness.global.size());
        assertFalse(harness.recoveryPublished.get());

        harness.global.runNext();
        assertTrue(harness.recoveryPublished.get());
        assertEquals(1, harness.workers.size());
        harness.workers.runNext();

        assertEquals(List.of(
                "WORKER:open:1",
                "GLOBAL:online-ids",
                "GLOBAL:capture:" + FIRST,
                "GLOBAL:capture:" + SECOND,
                "ENTITY:snapshot:" + FIRST,
                "ENTITY:snapshot:" + SECOND,
                "GLOBAL:freeze:" + FIRST,
                "GLOBAL:freeze:" + SECOND,
                "GLOBAL:staff:" + FIRST,
                "GLOBAL:staff:" + SECOND,
                "GLOBAL:vanish",
                "GLOBAL:alerts",
                "GLOBAL:health",
                "WORKER:follow-up",
                "WORKER:recovered:1"
        ), harness.events);
    }

    @Test
    void transientOpenFailureUsesCappedDelayedRetryAndRecoversWithoutRestart() {
        Harness harness = new Harness(new StorageBootstrapCoordinator.RetryPolicy(3, 100L, 500L));
        harness.openFailuresRemaining = 1;

        assertTrue(harness.coordinator.start());
        harness.workers.runNext();

        assertEquals(1, harness.coordinator.attempts());
        assertTrue(harness.coordinator.retryScheduled());
        assertEquals(List.of(100L), harness.retryDelays);
        assertTrue(harness.events.contains("WORKER:retrying:2:3:100"));
        assertEquals(1, harness.retries.size());
        assertFalse(harness.published.get());

        harness.retries.runNext();
        assertFalse(harness.coordinator.retryScheduled());
        assertEquals(1, harness.workers.size());
        harness.workers.runNext();
        harness.runSuccessfulRecovery();

        assertEquals(2, harness.coordinator.attempts());
        assertTrue(harness.published.get());
        assertTrue(harness.recoveryPublished.get());
        assertTrue(harness.events.contains("WORKER:recovered:2"));
        assertFalse(harness.events.stream().anyMatch(value -> value.endsWith(":failed")));
    }

    @Test
    void rejectedInitialWorkerSubmissionUsesSameRetryBudget() {
        Harness harness = new Harness(new StorageBootstrapCoordinator.RetryPolicy(2, 75L, 75L));
        harness.workers.rejectNext = true;

        assertFalse(harness.coordinator.start());
        assertEquals(1, harness.coordinator.attempts());
        assertTrue(harness.coordinator.retryScheduled());
        assertEquals(List.of(75L), harness.retryDelays);
        assertTrue(harness.events.contains("NONE:retrying:2:2:75"));

        harness.retries.runNext();
        harness.workers.runNext();
        harness.runSuccessfulRecovery();

        assertEquals(2, harness.coordinator.attempts());
        assertTrue(harness.published.get());
        assertTrue(harness.events.contains("WORKER:recovered:2"));
    }

    @Test
    void retryExhaustionStopsAfterConfiguredAttemptCount() {
        Harness harness = new Harness(new StorageBootstrapCoordinator.RetryPolicy(3, 100L, 500L));
        harness.openFailuresRemaining = 3;

        harness.coordinator.start();
        harness.workers.runNext();
        harness.retries.runNext();
        harness.workers.runNext();
        harness.retries.runNext();
        harness.workers.runNext();

        assertEquals(3, harness.coordinator.attempts());
        assertEquals(List.of(100L, 200L), harness.retryDelays);
        assertTrue(harness.events.contains("WORKER:failed"));
        assertEquals(0, harness.retries.size());
        assertEquals(0, harness.global.size());
        assertFalse(harness.published.get());
    }

    @Test
    void shutdownBeforeScheduledRetryPreventsAnotherWorkerAttempt() {
        Harness harness = new Harness(new StorageBootstrapCoordinator.RetryPolicy(3, 100L, 500L));
        harness.openFailuresRemaining = 1;

        harness.coordinator.start();
        harness.workers.runNext();
        harness.stopping.set(true);
        harness.retries.runNext();

        assertEquals(1, harness.coordinator.attempts());
        assertEquals(0, harness.workers.size());
        assertFalse(harness.published.get());
    }

    @Test
    void recoveryFailureClosesPublishedStorageBeforeRetrying() {
        Harness harness = new Harness(new StorageBootstrapCoordinator.RetryPolicy(2, 75L, 75L));
        harness.freezeFailuresRemaining = 1;

        harness.coordinator.start();
        harness.workers.runNext();
        harness.global.runNext();
        harness.entities.runAll();
        harness.global.runNext();
        harness.workers.runNext();

        assertEquals(1, harness.closeCalls);
        assertFalse(harness.published.get());
        assertEquals(List.of(75L), harness.retryDelays);

        harness.retries.runNext();
        harness.workers.runNext();
        harness.runSuccessfulRecovery();

        assertTrue(harness.published.get());
        assertTrue(harness.events.contains("WORKER:recovered:2"));
        assertEquals(1, harness.closeCalls);
    }

    @Test
    void retiredEntityTaskIsExcludedFromStartupSnapshot() {
        Harness harness = new Harness();
        harness.retireSecond = true;

        harness.coordinator.start();
        harness.workers.runNext();
        harness.global.runNext();
        harness.entities.runNext();
        harness.entities.runNext();
        harness.global.runNext();
        harness.workers.runNext();

        assertTrue(harness.events.contains("GLOBAL:freeze:" + FIRST));
        assertFalse(harness.events.contains("GLOBAL:freeze:" + SECOND));
        assertTrue(harness.events.contains("WORKER:follow-up"));
    }

    @Test
    void shutdownAfterStoragePublicationSkipsPlayerRecoveryAndClosesOnWorker() {
        Harness harness = new Harness();
        harness.coordinator.start();
        harness.workers.runNext();
        harness.stopping.set(true);

        harness.global.runNext();
        assertFalse(harness.recoveryPublished.get());
        assertEquals(1, harness.workers.size());
        harness.workers.runNext();

        assertTrue(harness.events.contains("GLOBAL:detach-alerts"));
        assertTrue(harness.events.contains("WORKER:remove-close"));
        assertFalse(harness.events.stream().anyMatch(value -> value.startsWith("GLOBAL:freeze:")));
        assertFalse(harness.events.contains("WORKER:follow-up"));
    }

    @Test
    void bukkitRecoveryFailureDetachesBeforeAsynchronousStorageCloseAndLateCallbacksDoNothing() {
        Harness harness = new Harness();
        harness.freezeFailuresRemaining = 1;
        harness.coordinator.start();
        harness.workers.runNext();
        harness.global.runNext();
        harness.entities.runNext();
        harness.entities.runNext();
        harness.global.runNext();

        int detach = harness.events.indexOf("GLOBAL:detach-alerts");
        assertTrue(detach >= 0);
        assertEquals(1, harness.workers.size());
        harness.workers.runNext();
        int close = harness.events.indexOf("WORKER:remove-close");
        assertTrue(close > detach);
        assertFalse(harness.events.contains("WORKER:follow-up"));

        harness.global.runAll();
        harness.workers.runAll();
        assertEquals(1, harness.closeCalls);
        assertFalse(harness.events.contains("WORKER:follow-up"));
        assertTrue(harness.events.contains("WORKER:failed"));
    }

    @Test
    void rejectedWorkerCleanupIsRetriedWithoutClosingMariaDbOnBukkitScheduler() {
        Harness harness = new Harness();
        harness.freezeFailuresRemaining = 1;
        harness.coordinator.start();
        harness.workers.runNext();
        harness.global.runNext();
        harness.entities.runNext();
        harness.entities.runNext();
        harness.workers.rejectNext = true;

        harness.global.runNext();

        assertEquals(0, harness.closeCalls);
        assertEquals(1, harness.retries.size());
        assertEquals(List.of(50L), harness.retryDelays);
        harness.retries.runNext();
        assertEquals(1, harness.workers.size());
        harness.workers.runNext();
        assertEquals(1, harness.closeCalls);
        assertTrue(harness.events.contains("WORKER:remove-close"));
        assertTrue(harness.events.contains("WORKER:failed"));
    }

    private enum Role {
        NONE,
        WORKER,
        GLOBAL,
        ENTITY,
        RETRY
    }

    private static final class Harness {
        private final AtomicReference<Role> role = new AtomicReference<>(Role.NONE);
        private final RoleQueue workers = new RoleQueue(role, Role.WORKER);
        private final RoleQueue global = new RoleQueue(role, Role.GLOBAL);
        private final RoleQueue entities = new RoleQueue(role, Role.ENTITY);
        private final RoleQueue retries = new RoleQueue(role, Role.RETRY);
        private final AtomicBoolean stopping = new AtomicBoolean();
        private final AtomicBoolean published = new AtomicBoolean();
        private final AtomicBoolean recoveryPublished = new AtomicBoolean();
        private final List<String> events = new ArrayList<>();
        private final List<Long> retryDelays = new ArrayList<>();
        private boolean retireSecond;
        private int openFailuresRemaining;
        private int freezeFailuresRemaining;
        private int openCalls;
        private int closeCalls;
        private final StorageBootstrapCoordinator<String> coordinator;

        private Harness() {
            this(new StorageBootstrapCoordinator.RetryPolicy(1, 1L, 1L));
        }

        private Harness(StorageBootstrapCoordinator.RetryPolicy retryPolicy) {
            coordinator = new StorageBootstrapCoordinator<>(
                    workers::offer,
                    global::offer,
                    this::scheduleRetry,
                    new StoragePhaseStub(),
                    new RecoveryStub(),
                    new FollowUpStub(),
                    stopping::get,
                    Logger.getLogger("StorageBootstrapCoordinatorTest"),
                    retryPolicy
            );
        }

        private boolean scheduleRetry(Runnable operation, long delayMillis) {
            retryDelays.add(delayMillis);
            return retries.offer(operation);
        }

        private void runSuccessfulRecovery() {
            global.runNext();
            entities.runAll();
            global.runNext();
            workers.runNext();
        }

        private void require(Role expected) {
            assertEquals(expected, role.get());
        }

        private final class StoragePhaseStub implements StorageBootstrapCoordinator.StoragePhase<String> {
            @Override
            public String openAndPublish() {
                require(Role.WORKER);
                openCalls++;
                events.add("WORKER:open:" + openCalls);
                if (openFailuresRemaining > 0) {
                    openFailuresRemaining--;
                    throw new IllegalStateException("transient open failure");
                }
                published.set(true);
                return "storage";
            }

            @Override
            public boolean isPublished(String storage) {
                return published.get();
            }

            @Override
            public void removeAndClose(String storage) {
                require(Role.WORKER);
                if (published.compareAndSet(true, false)) {
                    closeCalls++;
                }
                events.add("WORKER:remove-close");
            }

            @Override
            public void failed(RuntimeException failure) {
                events.add(role.get() + ":failed");
            }

            @Override
            public void retrying(
                    int nextAttempt,
                    int maximumAttempts,
                    long delayMillis,
                    RuntimeException failure
            ) {
                events.add(role.get() + ":retrying:" + nextAttempt + ':' + maximumAttempts + ':' + delayMillis);
            }

            @Override
            public void recovered(int attempts) {
                events.add(role.get() + ":recovered:" + attempts);
            }
        }

        private final class RecoveryStub implements StorageBootstrapCoordinator.BukkitRecovery<String> {
            @Override
            public List<UUID> onlinePlayerIds() {
                require(Role.GLOBAL);
                events.add("GLOBAL:online-ids");
                return List.of(FIRST, SECOND);
            }

            @Override
            public void capturePlayer(
                    UUID playerId,
                    Consumer<StorageBootstrapCoordinator.PlayerSnapshot> captured,
                    Runnable retired
            ) {
                require(Role.GLOBAL);
                events.add("GLOBAL:capture:" + playerId);
                entities.offer(() -> {
                    require(Role.ENTITY);
                    if (retireSecond && playerId.equals(SECOND)) {
                        retired.run();
                        return;
                    }
                    events.add("ENTITY:snapshot:" + playerId);
                    captured.accept(new StorageBootstrapCoordinator.PlayerSnapshot(
                            playerId, "Player", StaffRank.MOD));
                });
            }

            @Override
            public void verifyFreeze(StorageBootstrapCoordinator.PlayerSnapshot snapshot) {
                require(Role.GLOBAL);
                events.add("GLOBAL:freeze:" + snapshot.playerId());
                if (freezeFailuresRemaining > 0) {
                    freezeFailuresRemaining--;
                    throw new IllegalStateException("freeze failure");
                }
            }

            @Override
            public void recoverStaffMode(StorageBootstrapCoordinator.PlayerSnapshot snapshot) {
                require(Role.GLOBAL);
                events.add("GLOBAL:staff:" + snapshot.playerId());
            }

            @Override
            public void initializeVanish() {
                require(Role.GLOBAL);
                events.add("GLOBAL:vanish");
            }

            @Override
            public void attachAlerts(String storage) {
                require(Role.GLOBAL);
                events.add("GLOBAL:alerts");
            }

            @Override
            public void detachAlerts() {
                require(Role.GLOBAL);
                events.add("GLOBAL:detach-alerts");
            }

            @Override
            public void publishOperationalState(String storage) {
                require(Role.GLOBAL);
                events.add("GLOBAL:health");
                recoveryPublished.set(true);
            }
        }

        private final class FollowUpStub implements StorageBootstrapCoordinator.FollowUp<String> {
            @Override
            public void run(String storage) {
                require(Role.WORKER);
                events.add("WORKER:follow-up");
            }

            @Override
            public void failed(RuntimeException failure) {
                events.add(role.get() + ":follow-up-failed");
            }
        }
    }

    private static final class RoleQueue {
        private final AtomicReference<Role> role;
        private final Role executionRole;
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean rejectNext;

        private RoleQueue(AtomicReference<Role> role, Role executionRole) {
            this.role = role;
            this.executionRole = executionRole;
        }

        private boolean offer(Runnable operation) {
            if (rejectNext) {
                rejectNext = false;
                return false;
            }
            tasks.add(operation);
            return true;
        }

        private int size() {
            return tasks.size();
        }

        private void runNext() {
            Runnable operation = tasks.remove();
            role.set(executionRole);
            try {
                operation.run();
            } finally {
                role.set(Role.NONE);
            }
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                runNext();
            }
        }
    }
}
