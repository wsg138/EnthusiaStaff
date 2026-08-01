package net.enthusia.staff.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void parsesEverySupportedUnit() {
        assertEquals(Duration.ofSeconds(1), parser.parse("1s").temporary().orElseThrow());
        assertEquals(Duration.ofMinutes(1), parser.parse("1m").temporary().orElseThrow());
        assertEquals(Duration.ofHours(1), parser.parse("1h").temporary().orElseThrow());
        assertEquals(Duration.ofDays(1), parser.parse("1d").temporary().orElseThrow());
        assertEquals(Duration.ofDays(7), parser.parse("1w").temporary().orElseThrow());
    }

    @Test
    void trimsInputAndIgnoresUnitAndPermanentAliasCase() {
        assertEquals(Duration.ofDays(8).plusHours(6), parser.parse("  1W1D6H  ").temporary().orElseThrow());
        assertTrue(parser.parse(" PERMANENT ").isPermanent());
        assertTrue(parser.parse(" PeRm ").isPermanent());
    }

    @Test
    void supportsRepeatedAndOutOfOrderTokensWithoutLosingTime() {
        assertEquals(Duration.ofHours(2), parser.parse("1h30m30m").temporary().orElseThrow());
        assertEquals(Duration.ofHours(1).plusMinutes(1), parser.parse("1m1h").temporary().orElseThrow());
    }

    @Test
    void acceptsTheExactHundredYearSafetyLimit() {
        assertEquals(Duration.ofDays(36_500), parser.parse("36500d").temporary().orElseThrow());
    }

    @Test
    void rejectsValuesAboveTheHundredYearSafetyLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("36500d1s")
        );
        assertEquals("duration exceeds the 100-year safety limit", exception.getMessage());
    }

    @Test
    void parsesPermanentAliases() {
        assertTrue(parser.parse("permanent").isPermanent());
        assertTrue(parser.parse("perm").isPermanent());
        assertFalse(parser.parse("1s").isPermanent());
    }

    @Test
    void rejectsMalformedZeroUnsupportedAndOverflowingValues() {
        for (String invalid : new String[]{
                "1d 2h",
                "0h",
                "1y",
                "1.5h",
                "-1h",
                "1h+",
                "999999999999999999999999999999s"
        }) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(invalid), invalid);
        }
    }

    @Test
    void rejectsMissingBlankAndOverlongInput() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("1".repeat(65)));
    }
}
