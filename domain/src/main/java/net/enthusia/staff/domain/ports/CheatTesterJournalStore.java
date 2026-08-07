package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.tester.CheatTesterJournalRecord;
import net.enthusia.staff.domain.tester.CheatTesterJournalStart;
import net.enthusia.staff.domain.tester.CheatTesterSessionState;

public interface CheatTesterJournalStore {
    CheatTesterJournalRecord start(CheatTesterJournalStart start);

    Optional<CheatTesterJournalRecord> activeForTarget(String serverId, UUID targetId);

    List<CheatTesterJournalRecord> activeForServer(String serverId, int limit);

    Optional<CheatTesterJournalRecord> checkpointEvidence(
            UUID sessionId,
            long expectedRevision,
            String evidence,
            Instant now
    );

    boolean complete(
            UUID sessionId,
            long expectedRevision,
            CheatTesterSessionState terminalState,
            String reason,
            String evidence,
            Instant now
    );
}
