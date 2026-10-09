package net.enthusia.staff.paper.aireview;

import java.util.ArrayList;
import java.util.List;
import net.enthusia.staff.paper.aireview.AiReviewModels.DecisionHistoryItem;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageAction;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import org.bukkit.Material;

/** Safe, read-only audit-row rendering: no message text or player identity. */
final class AiReviewHistoryPresentation {
    private AiReviewHistoryPresentation() {
    }

    static Row summarize(DecisionHistoryItem decision) {
        boolean unavailable = decision.degraded()
                || "FAIL_OPEN".equals(decision.ingestionStatus());
        Material material;
        String title;
        if (unavailable) {
            material = Material.GRAY_DYE;
            title = "Fail-open · AI unavailable";
        } else if (decision.messageAction() == MessageAction.BLOCK) {
            material = Material.RED_DYE;
            title = "Blocked · " + friendlyLabel(decision.semanticLabel());
        } else if (decision.reviewPriority() != ReviewPriority.NONE) {
            material = Material.YELLOW_DYE;
            title = "Allowed · Staff review";
        } else {
            material = Material.LIME_DYE;
            title = "Allowed · " + friendlyLabel(decision.semanticLabel());
        }
        List<String> lore = new ArrayList<>();
        lore.add("Classification: " + friendlyLabel(decision.semanticLabel()));
        lore.add("Platform: " + AiReviewPresentation.bounded(decision.platform(), 30));
        lore.add("When: " + decision.occurredAt());
        lore.add("Review priority: " + decision.reviewPriority());
        if (decision.corrected()) {
            lore.add("Staff correction recorded");
        }
        if (unavailable) {
            lore.add("The AI could not safely classify this message.");
            lore.add("Fail-open is NOT a verified safe decision.");
        }
        lore.add("Click to examine this event");
        return new Row(material, title, List.copyOf(lore));
    }

    record Row(Material material, String title, List<String> lore) {
    }

    private static String friendlyLabel(String label) {
        return AiReviewPresentation.bounded(label.replace('_', ' '), 36);
    }
}
