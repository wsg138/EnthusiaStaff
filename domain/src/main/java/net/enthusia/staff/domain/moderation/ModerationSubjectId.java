package net.enthusia.staff.domain.moderation;

import java.util.UUID;

public record ModerationSubjectId(UUID value) {
    public ModerationSubjectId {
        if (value == null) {
            throw new IllegalArgumentException("moderationSubjectId must be present");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
