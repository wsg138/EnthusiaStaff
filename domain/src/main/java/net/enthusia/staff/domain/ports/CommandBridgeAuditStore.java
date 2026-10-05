package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.commandbridge.CommandBridgeOutcome;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Durable request claim and terminal audit boundary for Discord console execution. */
public interface CommandBridgeAuditStore {
    Claim claim(RequestAudit request);

    void complete(UUID requestId, CommandBridgeOutcome outcome, Instant completedAt);

    record RequestAudit(
            UUID requestId,
            ModerationSubjectId subjectId,
            DiscordUserId discordUserId,
            UUID actorPlayerId,
            String targetServer,
            String commandName,
            String requestFingerprint,
            Instant requestedAt
    ) {
        public RequestAudit {
            if (requestId == null || subjectId == null || discordUserId == null || actorPlayerId == null
                    || targetServer == null || targetServer.isBlank() || commandName == null || commandName.isBlank()
                    || requestFingerprint == null || requestFingerprint.length() != 64 || requestedAt == null) {
                throw new IllegalArgumentException("command bridge audit request is invalid");
            }
        }
    }

    record Claim(Status status, Optional<CommandBridgeOutcome> terminalOutcome) {
        public Claim {
            if (status == null || terminalOutcome == null) {
                throw new IllegalArgumentException("command bridge audit claim is invalid");
            }
            if ((status == Status.TERMINAL_REPLAY) != terminalOutcome.isPresent()) {
                throw new IllegalArgumentException("terminal replay must carry exactly one terminal outcome");
            }
        }

        public static Claim claimed() {
            return new Claim(Status.CLAIMED, Optional.empty());
        }

        public static Claim terminal(CommandBridgeOutcome outcome) {
            return new Claim(Status.TERMINAL_REPLAY, Optional.of(outcome));
        }

        public static Claim unresolved() {
            return new Claim(Status.UNRESOLVED_PRIOR_REQUEST, Optional.empty());
        }

        public static Claim conflict() {
            return new Claim(Status.REQUEST_ID_CONFLICT, Optional.empty());
        }
    }

    enum Status {
        CLAIMED,
        TERMINAL_REPLAY,
        UNRESOLVED_PRIOR_REQUEST,
        REQUEST_ID_CONFLICT
    }
}
