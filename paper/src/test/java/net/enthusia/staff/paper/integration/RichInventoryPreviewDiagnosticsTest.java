package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RichInventoryPreviewDiagnosticsTest {
    @Test
    void onlyUnescapedInventoryAliasesTriggerPrivateSafeWarningCandidate() {
        assertTrue(RichInventoryPreviewDiagnostics.markerPresent("[inv]"));
        assertTrue(RichInventoryPreviewDiagnostics.markerPresent("Is this good? [INVENTORY]"));
        assertTrue(RichInventoryPreviewDiagnostics.markerPresent("hello \\\\[inv]"));
        assertFalse(RichInventoryPreviewDiagnostics.markerPresent("\\[inv]"));
        assertFalse(RichInventoryPreviewDiagnostics.markerPresent("[item]"));
        assertFalse(RichInventoryPreviewDiagnostics.markerPresent("[ender]"));
        assertFalse(RichInventoryPreviewDiagnostics.markerPresent(null));
    }

    @Test
    void warningThrottleBoundsLogVolumeAndRecoversAfterClockRollback() {
        AtomicLong last = new AtomicLong(Long.MIN_VALUE);
        assertTrue(RichInventoryPreviewDiagnostics.reserveWarning(last, 100_000L));
        assertFalse(RichInventoryPreviewDiagnostics.reserveWarning(last, 159_999L));
        assertTrue(RichInventoryPreviewDiagnostics.reserveWarning(last, 160_000L));
        // A clock rollback cannot make the warning mute permanent.
        assertTrue(RichInventoryPreviewDiagnostics.reserveWarning(last, 10_000L));
        assertFalse(RichInventoryPreviewDiagnostics.reserveWarning(last, 10_001L));
    }
}
