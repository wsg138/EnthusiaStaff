package net.enthusia.staff.domain.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeBaseAuditEventTest {
    @Test
    void eventContainsOnlyCoordinateFreeLifecycleMetadata() {
        UUID operationId = UUID.randomUUID();
        FakeBaseAuditEvent event = new FakeBaseAuditEvent(
                UUID.randomUUID(),
                operationId,
                "survival-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                FakeBaseAuditAction.CREATED,
                "COMMITTED",
                "VIRTUAL_RENDERED",
                Instant.parse("2026-08-07T20:00:00Z")
        );

        assertEquals(operationId, event.operationId());
        assertEquals("survival-1", event.serverId());
        assertEquals("VIRTUAL_RENDERED", event.reasonCode());
    }

    @Test
    void rejectsUnboundedAuditText() {
        assertThrows(IllegalArgumentException.class, () -> new FakeBaseAuditEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "x".repeat(65),
                UUID.randomUUID(),
                UUID.randomUUID(),
                FakeBaseAuditAction.CREATED,
                "COMMITTED",
                "CREATE",
                Instant.now()
        ));
    }
}
