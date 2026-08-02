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
        assertEquals(List.of("WORKER:open"), harness.events);
        assertEquals(1, harness.global.size());

        harness.global.runNext();
        assertEquals(List.of(
                "WORKER:open",
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
                "WORKER:open",
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
                "WORKER:follow-up"
        ), harness.events);
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
        harness.failFreeze = true;
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
    }

    @Test
    void rejectedWorkerCleanupIsRetriedWithoutClosingMariaDbOnBukkitScheduler() {
        Harness harness = new Harness();
        harness.failFreeze = true;
        harness.coordinator.start();
        harness.workers.runNext();
        harness.global.runNext();
        harness.entities.runNext();
        harness.entities.runNext();
        harness.workers.rejectNext = true;

        harness.global.runNext();

        assertEquals(0, harness.closeCalls);
        assertEquals(1, harness.retries.size());
        harness.retries.runNext();
        assertEquals(1, harness.workers.size());
        harness.workers.runNext();
        assertEquals(1, harness.closeCalls);
        assertTrue(harness.events.contains("WORKER:remove-close"));
    }

    private enum Role { NONE, WORKER, GLOBAL, ENTITY, RETRY }

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
        private boolean retireSecond;
        private boolean failFreeze;
        private int closeCalls;
        private final StorageBootstrapCoordinator<String> coordinator = new StorageBootstrapCoordinator<>(
                workers::offer,
                global::offer,
                retries::offer,
                new StorageBootstrapCoordinator.StoragePhase<>() {
                    @Override
                    public String openAndPublish() {
                        require(Role.WORKER);
                        events.add("WORKER:open");
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
                },
                new StorageBootstrapCoordinator.BukkitRecovery<>() {
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
                        if (failFreeze) {
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
                },
                new StorageBootstrapCoordinator.FollowUp<>() {
                    @Override
                    public void run(String storage) {
                        require(Role.WORKER);
                        events.add("WORKER:follow-up");
                    }

                    @Override
                    public void failed(RuntimeException failure) {
                        events.add(role.get() + ":follow-up-failed");
                    }
                },
                stopping::get,
                Logger.getLogger("StorageBootstrapCoordinatorTest")
        );

        private void require(Role expected) {
            assertEquals(expected, role.get());
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
