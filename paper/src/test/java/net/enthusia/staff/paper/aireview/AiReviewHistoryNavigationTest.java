package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiReviewHistoryNavigationTest {
    private static AiReviewGuiState.History history(
            String cursor, String nextCursor, List<String> previous
    ) {
        return new AiReviewGuiState.History(
                UUID.randomUUID(), 1L, List.of(), cursor, nextCursor, previous
        );
    }

    @Test
    void firstPageRoundTripKeepsPreviousCursorStack() {
        var first = history(null, "event-01", List.of());
        var second = AiReviewHistoryNavigation.next(first);
        assertEquals("event-01", second.cursor());
        assertEquals(List.of(""), second.previousCursors());

        var secondState = history(second.cursor(), "event-02", second.previousCursors());
        var previous = AiReviewHistoryNavigation.previous(secondState);
        assertNull(previous.cursor());
        assertEquals(List.of(), previous.previousCursors());
    }

    @Test
    void laterPageRoundTripPreservesOpaqueCursor() {
        var page = history("event-02", "event-03", List.of("", "event-01"));
        var next = AiReviewHistoryNavigation.next(page);
        assertEquals("event-03", next.cursor());
        assertEquals(List.of("", "event-01", "event-02"), next.previousCursors());
        var previous = AiReviewHistoryNavigation.previous(
                history(next.cursor(), null, next.previousCursors())
        );
        assertEquals("event-02", previous.cursor());
        assertEquals(List.of("", "event-01"), previous.previousCursors());
    }

    @Test
    void missingPagesAndOversizedStackCannotAdvance() {
        assertThrows(IllegalStateException.class,
                () -> AiReviewHistoryNavigation.previous(history(null, null, List.of())));
        assertThrows(IllegalStateException.class,
                () -> AiReviewHistoryNavigation.next(history(null, null, List.of())));
        assertThrows(IllegalStateException.class,
                () -> AiReviewHistoryNavigation.next(history(
                        "current", "next",
                        java.util.Collections.nCopies(AiReviewHistoryNavigation.MAX_PREVIOUS_PAGES, "prior")
                )));
    }
}
