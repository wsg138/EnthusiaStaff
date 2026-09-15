package net.enthusia.staff.domain.investigation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Private subject-centric investigation case used by Discord moderation workflows. */
public record InvestigationCase(
        UUID caseId,
        ModerationSubjectId subjectId,
        Source source,
        Optional<UUID> punishmentId,
        Optional<String> legacyCaseId,
        String summary,
        State state,
        UUID openedBy,
        Instant openedAt,
        Instant lastActivityAt,
        Optional<Instant> closedAt,
        Optional<Instant> punishmentEndedAt,
        long sourceRevision,
        long revision,
        boolean replayed
) {
    public enum Source {
        DISCORD_PUNISHMENT,
        INVESTIGATION
    }

    public enum State {
        OPEN,
        CLOSED
    }

    public InvestigationCase {
        if (caseId == null || subjectId == null || source == null || punishmentId == null
                || legacyCaseId == null || blank(summary) || state == null || openedBy == null
                || openedAt == null || lastActivityAt == null || closedAt == null
                || punishmentEndedAt == null || sourceRevision < 0 || revision < 0) {
            throw new IllegalArgumentException("investigation case fields must be present and valid");
        }
        if (lastActivityAt.isBefore(openedAt)) {
            throw new IllegalArgumentException("case activity cannot predate opening");
        }
        validateSource(source, punishmentId);
        validateClosedState(state, closedAt, lastActivityAt);
        punishmentEndedAt.ifPresent(endedAt -> {
            if (endedAt.isBefore(openedAt)) {
                throw new IllegalArgumentException("punishment end cannot predate case opening");
            }
        });
        legacyCaseId.ifPresent(value -> {
            if (value.isBlank() || value.length() > 16) {
                throw new IllegalArgumentException("legacy case id must be nonblank and at most 16 characters");
            }
        });
    }

    private static void validateSource(Source source, Optional<UUID> punishmentId) {
        boolean hasPunishment = punishmentId.isPresent();
        if ((source == Source.DISCORD_PUNISHMENT) != hasPunishment) {
            throw new IllegalArgumentException("punishment cases require exactly one punishment id");
        }
    }

    private static void validateClosedState(State state, Optional<Instant> closedAt, Instant lastActivityAt) {
        if (state == State.OPEN && closedAt.isPresent()) {
            throw new IllegalArgumentException("open case cannot have a close time");
        }
        if (state == State.CLOSED && closedAt.isEmpty()) {
            throw new IllegalArgumentException("closed case requires a close time");
        }
        closedAt.ifPresent(value -> {
            if (value.isBefore(lastActivityAt)) {
                throw new IllegalArgumentException("case close time cannot predate last activity");
            }
        });
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
