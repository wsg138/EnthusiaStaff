package net.enthusia.staff.paper.aireview;

import net.enthusia.staff.paper.aireview.AiReviewGuiState.WriteKind;
import net.enthusia.staff.paper.aireview.AiReviewModels.Correction;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;

final class AiReviewWriteFence {
    private AiReviewWriteFence() {
    }

    static boolean valid(
            WriteKind kind,
            String proposalId,
            CorrectionDecision expectedDecision,
            EventDetails fresh
    ) {
        if (kind == null || fresh == null) {
            return false;
        }
        if (proposalId == null) {
            return kind == WriteKind.CORRECT
                    && expectedDecision != null
                    && fresh.acceptedCorrection() == null;
        }
        Correction pending = fresh.pendingCorrection(proposalId);
        if (pending == null) {
            return false;
        }
        return kind != WriteKind.CORRECT
                || expectedDecision != null && pending.corrected().equals(expectedDecision);
    }
}
