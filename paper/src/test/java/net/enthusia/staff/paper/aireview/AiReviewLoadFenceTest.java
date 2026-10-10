package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Synthetic, scheduler-free regression for stale asynchronous GUI responses. */
class AiReviewLoadFenceTest {
    @Test
    void newerLoadSupersedesOldResponseAndCanOnlyBeConsumedOnce() {
        var fence = new AiReviewLoadFence();
        UUID viewer = UUID.randomUUID();
        UUID oldToken = fence.begin(viewer);
        UUID currentToken = fence.begin(viewer);

        assertFalse(fence.consume(viewer, oldToken));
        assertEquals(1, fence.pendingViewerCount());
        assertTrue(fence.consume(viewer, currentToken));
        assertFalse(fence.consume(viewer, currentToken));
        assertEquals(0, fence.pendingViewerCount());
    }

    @Test
    void inventoryCloseRetiresOutstandingLoadWithoutAffectingOtherViewer() {
        var fence = new AiReviewLoadFence();
        UUID firstViewer = UUID.randomUUID();
        UUID secondViewer = UUID.randomUUID();
        UUID first = fence.begin(firstViewer);
        UUID second = fence.begin(secondViewer);

        fence.retire(firstViewer);

        assertFalse(fence.consume(firstViewer, first));
        assertTrue(fence.consume(secondViewer, second));
        assertEquals(0, fence.pendingViewerCount());
    }

    @Test
    void invalidViewerOrTokenCannotAuthorizeResult() {
        var fence = new AiReviewLoadFence();
        UUID viewer = UUID.randomUUID();
        UUID token = fence.begin(viewer);

        assertFalse(fence.consume(null, token));
        assertFalse(fence.consume(viewer, null));
        assertFalse(fence.consume(UUID.randomUUID(), token));
        assertThrows(IllegalArgumentException.class, () -> fence.begin(null));
        assertTrue(fence.consume(viewer, token));
        fence.retire(null);
        assertEquals(0, fence.pendingViewerCount());
    }
}
