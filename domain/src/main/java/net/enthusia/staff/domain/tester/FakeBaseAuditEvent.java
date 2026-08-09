package net.enthusia.staff.domain.tester;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable fake-base audit data. Runtime coordinates are deliberately excluded. */
public record FakeBaseAuditEvent(
        UUID eventId,
        UUID operationId,
        String serverId,
        UUID staffId,
        UUID targetId,
        FakeBaseAuditAction action,
        String outcome,
        String reasonCode,
        Instant occurredAt
) {
    private static final int MAX_TEXT_LENGTH = 64;

    public FakeBaseAuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(operationId, "operationId");
        serverId = requireText(serverId, "serverId");
        Objects.requireNonNull(staffId, "staffId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(action, "action");
        outcome = requireText(outcome, "outcome");
        reasonCode = requireText(reasonCode, "reasonCode");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(name + " must be non-blank and at most " + MAX_TEXT_LENGTH + " characters");
        }
        return value;
    }
}
