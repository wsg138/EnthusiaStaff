package net.enthusia.staff.domain.investigation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Private D09 lifecycle metadata attached to an authoritative moderation case. */
public record InvestigationCase(
        CaseId caseId,
        ModerationSubjectId subjectId,
        Source source,
        Optional<UUID> punishmentId,
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
                || blank(summary) || state == null || openedBy == null || openedAt == null
                || lastActivityAt == null || closedAt == null || punishmentEndedAt == null
                || sourceRevision < 0 || revision < 0) {
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
    }

    private static void validateSource(Source source, Optional<UUID> punishmentId) {
        if ((source == Source.DISCORD_PUNISHMENT) != punishmentId.isPresent()) {
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
