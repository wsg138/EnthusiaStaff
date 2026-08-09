package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.ports.CheatTesterJournalStore;
import net.enthusia.staff.domain.tester.CheatTesterJournalRecord;
import net.enthusia.staff.domain.tester.CheatTesterJournalStart;
import net.enthusia.staff.domain.tester.CheatTesterSessionState;
import net.enthusia.staff.domain.tester.CheatTesterType;
import org.junit.jupiter.api.Test;

class CheatTesterJournalCompletionTest {
    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");

    @Test
    void completesAtCurrentRevisionWithoutLookup() {
        CheatTesterSession session = session();
        FakeStore store = new FakeStore();
        store.completeResult = true;
        CheatTesterJournalCompletion completion = completion();

        assertTrue(completion.complete(store, session, CheatTesterSessionState.RESTORED, "done", "{}"));
        assertEquals(1, store.completeCalls);
        assertEquals(0, store.lookupCalls);
        assertEquals(0L, store.lastRevision);
        assertEquals(NOW, store.lastNow);
    }

    @Test
    void refreshesAuthoritativeRevisionOnce() {
        CheatTesterSession session = session();
        FakeStore store = new FakeStore();
        store.active = Optional.of(record(session.sessionId, session.targetId, 4L));
        store.succeedOnSecondComplete = true;

        assertTrue(completion().complete(store, session, CheatTesterSessionState.CANCELLED, "cancelled", "{}"));
        assertEquals(2, store.completeCalls);
        assertEquals(1, store.lookupCalls);
        assertEquals(4L, session.revision);
        assertEquals(4L, store.lastRevision);
    }

    @Test
    void refusesRevisionRefreshFromDifferentSession() {
        CheatTesterSession session = session();
        FakeStore store = new FakeStore();
        store.active = Optional.of(record(UUID.randomUUID(), session.targetId, 3L));

        assertFalse(completion().complete(store, session, CheatTesterSessionState.FAILED, "failed", "{}"));
        assertEquals(1, store.completeCalls);
        assertEquals(1, store.lookupCalls);
        assertEquals(0L, session.revision);
    }

    private static CheatTesterJournalCompletion completion() {
        return new CheatTesterJournalCompletion(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CheatTesterSession session() {
        return new CheatTesterSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                CheatTesterType.NO_FALL,
                false,
                NOW.minusSeconds(1)
        );
    }

    private static CheatTesterJournalRecord record(UUID sessionId, UUID targetId, long revision) {
        return new CheatTesterJournalRecord(
                sessionId,
                "paper-1",
                UUID.randomUUID(),
                targetId,
                CheatTesterType.NO_FALL,
                "{}",
                "{}",
                null,
                CheatTesterSessionState.ACTIVE,
                null,
                NOW.minusSeconds(2),
                NOW.plusSeconds(2),
                null,
                revision
        );
    }

    private static final class FakeStore implements CheatTesterJournalStore {
        private Optional<CheatTesterJournalRecord> active = Optional.empty();
        private boolean completeResult;
        private boolean succeedOnSecondComplete;
        private int completeCalls;
        private int lookupCalls;
        private long lastRevision = -1L;
        private Instant lastNow;

        @Override
        public CheatTesterJournalRecord start(CheatTesterJournalStart start) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CheatTesterJournalRecord> activeForTarget(UUID targetId) {
            lookupCalls++;
            return active;
        }

        @Override
        public Optional<CheatTesterJournalRecord> activeForTarget(String serverId, UUID targetId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CheatTesterJournalRecord> activeForServer(String serverId, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CheatTesterJournalRecord> checkpointEvidence(
                UUID sessionId,
                long expectedRevision,
                String evidence,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean complete(
                UUID sessionId,
                long expectedRevision,
                CheatTesterSessionState terminalState,
                String reason,
                String evidence,
                Instant now
        ) {
            completeCalls++;
            lastRevision = expectedRevision;
            lastNow = now;
            return completeResult || (succeedOnSecondComplete && completeCalls == 2);
        }
    }
}
