package net.enthusia.staff.paper.aireview;

import java.util.List;
import net.enthusia.staff.paper.aireview.AiReviewModels.Correction;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionAuthority;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewItem;

interface AiReviewClient {
    List<ReviewItem> listReviews(int limit);

    EventDetails event(String eventId);

    Correction correct(
            String eventId,
            String reviewerId,
            CorrectionAuthority authority,
            CorrectionDecision corrected,
            String note
    );

    Correction reject(
            String proposalId,
            String reviewerId,
            CorrectionAuthority authority,
            String note
    );
}
