package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.paper.aireview.AiReviewGuiState.WriteKind;
import net.enthusia.staff.paper.aireview.AiReviewModels.Containment;
import net.enthusia.staff.paper.aireview.AiReviewModels.Correction;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionStatus;
import net.enthusia.staff.paper.aireview.AiReviewModels.Decision;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageAction;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import net.enthusia.staff.paper.aireview.AiReviewModels.StrikeRecommendation;
import net.enthusia.staff.paper.aireview.AiReviewModels.SupportFlow;
import org.junit.jupiter.api.Test;

class AiReviewFenceTest {
    @Test
    void newerQueueLoadRetiresOlderResponse() {
        AiReviewLoadFence fence = new AiReviewLoadFence();
        UUID viewer = UUID.randomUUID();
        UUID older = fence.begin(viewer);
        UUID newer = fence.begin(viewer);

        assertFalse(fence.consume(viewer, older));
        assertTrue(fence.consume(viewer, newer));
        assertTrue(fence.pendingViewerCount() == 0);
    }

    @Test
    void retiredViewerCannotConsumeOutstandingLoad() {
        AiReviewLoadFence fence = new AiReviewLoadFence();
        UUID viewer = UUID.randomUUID();
        UUID token = fence.begin(viewer);
        fence.retire(viewer);
        assertFalse(fence.consume(viewer, token));
    }

    @Test
    void newCorrectionCannotOverwriteAcceptedCentralCorrection() {
        CorrectionDecision corrected = corrected();
        EventDetails fresh = event(
                List.of(accepted("accepted", corrected)),
                accepted("accepted", corrected)
        );
        assertFalse(AiReviewWriteFence.valid(
                WriteKind.CORRECT,
                null,
                corrected,
                fresh
        ));
    }

    @Test
    void pendingProposalMustStillExistAndMatchBeforeApproval() {
        CorrectionDecision corrected = corrected();
        EventDetails matching = event(
                List.of(pending("proposal-1", corrected)),
                null
        );
        assertTrue(AiReviewWriteFence.valid(
                WriteKind.CORRECT,
                "proposal-1",
                corrected,
                matching
        ));
        assertTrue(AiReviewWriteFence.valid(
                WriteKind.REJECT,
                "proposal-1",
                null,
                matching
        ));

        EventDetails resolved = event(
                List.of(accepted("proposal-1", corrected)),
                accepted("proposal-1", corrected)
        );
        assertFalse(AiReviewWriteFence.valid(
                WriteKind.CORRECT,
                "proposal-1",
                corrected,
                resolved
        ));
        assertFalse(AiReviewWriteFence.valid(
                WriteKind.REJECT,
                "proposal-1",
                null,
                resolved
        ));

        CorrectionDecision changed = corrected.withAction(MessageAction.ALLOW);
        assertFalse(AiReviewWriteFence.valid(
                WriteKind.CORRECT,
                "proposal-1",
                changed,
                matching
        ));
    }

    private static EventDetails event(List<Correction> corrections, Correction accepted) {
        return new EventDetails(
                "event-1",
                "client",
                "minecraft",
                "minecraft_public",
                "SMP",
                "global",
                null,
                "message-1",
                null,
                "sender-1",
                Instant.parse("2026-10-03T20:00:00Z"),
                "synthetic",
                null,
                decision(),
                null,
                corrections,
                accepted,
                List.of()
        );
    }

    private static Decision decision() {
        return new Decision(
                MessageAction.BLOCK,
                "REAL_WORLD_THREAT",
                ReviewPriority.URGENT,
                StrikeRecommendation.EVIDENCE,
                Containment.MUTE,
                120,
                SupportFlow.TARGET_SAFETY_CHECK,
                Map.of("REAL_WORLD_THREAT", 0.9),
                0.9,
                List.of("rule"),
                List.of("reason"),
                "model-v1",
                "v1"
        );
    }

    private static CorrectionDecision corrected() {
        return CorrectionDecision.from(decision());
    }

    private static Correction pending(String id, CorrectionDecision corrected) {
        return correction(id, corrected, CorrectionStatus.PENDING_CONFIRMATION);
    }

    private static Correction accepted(String id, CorrectionDecision corrected) {
        return correction(id, corrected, CorrectionStatus.ACCEPTED);
    }

    private static Correction correction(
            String id,
            CorrectionDecision corrected,
            CorrectionStatus status
    ) {
        return new Correction(
                id,
                "event-1",
                status,
                corrected,
                status == CorrectionStatus.ACCEPTED ? 2 : 1,
                0,
                Instant.parse("2026-10-03T20:00:01Z"),
                status == CorrectionStatus.ACCEPTED
                        ? Instant.parse("2026-10-03T20:00:02Z")
                        : null
        );
    }
}
