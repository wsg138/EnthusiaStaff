package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only access to private staff notes for authorized moderation surfaces. */
public interface StaffNoteStore {
    List<StaffNote> recent(UUID targetId, int limit);

    record StaffNote(
            UUID noteId,
            UUID targetId,
            UUID actorId,
            String noteText,
            Instant createdAt
    ) {
        public StaffNote {
            if (noteId == null || targetId == null || actorId == null || noteText == null
                    || noteText.isBlank() || createdAt == null) {
                throw new IllegalArgumentException("staff note fields must be present");
            }
        }
    }
}
