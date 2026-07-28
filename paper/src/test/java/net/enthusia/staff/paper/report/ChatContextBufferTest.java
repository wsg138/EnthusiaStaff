package net.enthusia.staff.paper.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ChatContextBufferTest {
    @Test
    void privateSnapshotIncludesOnlyTheReportParticipants() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ChatContextBuffer buffer = new ChatContextBuffer(Clock.fixed(now, ZoneOffset.UTC));
        UUID reporter = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();

        buffer.capturePrivate(reporter, "Reporter", target, "Target", "first");
        buffer.capturePrivate(target, "Target", reporter, "Reporter", "second");
        buffer.capturePrivate(reporter, "Reporter", unrelated, "Other", "excluded");

        var snapshot = buffer.privateSnapshot(reporter, target, now);
        assertEquals(2, snapshot.size());
        assertTrue(snapshot.stream().noneMatch(message -> message.body().equals("excluded")));
    }

    @Test
    void privateCaptureRejectsMissingCallbackFields() {
        ChatContextBuffer buffer = new ChatContextBuffer(Clock.systemUTC());

        assertThrows(IllegalArgumentException.class, () ->
                buffer.capturePrivate(UUID.randomUUID(), "Sender", UUID.randomUUID(), "Target", null));
    }
}
