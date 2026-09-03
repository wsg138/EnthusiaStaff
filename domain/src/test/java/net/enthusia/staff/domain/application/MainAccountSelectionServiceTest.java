package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MainAccountSelectionServiceTest {
    @Test
    void requiresAtLeastTwentyFivePercentMoreActivePlaytime() {
        assertFalse(MainAccountSelectionService.shouldSwitch(100, 124));
        assertTrue(MainAccountSelectionService.shouldSwitch(100, 125));
        assertTrue(MainAccountSelectionService.shouldSwitch(100, 126));
    }

    @Test
    void arithmeticDoesNotOverflowAtLargeValues() {
        assertTrue(MainAccountSelectionService.shouldSwitch(
                4_000_000_000_000_000_000L,
                5_000_000_000_000_000_000L
        ));
        assertFalse(MainAccountSelectionService.shouldSwitch(Long.MAX_VALUE - 1L, Long.MAX_VALUE));
    }
}
