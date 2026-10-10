package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import net.enthusia.staff.paper.aireview.AiReviewClientException.Category;
import net.enthusia.staff.paper.aireview.AiReviewModels.Containment;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionAuthority;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionStatus;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageAction;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import net.enthusia.staff.paper.aireview.AiReviewModels.StrikeRecommendation;
import net.enthusia.staff.paper.aireview.AiReviewModels.SupportFlow;
import org.junit.jupiter.api.Test;

/** Opt-in contract against the actual Python API on loopback; never uses production credentials. */
class AiReviewRealApiContractTest {
    private static final String BASE_URL = System.getenv("ENTHUSIA_CONTRACT_BASE_URL");
    private static final String STAFF_TOKEN = System.getenv("ENTHUSIA_CONTRACT_STAFF_TOKEN");
    private static final String READER_TOKEN = System.getenv("ENTHUSIA_CONTRACT_READER_TOKEN");
    private static final String BLOCK_ID = System.getenv("ENTHUSIA_CONTRACT_BLOCK_ID");
    private static final String FAIL_OPEN_ID = System.getenv("ENTHUSIA_CONTRACT_FAIL_OPEN_ID");

    @Test
    void liveLoopbackApiProvidesFilteredHistoryAndTwoStaffCorrections() {
        assumeTrue(BASE_URL != null && BASE_URL.startsWith("http://127.0.0.1:"));
        assumeTrue(STAFF_TOKEN != null && READER_TOKEN != null
                && BLOCK_ID != null && FAIL_OPEN_ID != null);
        AiReviewHttpClient staff = client("contract-staff", STAFF_TOKEN);

        var first = staff.listDecisions(1, null);
        assertEquals(1, first.items().size());
        assertTrue(first.nextCursor() != null);
        var second = staff.listDecisions(1, first.nextCursor());
        assertEquals(1, second.items().size());
        assertFalse(first.items().get(0).eventId().equals(second.items().get(0).eventId()));

        var blocked = staff.listDecisions(10, null, AiReviewHistoryFilter.BLOCKED);
        assertEquals(1, blocked.items().size());
        assertEquals(BLOCK_ID, blocked.items().get(0).eventId());
        assertEquals(MessageAction.BLOCK, blocked.items().get(0).messageAction());
        assertFalse(blocked.items().get(0).corrected());
        assertEquals(1, staff.listDecisions(10, null, AiReviewHistoryFilter.REVIEW)
                .items().stream().filter(x -> x.messageAction() == MessageAction.ALLOW).count());
        assertEquals(2, staff.listDecisions(10, null, AiReviewHistoryFilter.ALLOWED)
                .items().size());
        assertRealFailOpen(staff);

        var original = staff.event(BLOCK_ID);
        assertEquals(MessageAction.BLOCK, original.decision().messageAction());
        assertEquals("LOW_LEVEL_HARASSMENT", original.decision().semanticLabel());
        assertEquals(Category.AUTH, assertThrows(AiReviewClientException.class,
                () -> client("contract-reader", READER_TOKEN)
                        .correct(BLOCK_ID, "contract-reader", CorrectionAuthority.STAFF,
                                safeCorrection(), null)).category());

        var firstVote = staff.correct(BLOCK_ID, "contract-reviewer-a",
                CorrectionAuthority.STAFF, safeCorrection(), "synthetic correction");
        assertEquals(CorrectionStatus.PENDING_CONFIRMATION, firstVote.status());
        var duplicate = staff.correct(BLOCK_ID, "contract-reviewer-a",
                CorrectionAuthority.STAFF, safeCorrection(), "synthetic retry");
        assertEquals(1, duplicate.approvals());
        var secondVote = staff.correct(BLOCK_ID, "contract-reviewer-b",
                CorrectionAuthority.STAFF, safeCorrection(), "synthetic confirmation");
        assertEquals(CorrectionStatus.ACCEPTED, secondVote.status());
        assertEquals(2, secondVote.approvals());

        var corrected = staff.listDecisions(10, null, AiReviewHistoryFilter.CORRECTED);
        assertEquals(1, corrected.items().size());
        assertEquals(BLOCK_ID, corrected.items().get(0).eventId());
        assertTrue(corrected.items().get(0).corrected());
        assertEquals(MessageAction.BLOCK, corrected.items().get(0).messageAction());
        assertEquals("LOW_LEVEL_HARASSMENT", corrected.items().get(0).semanticLabel());
        assertEquals(MessageAction.ALLOW, staff.event(BLOCK_ID)
                .acceptedCorrection().corrected().messageAction());
    }

    private static void assertRealFailOpen(AiReviewHttpClient staff) {
        var items = staff.listDecisions(10, null, AiReviewHistoryFilter.FAIL_OPEN).items();
        assertEquals(1, items.size());
        var fallback = items.getFirst();
        assertEquals(FAIL_OPEN_ID, fallback.eventId());
        assertEquals("FAIL_OPEN", fallback.ingestionStatus());
        assertEquals(MessageAction.ALLOW, fallback.messageAction());
        assertTrue(fallback.degraded());
        assertTrue(AiReviewHistoryPresentation.summarize(fallback)
                .lore().contains("Fail-open is NOT a verified safe decision."));
    }

    private static CorrectionDecision safeCorrection() {
        return new CorrectionDecision("SAFE", MessageAction.ALLOW, ReviewPriority.NONE,
                StrikeRecommendation.NONE, Containment.NONE, null, SupportFlow.NONE,
                List.of("human_verified_synthetic"));
    }

    private static AiReviewHttpClient client(String clientId, String token) {
        var config = new AiReviewConfiguration(
                URI.create(BASE_URL), clientId, token, AiReviewPermissions.QUEUE,
                Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(10),
                Duration.ofSeconds(30), 100, 1, 8, 262_144, 32, 21, 800, 8, 12, 10, false
        );
        return new AiReviewHttpClient(config, new ObjectMapper());
    }
}
