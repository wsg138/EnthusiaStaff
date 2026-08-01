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
    private static final int CONTEXT_LIMIT = 2_000;
    private static final String REPORTER_NAME = "Reporter";
    private static final String TARGET_NAME = "Target";

    @Test
    void privateSnapshotIncludesOnlyTheReportParticipants() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ChatContextBuffer buffer = new ChatContextBuffer(Clock.fixed(now, ZoneOffset.UTC));
        UUID reporter = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();

        buffer.capturePrivate(reporter, REPORTER_NAME, target, TARGET_NAME, "first");
        buffer.capturePrivate(target, TARGET_NAME, reporter, REPORTER_NAME, "second");
        buffer.capturePrivate(reporter, REPORTER_NAME, unrelated, "Other", "excluded");

        var snapshot = buffer.privateSnapshot(reporter, target, now);
        assertEquals(2, snapshot.size());
        assertTrue(snapshot.stream().noneMatch(message -> message.body().equals("excluded")));
    }

    @Test
    void privateCaptureRejectsMissingCallbackFields() {
        ChatContextBuffer buffer = new ChatContextBuffer(Clock.systemUTC());

        assertThrows(IllegalArgumentException.class, () ->
                buffer.capturePrivate(UUID.randomUUID(), "Sender", UUID.randomUUID(), TARGET_NAME, null));
    }

    @Test
    void snapshotsKeepTheMostRecentBoundedContext() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ChatContextBuffer buffer = new ChatContextBuffer(Clock.fixed(now, ZoneOffset.UTC));
        UUID reporter = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        for (int index = 0; index <= CONTEXT_LIMIT; index++) {
            buffer.capturePublic(reporter, REPORTER_NAME, "public-" + index);
            buffer.capturePrivate(reporter, REPORTER_NAME, target, TARGET_NAME, "private-" + index);
        }

        var publicSnapshot = buffer.snapshot(now);
        var privateSnapshot = buffer.privateSnapshot(reporter, target, now);
        assertEquals(CONTEXT_LIMIT, publicSnapshot.size());
        assertEquals("public-1", publicSnapshot.getFirst().body());
        assertEquals("public-" + CONTEXT_LIMIT, publicSnapshot.getLast().body());
        assertEquals(CONTEXT_LIMIT, privateSnapshot.size());
        assertEquals("private-1", privateSnapshot.getFirst().body());
        assertEquals("private-" + CONTEXT_LIMIT, privateSnapshot.getLast().body());
    }
}
