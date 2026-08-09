package net.enthusia.staff.paper.tester;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks synthetic entity ownership and target-only interaction evidence. */
final class CheatTesterFakeEntityState {
    private final Clock clock;
    private final Map<UUID, CheatTesterSession> activeByTarget;
    private final Map<Integer, UUID> targets = new ConcurrentHashMap<>();

    CheatTesterFakeEntityState(Clock clock, Map<UUID, CheatTesterSession> activeByTarget) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.activeByTarget = java.util.Objects.requireNonNull(activeByTarget, "activeByTarget");
    }

    void track(FakeEntityAdapter.Handle handle, UUID targetId) {
        targets.put(handle.entityId(), targetId);
    }

    void remove(CheatTesterSession session) {
        if (session.fakeHandle != null) {
            targets.remove(session.fakeHandle.entityId());
        }
        targets.entrySet().removeIf(entry -> entry.getValue().equals(session.targetId));
    }

    boolean recordInteraction(UUID viewerId, int entityId, String action) {
        UUID targetId = targets.get(entityId);
        if (targetId == null) {
            return false;
        }
        CheatTesterSession session = activeByTarget.get(targetId);
        if (session == null || session.fakeHandle == null || session.fakeHandle.entityId() != entityId) {
            return true;
        }
        if (viewerId.equals(session.staffId) || !viewerId.equals(session.targetId)) {
            return true;
        }
        session.fakeInteractions.incrementAndGet();
        if ("ATTACK".equals(action)) {
            session.fakeAttacks.incrementAndGet();
        }
        session.firstInteractionMillis.compareAndSet(
                -1L,
                Math.max(0L, clock.instant().toEpochMilli() - session.startedAt.toEpochMilli())
        );
        return true;
    }

    void clear() {
        targets.clear();
    }
}
