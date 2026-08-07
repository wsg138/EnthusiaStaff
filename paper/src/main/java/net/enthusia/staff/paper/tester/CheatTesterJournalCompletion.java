package net.enthusia.staff.paper.tester;

import java.time.Clock;
import java.util.Optional;
import net.enthusia.staff.domain.ports.CheatTesterJournalStore;
import net.enthusia.staff.domain.tester.CheatTesterJournalRecord;
import net.enthusia.staff.domain.tester.CheatTesterSessionState;

/** Optimistic durable completion with one authoritative revision refresh. */
final class CheatTesterJournalCompletion {
    private final Clock clock;

    CheatTesterJournalCompletion(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    boolean complete(
            CheatTesterJournalStore store,
            CheatTesterSession session,
            CheatTesterSessionState terminalState,
            String reason,
            String evidence
    ) {
        String bounded = CheatTesterEvidence.boundedReason(reason);
        if (store.complete(session.sessionId, session.revision, terminalState, bounded, evidence, clock.instant())) {
            return true;
        }
        Optional<CheatTesterJournalRecord> current = store.activeForTarget(session.targetId);
        if (current.isEmpty() || !current.orElseThrow().sessionId().equals(session.sessionId)) {
            return false;
        }
        session.revision = current.orElseThrow().revision();
        return store.complete(session.sessionId, session.revision, terminalState, bounded, evidence, clock.instant());
    }
}
