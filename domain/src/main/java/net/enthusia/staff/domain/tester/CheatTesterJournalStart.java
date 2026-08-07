package net.enthusia.staff.domain.tester;

import java.time.Instant;
import java.util.UUID;

public record CheatTesterJournalStart(
        UUID sessionId,
        String serverId,
        UUID staffId,
        UUID targetId,
        CheatTesterType testerType,
        String snapshot,
        String configuration,
        Instant startedAt,
        Instant expiresAt
) {
    private static final int MAX_SERVER_ID = 64;
    private static final int MAX_SNAPSHOT = 8 * 1024 * 1024;
    private static final int MAX_CONFIGURATION = 16 * 1024;

    public CheatTesterJournalStart {
        if (sessionId == null || staffId == null || targetId == null || testerType == null
                || startedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("tester journal identifiers, type, and times must be present");
        }
        serverId = bounded(serverId, MAX_SERVER_ID, "serverId");
        snapshot = bounded(snapshot, MAX_SNAPSHOT, "snapshot");
        configuration = bounded(configuration, MAX_CONFIGURATION, "configuration");
        if (!expiresAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("tester expiration must be after start");
        }
    }

    private static String bounded(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " must be present and within its safety limit");
        }
        return value;
    }
}
