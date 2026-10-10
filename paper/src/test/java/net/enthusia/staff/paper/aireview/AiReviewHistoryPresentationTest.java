package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import net.enthusia.staff.paper.aireview.AiReviewModels.DecisionHistoryItem;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageAction;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class AiReviewHistoryPresentationTest {
    private static DecisionHistoryItem decision(
            String ingestionStatus, MessageAction action, ReviewPriority priority,
            boolean degraded, boolean corrected
    ) {
        return new DecisionHistoryItem(
                "event-1", Instant.parse("2026-10-09T12:00:00Z"),
                Instant.parse("2026-10-09T12:00:01Z"),
                "minecraft", "minecraft_private", ingestionStatus, action,
                action == MessageAction.BLOCK ? "LOW_LEVEL_HARASSMENT" : "SAFE",
                priority, List.of(), "model-1", "policy-1", degraded, corrected
        );
    }

    @Test
    void failOpenCannotBeMisrepresentedAsSafeClassification() {
        var row = AiReviewHistoryPresentation.summarize(
                decision("FAIL_OPEN", MessageAction.ALLOW, ReviewPriority.NONE, true, false)
        );
        assertEquals(Material.GRAY_DYE, row.material());
        assertTrue(row.title().contains("Fail-open"));
        assertTrue(row.lore().stream().anyMatch(s -> s.contains("NOT a verified safe decision")));
        assertFalse(row.title().startsWith("Allowed"));
    }

    @Test
    void staffReviewIsDifferentFromOrdinaryAllow() {
        var ordinary = AiReviewHistoryPresentation.summarize(
                decision("INGESTED", MessageAction.ALLOW, ReviewPriority.NONE, false, false)
        );
        var flagged = AiReviewHistoryPresentation.summarize(
                decision("INGESTED", MessageAction.ALLOW, ReviewPriority.NORMAL, false, true)
        );
        assertEquals(Material.LIME_DYE, ordinary.material());
        assertEquals(Material.YELLOW_DYE, flagged.material());
        assertTrue(flagged.title().contains("Staff review"));
        assertTrue(flagged.lore().contains("Staff correction recorded"));
    }

    @Test
    void blockShowsReadableCategoryWithoutRawMessageOrIdentity() {
        var row = AiReviewHistoryPresentation.summarize(
                decision("INGESTED", MessageAction.BLOCK, ReviewPriority.URGENT, false, false)
        );
        assertEquals(Material.RED_DYE, row.material());
        assertTrue(row.title().contains("LOW LEVEL HARASSMENT"));
        assertFalse(row.lore().toString().contains("player-1"));
    }
}
