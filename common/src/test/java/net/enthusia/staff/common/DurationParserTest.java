package net.enthusia.staff.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationParserTest {
    private final DurationParser parser = new DurationParser();

    @Test
    void parsesCombinedDurations() {
        assertEquals(Duration.ofDays(8).plusHours(6), parser.parse("1w1d6h").temporary().orElseThrow());
    }

    @Test
    void parsesPermanentAliases() {
        assertTrue(parser.parse("permanent").isPermanent());
        assertTrue(parser.parse("perm").isPermanent());
    }

    @Test
    void rejectsGapsZeroAndExcessiveValues() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("1d 2h"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("0h"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("101y"));
    }
}
