package net.enthusia.staff.domain.tester;

import java.time.Instant;
import java.util.UUID;

public record CheatTesterJournalRecord(
        UUID sessionId,
        String serverId,
        UUID staffId,
        UUID targetId,
        CheatTesterType testerType,
        String snapshot,
        String configuration,
        String evidence,
        CheatTesterSessionState state,
        String completionReason,
        Instant startedAt,
        Instant expiresAt,
        Instant completedAt,
        long revision
) {
    public CheatTesterJournalRecord {
        if (sessionId == null || serverId == null || serverId.isBlank() || staffId == null || targetId == null
                || testerType == null || snapshot == null || configuration == null || state == null
                || startedAt == null || expiresAt == null || revision < 0) {
            throw new IllegalArgumentException("tester journal record is incomplete");
        }
    }

    public boolean active() {
        return state == CheatTesterSessionState.ACTIVE;
    }
}
