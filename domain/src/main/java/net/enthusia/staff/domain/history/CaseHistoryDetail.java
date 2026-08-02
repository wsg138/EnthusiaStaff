package net.enthusia.staff.domain.history;

import java.util.List;
import net.enthusia.staff.domain.casefile.CaseReview;

public record CaseHistoryDetail(
        CaseReview caseReview,
        List<ModerationHistoryEntry> timeline
) {
    public CaseHistoryDetail {
        if (caseReview == null || timeline == null) {
            throw new IllegalArgumentException("case history fields must be present");
        }
        timeline = List.copyOf(timeline);
    }
}
