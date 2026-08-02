package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

final class SanctionExpirationParserTest {
    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
    private final SanctionExpirationParser parser = new SanctionExpirationParser(
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void acceptsAbsoluteIsoTimestamp() {
        assertEquals(
                Instant.parse("2026-08-10T14:30:00Z"),
                parser.parse("2026-08-10T14:30:00Z").orElseThrow()
        );
    }

    @Test
    void acceptsCompactDurations() {
        assertEquals(NOW.plusSeconds(30), parser.parse("30s").orElseThrow());
        assertEquals(NOW.plusSeconds(2 * 60), parser.parse("2m").orElseThrow());
        assertEquals(NOW.plusSeconds(3 * 60 * 60), parser.parse("3H").orElseThrow());
        assertEquals(NOW.plusSeconds(4 * 24 * 60 * 60), parser.parse("4d").orElseThrow());
        assertEquals(NOW.plusSeconds(14 * 24 * 60 * 60), parser.parse("2w").orElseThrow());
    }

    @Test
    void rejectsBlankMalformedZeroNegativeAndOverflowingValues() {
        for (String value : new String[]{"", "0m", "-1h", "later", "1month", "999999999w"}) {
            assertTrue(parser.parse(value).isEmpty(), value);
        }
        assertTrue(parser.parse(null).isEmpty());
    }
}
