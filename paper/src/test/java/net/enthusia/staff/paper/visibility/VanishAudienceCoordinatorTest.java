package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

final class VanishAudienceCoordinatorTest {
    private static final String VIEWER_NAME = "viewer";
    private static final String TARGET_NAME = "target";
    private static final UUID VIEWER_ID = UUID.fromString("7e7819d4-33f2-43d7-ac2d-63ef13b32d4d");
    private static final UUID TARGET_ID = UUID.fromString("fb716257-f247-4f73-b51c-c457dcccf025");

    @Test
    void targetRefreshRunsEachPairOnTheViewerOwner() {
        Fixture fixture = new Fixture();
        EntityHandle viewer = new EntityHandle(VIEWER_NAME);
        EntityHandle target = new EntityHandle(TARGET_NAME);
        fixture.audiences.register(VIEWER_ID, viewer, GameMode.SURVIVAL);
        fixture.audiences.register(TARGET_ID, target, GameMode.SPECTATOR);

        fixture.audiences.refreshTarget(TARGET_ID);

        assertTrue(fixture.refreshed.isEmpty());
        assertEquals(Set.of(viewer, target), new HashSet<>(fixture.scheduler.owners()));

        fixture.scheduler.runAll();

        assertEquals(Set.of("viewer->target:SPECTATOR", "target->target:SPECTATOR"),
                Set.copyOf(fixture.refreshed));
    }

    @Test
    void staleViewerTaskCannotMutateAReconnectedSession() {
        Fixture fixture = new Fixture();
        EntityHandle oldViewer = new EntityHandle("old-viewer");
        fixture.audiences.register(VIEWER_ID, oldViewer, GameMode.SURVIVAL);
        fixture.audiences.register(TARGET_ID, new EntityHandle(TARGET_NAME), GameMode.SURVIVAL);
        fixture.audiences.refreshViewer(VIEWER_ID);

        fixture.audiences.register(VIEWER_ID, new EntityHandle("new-viewer"), GameMode.SURVIVAL);
        fixture.scheduler.runAll();

        assertTrue(fixture.refreshed.isEmpty());
    }

    @Test
    void queuedRefreshUsesTheLatestTargetGameMode() {
        Fixture fixture = new Fixture();
        fixture.audiences.register(VIEWER_ID, new EntityHandle(VIEWER_NAME), GameMode.SURVIVAL);
        fixture.audiences.register(TARGET_ID, new EntityHandle(TARGET_NAME), GameMode.SURVIVAL);
        fixture.audiences.refreshViewer(VIEWER_ID);

        fixture.audiences.updateGameMode(TARGET_ID, GameMode.SPECTATOR);
        fixture.scheduler.runAll();

        assertTrue(fixture.refreshed.contains("viewer->target:SPECTATOR"));
    }

    @Test
    void removedTargetIsSkippedByAlreadyQueuedRefreshes() {
        Fixture fixture = new Fixture();
        fixture.audiences.register(VIEWER_ID, new EntityHandle(VIEWER_NAME), GameMode.SURVIVAL);
        fixture.audiences.register(TARGET_ID, new EntityHandle(TARGET_NAME), GameMode.SURVIVAL);
        fixture.audiences.refreshTarget(TARGET_ID);

        fixture.audiences.remove(TARGET_ID);
        fixture.scheduler.runAll();

        assertTrue(fixture.refreshed.isEmpty());
    }

    @Test
    void incrementalRecoveryEventuallyRefreshesEveryPair() {
        Fixture fixture = new Fixture();
        fixture.audiences.register(VIEWER_ID, new EntityHandle(VIEWER_NAME), GameMode.SURVIVAL);
        fixture.audiences.refreshViewer(VIEWER_ID);
        fixture.audiences.refreshTarget(VIEWER_ID);

        fixture.audiences.register(TARGET_ID, new EntityHandle(TARGET_NAME), GameMode.SURVIVAL);
        fixture.audiences.refreshViewer(TARGET_ID);
        fixture.audiences.refreshTarget(TARGET_ID);
        fixture.scheduler.runAll();

        assertEquals(Set.of(
                "viewer->viewer:SURVIVAL",
                "viewer->target:SURVIVAL",
                "target->viewer:SURVIVAL",
                "target->target:SURVIVAL"
        ), Set.copyOf(fixture.refreshed));
    }

