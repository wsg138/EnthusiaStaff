package net.enthusia.staff.domain.commandbridge;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Authenticated command request after the Discord actor has been resolved through canonical linking. */
public record CommandBridgeRequest(
        UUID requestId,
        ModerationSubjectId subjectId,
        UUID actorPlayerId,
        String targetServer,
        String command,
        Instant requestedAt
) {
    private static final int MAX_TARGET_LENGTH = 64;
    private static final int MAX_COMMAND_LENGTH = 512;

    public CommandBridgeRequest {
        if (requestId == null || subjectId == null || actorPlayerId == null || requestedAt == null) {
            throw new IllegalArgumentException("command bridge identity fields are required");
        }
        targetServer = bounded(targetServer, "targetServer", MAX_TARGET_LENGTH);
        command = bounded(command, "command", MAX_COMMAND_LENGTH);
    }

    private static String bounded(String value, String field, int maximum) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
