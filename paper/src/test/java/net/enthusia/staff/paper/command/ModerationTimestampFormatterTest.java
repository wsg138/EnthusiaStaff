package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ModerationTimestampFormatterTest {
    private static final Instant SAMPLE = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void preservesOperatorTimestampLayout() {
        assertEquals(
                "2026-01-02 03:04:05 UTC",
                ModerationTimestampFormatter.inZone(ZoneId.of("UTC")).format(SAMPLE)
        );
        assertEquals(
                "2026-01-01 22:04:05 EST",
                ModerationTimestampFormatter.inZone(ZoneId.of("America/New_York")).format(SAMPLE)
        );
    }
}
