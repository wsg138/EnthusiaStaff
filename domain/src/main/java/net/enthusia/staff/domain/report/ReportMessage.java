package net.enthusia.staff.domain.report;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An attributed report message. Legacy rows intentionally remain unverified. */
public record ReportMessage(UUID messageId, UUID actorId, String body,
                            Instant createdAt, Origin origin) {
    public enum Origin { UNKNOWN, REPORTER_FOLLOWUP, STAFF_ACTION }

    public ReportMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(origin, "origin");
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Report message body must be present");
        }
    }
}
