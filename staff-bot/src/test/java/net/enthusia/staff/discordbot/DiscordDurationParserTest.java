package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DiscordDurationParserTest {
    private final DiscordDurationParser parser = new DiscordDurationParser();

    @Test
    void presetsAndPermanentAreDistinguishedFromCustomDurations() {
        var preset = parser.parse(" 1h ", true);
        var custom = parser.parse("90m", true);
        var permanent = parser.parse("PERM", true);

        assertEquals(Duration.ofHours(1), preset.length().temporary().orElseThrow());
        assertFalse(preset.custom());
        assertEquals(Duration.ofMinutes(90), custom.length().temporary().orElseThrow());
        assertTrue(custom.custom());
        assertTrue(permanent.length().isPermanent());
        assertFalse(permanent.custom());
    }

    @Test
    void acceptsLargeCustomSecondsThroughSafetyMaximum() {
        var millionSeconds = parser.parse("1000000s", true);
        var maximumSeconds = parser.parse("315360000s", true);

        assertEquals(Duration.ofSeconds(1_000_000), millionSeconds.length().temporary().orElseThrow());
        assertTrue(millionSeconds.custom());
        assertEquals(Duration.ofDays(3650), maximumSeconds.length().temporary().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> parser.parse("315360001s", true));
    }

    @Test
    void rejectsMalformedDisallowedAndExcessiveDurations() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("permanent", false));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("0m", true));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("100000w", true));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("1 month", true));
    }
}
