package net.enthusia.staff.paper.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ViewerInputCaptureRegistryTest {
    private static final UUID VIEWER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void freshFlowInvalidatesAnAlreadyScheduledOlderCapture() {
        ViewerInputCaptureRegistry<String> captures = new ViewerInputCaptureRegistry<>();
        captures.begin(VIEWER, "abandoned review");
        ViewerInputCaptureRegistry.Capture<String> older = captures.take(VIEWER);

        captures.invalidate(VIEWER);

        assertFalse(captures.claim(VIEWER, older));
        assertNull(captures.take(VIEWER));
    }

    @Test
    void newerCapturePreventsAnOlderCallbackFromReopeningItsReview() {
        ViewerInputCaptureRegistry<String> captures = new ViewerInputCaptureRegistry<>();
        captures.begin(VIEWER, "review");
        ViewerInputCaptureRegistry.Capture<String> older = captures.take(VIEWER);
        captures.begin(VIEWER, "review");
        ViewerInputCaptureRegistry.Capture<String> newer = captures.take(VIEWER);

        assertFalse(captures.claim(VIEWER, older));
        assertTrue(captures.claim(VIEWER, newer));
    }

    @Test
    void onlyTheFirstScheduledCallbackCanClaimACapture() {
        ViewerInputCaptureRegistry<String> captures = new ViewerInputCaptureRegistry<>();
        captures.begin(VIEWER, "review");
        ViewerInputCaptureRegistry.Capture<String> capture = captures.take(VIEWER);

        assertTrue(captures.claim(VIEWER, capture));
        assertFalse(captures.claim(VIEWER, capture));
    }
}
