package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheatTesterRestorationSafetyTest {
    @Test
    void failedEvidenceCaptureStillAllowsRestoration() {
        List<String> calls = new ArrayList<>();
        String captured = CheatTesterManager.captureForRestoration(() -> {
            throw new IllegalStateException("target metadata unavailable");
        }, failure -> calls.add("capture-failed"));
        CheatTesterManager.checkpointThenRestore(() -> calls.add("checkpoint:" + captured),
                () -> calls.add("restore"), failure -> calls.add("failed"));
        assertEquals(List.of("capture-failed", "checkpoint:{\"evidenceCaptureFailed\":true}", "restore"), calls);
    }

    @Test
    void failedStoreSupplierOrCheckpointStillSchedulesExactlyOneRestoration() {
        List<String> calls = new ArrayList<>();
        CheatTesterManager.checkpointThenRestore(() -> {
            calls.add("checkpoint");
            throw new IllegalStateException("storage unavailable");
        }, () -> calls.add("restore"), failure -> calls.add("failure"));
        assertEquals(List.of("checkpoint", "failure", "restore"), calls);
    }

    @Test
    void successfulCaptureAndCheckpointPreserveEvidenceAndOrder() {
        List<String> calls = new ArrayList<>();
        assertEquals("evidence", CheatTesterManager.captureForRestoration(() -> "evidence", failure -> calls.add("failure")));
        CheatTesterManager.checkpointThenRestore(() -> calls.add("checkpoint"), () -> calls.add("restore"), failure -> calls.add("failure"));
        assertEquals(List.of("checkpoint", "restore"), calls);
    }
}
