package net.enthusia.staff.domain.investigation;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Private staff note with explicit subject/scope and immutable version history. */
public record InvestigationNote(
        UUID noteId,
        ModerationSubjectId subjectId,
        Scope scope,
        Visibility visibility,
        String text,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        long revision,
        boolean replayed
) {
    public static final int MAX_TEXT_LENGTH = 8_000;

    public enum ScopeType {
        SUBJECT,
        DISCORD_USER,
        MINECRAFT_PLAYER,
        CASE
    }

    public enum Visibility {
        STAFF,
        MANAGEMENT
    }

    public record Scope(ScopeType type, String value) {
        public Scope {
            if (type == null || blank(value) || value.length() > 160) {
                throw new IllegalArgumentException("note scope must be present and bounded");
            }
        }
    }

    public record Version(long revision, String text, UUID changedBy, Instant changedAt) {
        public Version {
            validateText(text);
            if (revision < 0 || changedBy == null || changedAt == null) {
                throw new IllegalArgumentException("note version fields must be present and valid");
            }
        }
    }

    public InvestigationNote {
        if (noteId == null || subjectId == null || scope == null || visibility == null
                || createdBy == null || createdAt == null || updatedBy == null || updatedAt == null
                || revision < 0 || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("investigation note fields must be present and valid");
        }
        validateText(text);
    }

    public static void validateText(String value) {
        if (blank(value) || value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("note text must be nonblank and at most 8000 characters");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
