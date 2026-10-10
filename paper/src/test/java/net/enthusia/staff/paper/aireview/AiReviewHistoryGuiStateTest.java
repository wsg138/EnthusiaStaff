package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.paper.aireview.AiReviewModels.DecisionHistoryItem;
import net.enthusia.staff.paper.aireview.AiReviewModels.Decision;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.Containment;
import net.enthusia.staff.paper.aireview.AiReviewModels.StrikeRecommendation;
import net.enthusia.staff.paper.aireview.AiReviewModels.SupportFlow;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageAction;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import org.junit.jupiter.api.Test;

class AiReviewHistoryGuiStateTest {
    private static final String OLDER_CURSOR = "older";
    private static final String NEXT_CURSOR = "next";
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
        var prior = new ArrayList<>(List.of("", OLDER_CURSOR));
        var page = new AiReviewGuiState.History(
                viewerId, 2L, entries, "current", NEXT_CURSOR, prior
        );
        entries.clear();
        prior.clear();

        assertEquals(1, page.items().size());
        assertEquals(List.of("", OLDER_CURSOR), page.previousCursors());
        assertEquals("current", page.cursor());
        assertEquals(NEXT_CURSOR, page.nextCursor());
        assertEquals(2L, page.generation());
        assertThrows(UnsupportedOperationException.class,
                () -> page.items().add(record("other")));
        assertThrows(UnsupportedOperationException.class,
                () -> page.previousCursors().add("new"));
    }

    @Test
    void historyOriginIsPreservedThroughDetailLabelAndConfirmationStates() {
        UUID viewerId = UUID.randomUUID();
        var history = new AiReviewGuiState.History(
                viewerId, 3L, List.of(record("event-1")), OLDER_CURSOR,
                NEXT_CURSOR, List.of(""), AiReviewHistoryFilter.CORRECTED
        );
        var decision = new Decision(
                MessageAction.ALLOW, "SAFE", ReviewPriority.NONE,
                StrikeRecommendation.NONE, Containment.NONE, null,
                SupportFlow.NONE, Map.of(), null, List.of(), List.of(),
                null, "m1", "v1"
        );
        var event = new EventDetails(
                "event-1", "staff-test", "minecraft", "minecraft_public",
                "smp", "global", "c", "external-1", "canonical-1", "player-1",
                Instant.parse("2026-10-09T10:00:00Z"), "hi", null,
                decision, null, List.of(), null, List.of()
        );
        var detail = new AiReviewGuiState.Detail(
                viewerId, 4L, event, 0, history
        );
        var picker = new AiReviewGuiState.LabelPicker(
                viewerId, 5L, event, 0, List.of("SAFE"), 0, detail.historyOrigin()
        );
        var confirm = new AiReviewGuiState.Confirm(
                viewerId, 6L, event, 0, AiReviewGuiState.WriteKind.CORRECT,
                CorrectionDecision.from(decision), null, "correct label",
                false, picker.historyOrigin()
        );
        assertSame(history, detail.historyOrigin());
        assertSame(history, picker.historyOrigin());
        assertSame(history, confirm.historyOrigin());
        assertEquals(OLDER_CURSOR, confirm.historyOrigin().cursor());
        assertEquals(AiReviewHistoryFilter.CORRECTED, confirm.historyOrigin().filter());
    }

    @Test
    void selectedFilterIsImmutableAndCyclesThroughClosedChoices() {
        var filtered = new AiReviewGuiState.History(
                UUID.randomUUID(), 8L, List.of(record("e1")),
                null, NEXT_CURSOR, List.of(), AiReviewHistoryFilter.FAIL_OPEN
        );
        assertEquals(AiReviewHistoryFilter.FAIL_OPEN, filtered.filter());
        assertEquals(AiReviewHistoryFilter.CORRECTED, filtered.filter().next());
        assertEquals(AiReviewHistoryFilter.ALL, filtered.filter().next().next());
        assertThrows(IllegalArgumentException.class,
                () -> new AiReviewGuiState.History(
                        UUID.randomUUID(), 8L, List.of(),
                        null, null, List.of(), null
                ));
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
