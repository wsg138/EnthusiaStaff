package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.enthusia.staff.domain.tester.CheatTesterType;
import org.junit.jupiter.api.Test;

class CheatTesterFakeEntityStateTest {
    private static final Instant STARTED = Instant.parse("2026-08-07T12:00:00Z");

    @Test
    void onlyTargetInteractionsContributeEvidence() {
        UUID staffId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID observerId = UUID.randomUUID();
        Map<UUID, CheatTesterSession> sessions = new ConcurrentHashMap<>();
        CheatTesterSession session = session(staffId, targetId, 42);
        sessions.put(targetId, session);
        CheatTesterFakeEntityState state = new CheatTesterFakeEntityState(
                Clock.fixed(STARTED.plusMillis(750), ZoneOffset.UTC),
                sessions
        );
        state.track(session.fakeHandle, targetId);

        assertTrue(state.recordInteraction(staffId, 42, "ATTACK"));
        assertTrue(state.recordInteraction(observerId, 42, "ATTACK"));
        assertEquals(0, session.fakeInteractions.get());
        assertEquals(0, session.fakeAttacks.get());

        assertTrue(state.recordInteraction(targetId, 42, "INTERACT"));
        assertEquals(1, session.fakeInteractions.get());
        assertEquals(0, session.fakeAttacks.get());
        assertEquals(750L, session.firstInteractionMillis.get());

        assertTrue(state.recordInteraction(targetId, 42, "ATTACK"));
        assertEquals(2, session.fakeInteractions.get());
        assertEquals(1, session.fakeAttacks.get());
        assertEquals(750L, session.firstInteractionMillis.get());
    }

    @Test
    void staleTrackedEntityStillCancelsUntilCleanup() {
        UUID staffId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Map<UUID, CheatTesterSession> sessions = new ConcurrentHashMap<>();
        CheatTesterSession session = session(staffId, targetId, 77);
        sessions.put(targetId, session);
        CheatTesterFakeEntityState state = new CheatTesterFakeEntityState(
                Clock.fixed(STARTED, ZoneOffset.UTC),
                sessions
        );

        assertFalse(state.recordInteraction(targetId, 77, "ATTACK"));
        state.track(session.fakeHandle, targetId);
        sessions.remove(targetId);
        assertTrue(state.recordInteraction(targetId, 77, "ATTACK"));

        state.remove(session);
        assertFalse(state.recordInteraction(targetId, 77, "ATTACK"));
        state.track(session.fakeHandle, targetId);
        state.clear();
        assertFalse(state.recordInteraction(targetId, 77, "ATTACK"));
    }

    private static CheatTesterSession session(UUID staffId, UUID targetId, int entityId) {
        CheatTesterSession session = new CheatTesterSession(
                UUID.randomUUID(),
                staffId,
                targetId,
                CheatTesterType.FAKE_ENTITY,
                false,
                STARTED
        );
        session.fakeHandle = new FakeEntityAdapter.Handle(entityId, UUID.randomUUID());
        return session;
    }
}
