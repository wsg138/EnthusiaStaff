package net.enthusia.staff.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ReportEvidencePurgeResultTest {
    @Test
    void reportsTotalAndPerCategorySaturation() {
        ReportEvidencePurgeResult result = new ReportEvidencePurgeResult(200, 500, 100);

        assertEquals(800, result.total());
        assertTrue(result.hasBacklogAt(500));
        assertFalse(result.hasBacklogAt(501));
    }

    @Test
    void rejectsInvalidCountsAndLimits() {
        assertThrows(IllegalArgumentException.class, () -> new ReportEvidencePurgeResult(-1, 0, 0));
        ReportEvidencePurgeResult result = new ReportEvidencePurgeResult(0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> result.hasBacklogAt(0));
    }
}