    @Test
    void ownerBroadcastSchedulesEachCurrentSession() {
        Fixture fixture = new Fixture();
        EntityHandle viewer = new EntityHandle(VIEWER_NAME);
        EntityHandle target = new EntityHandle(TARGET_NAME);
        List<EntityHandle> callbacks = new ArrayList<>();
        fixture.audiences.register(VIEWER_ID, viewer, GameMode.SURVIVAL);
        fixture.audiences.register(TARGET_ID, target, GameMode.SURVIVAL);

        fixture.audiences.forEachOwner(callbacks::add);
        assertTrue(callbacks.isEmpty());

        fixture.scheduler.runAll();
        assertEquals(Set.of(viewer, target), Set.copyOf(callbacks));
    }

    @Test
    void ownerCallbackIsFencedAgainstReconnect() {
        Fixture fixture = new Fixture();
        EntityHandle oldViewer = new EntityHandle("old-viewer");
        EntityHandle newViewer = new EntityHandle("new-viewer");
        List<EntityHandle> callbacks = new ArrayList<>();
        fixture.audiences.register(VIEWER_ID, oldViewer, GameMode.SURVIVAL);

        assertTrue(fixture.audiences.onOwner(VIEWER_ID, callbacks::add));
        fixture.audiences.register(VIEWER_ID, newViewer, GameMode.SURVIVAL);
        fixture.scheduler.runAll();
        assertTrue(callbacks.isEmpty());

        assertTrue(fixture.audiences.onOwner(VIEWER_ID, callbacks::add));
        fixture.scheduler.runAll();
        assertEquals(List.of(newViewer), callbacks);

        fixture.audiences.remove(VIEWER_ID);
        assertFalse(fixture.audiences.onOwner(VIEWER_ID, callbacks::add));
    }

    @Test
    void staleOwnerCallbackRunsRetirementCleanup() {
        Fixture fixture = new Fixture();
        AtomicInteger retired = new AtomicInteger();
        List<EntityHandle> callbacks = new ArrayList<>();
        fixture.audiences.register(VIEWER_ID, new EntityHandle("old-viewer"), GameMode.SURVIVAL);

        assertTrue(fixture.audiences.onOwner(VIEWER_ID, callbacks::add, retired::incrementAndGet));
        fixture.audiences.register(VIEWER_ID, new EntityHandle("new-viewer"), GameMode.SURVIVAL);
        fixture.scheduler.runAll();

        assertTrue(callbacks.isEmpty());
        assertEquals(1, retired.get());
    }

    @Test
    void rejectedOwnerScheduleRunsRetirementCleanup() {
        Fixture fixture = new Fixture();
        AtomicInteger retired = new AtomicInteger();
        fixture.audiences.register(VIEWER_ID, new EntityHandle(VIEWER_NAME), GameMode.SURVIVAL);
        fixture.scheduler.rejectNext();

        assertFalse(fixture.audiences.onOwner(VIEWER_ID, ignored -> {
        }, retired::incrementAndGet));
        assertEquals(1, retired.get());
    }

    @Test
    void playerIdSnapshotTracksCurrentSessions() {
        Fixture fixture = new Fixture();
        fixture.audiences.register(VIEWER_ID, new EntityHandle(VIEWER_NAME), GameMode.SURVIVAL);
        List<UUID> snapshot = fixture.audiences.playerIds();
        fixture.audiences.register(TARGET_ID, new EntityHandle(TARGET_NAME), GameMode.SURVIVAL);

        assertEquals(List.of(VIEWER_ID), snapshot);
        assertEquals(Set.of(VIEWER_ID, TARGET_ID), Set.copyOf(fixture.audiences.playerIds()));
    }

    private static final class Fixture {
        private final QueuedScheduler scheduler = new QueuedScheduler();
        private final List<String> refreshed = new ArrayList<>();
        private final VanishAudienceCoordinator<EntityHandle> audiences = new VanishAudienceCoordinator<>(
                scheduler,
                (viewer, target) -> refreshed.add(
                        viewer.owner().name() + "->" + target.owner().name() + ':' + target.gameMode()
                )
        );
    }

    private static final class QueuedScheduler
            implements VanishAudienceCoordinator.OwningScheduler<EntityHandle> {
        private final List<Scheduled> pending = new ArrayList<>();
        private boolean rejectNext;

        @Override
        public boolean execute(EntityHandle owner, Runnable operation) {
            if (rejectNext) {
                rejectNext = false;
                return false;
            }
            pending.add(new Scheduled(owner, operation));
            return true;
        }

        void rejectNext() {
            rejectNext = true;
        }

        List<EntityHandle> owners() {
            return pending.stream().map(Scheduled::owner).toList();
        }

        void runAll() {
            List<Scheduled> scheduled = List.copyOf(pending);
            pending.clear();
            scheduled.forEach(task -> task.operation().run());
        }
    }

    private record EntityHandle(String name) {
    }

    private record Scheduled(EntityHandle owner, Runnable operation) {
    }
}
