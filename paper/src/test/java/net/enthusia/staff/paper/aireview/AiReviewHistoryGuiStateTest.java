package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.paper.aireview.AiReviewModels.DecisionHistoryItem;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageAction;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import org.junit.jupiter.api.Test;

class AiReviewHistoryGuiStateTest {
    private static DecisionHistoryItem record(String id) {
        return new DecisionHistoryItem(
                id, Instant.parse("2026-10-09T10:00:00Z"),
                Instant.parse("2026-10-09T10:00:01Z"),
                "minecraft", "minecraft_public", "INGESTED",
                MessageAction.ALLOW, "SAFE", ReviewPriority.NONE,
                List.of("safe"), "m1", "v1", false, false
        );
    }

    @Test
    void historyIsReadOnlyImmutableAndSupportsCursorStack() {
        UUID viewerId = UUID.randomUUID();
        var entries = new ArrayList<>(List.of(record("event-1")));
        var prior = new ArrayList<>(List.of("", "older"));
        var page = new AiReviewGuiState.History(
                viewerId, 2L, entries, "current", "next", prior
        );
        entries.clear();
        prior.clear();

        assertEquals(1, page.items().size());
        assertEquals(List.of("", "older"), page.previousCursors());
        assertEquals("current", page.cursor());
        assertEquals("next", page.nextCursor());
        assertEquals(2L, page.generation());
        assertThrows(UnsupportedOperationException.class,
                () -> page.items().add(record("other")));
        assertThrows(UnsupportedOperationException.class,
                () -> page.previousCursors().add("new"));
    }

    @Test
    void historyRejectsInvalidOrUnboundedPageTokens() {
        UUID viewerId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new AiReviewGuiState.History(
                        viewerId, 1L, List.of(), "", null, List.of()
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new AiReviewGuiState.History(
                        viewerId, 1L, List.of(), null, "x".repeat(65), List.of()
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new AiReviewGuiState.History(
                        viewerId, 1L, List.of(), null, null,
                        java.util.Collections.nCopies(51, "cursor")
                ));
        assertTrue(new AiReviewGuiState.History(
                viewerId, 1L, List.of(), null, null, List.of()
        ).items().isEmpty());
    }
}
