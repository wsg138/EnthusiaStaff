package net.enthusia.staff.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChecksTest {
    @Test
    void nonBlankTrimsAndReturnsTheNormalizedValue() {
        assertEquals("value", Checks.nonBlank("  value  ", "field", 5));
    }

    @Test
    void nonBlankAcceptsTheExactMaximumLengthAfterTrimming() {
        assertEquals("12345", Checks.nonBlank(" 12345 ", "field", 5));
    }

    @Test
    void nonBlankRejectsNullEmptyAndWhitespaceOnlyValues() {
        assertThrows(IllegalArgumentException.class, () -> Checks.nonBlank(null, "field", 5));
        assertThrows(IllegalArgumentException.class, () -> Checks.nonBlank("", "field", 5));
        assertThrows(IllegalArgumentException.class, () -> Checks.nonBlank("   ", "field", 5));
    }

    @Test
    void nonBlankRejectsValuesAboveTheMaximumAfterTrimming() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Checks.nonBlank(" 123456 ", "field", 5)
        );
        assertEquals("field exceeds 5 characters", exception.getMessage());
    }

    @Test
    void nonEmptyReturnsTheOriginalCollectionInstance() {
        List<String> values = new ArrayList<>(List.of("value"));
        assertSame(values, Checks.nonEmpty(values, "values"));
    }

    @Test
    void nonEmptyRejectsNullAndEmptyCollections() {
        assertThrows(IllegalArgumentException.class, () -> Checks.nonEmpty(null, "values"));
        assertThrows(IllegalArgumentException.class, () -> Checks.nonEmpty(List.of(), "values"));
    }
}
