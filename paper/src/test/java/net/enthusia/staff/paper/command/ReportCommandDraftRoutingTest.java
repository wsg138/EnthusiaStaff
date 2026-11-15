package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ReportCommandDraftRoutingTest {
    private static final String DESCRIBE = "describe";
    private static final String CANCEL = "cancel";
    @Test
    void validOneWordPlayerNamesAreNotReservedWhenNoDraftExists() {
        assertFalse(ReportCommand.isDescriptionContinuation(new String[]{DESCRIBE}, false));
        assertFalse(ReportCommand.isCancelContinuation(new String[]{CANCEL}, false));
        assertFalse(ReportCommand.isDescriptionContinuation(new String[]{"Describe"}, false));
        assertFalse(ReportCommand.isCancelContinuation(new String[]{"Cancel"}, false));
    }

    @Test
    void selectedDescriptionRequiresDraftOrExplicitArguments() {
        assertTrue(ReportCommand.isDescriptionContinuation(new String[]{DESCRIBE}, true));
        assertTrue(ReportCommand.isDescriptionContinuation(
                new String[]{DESCRIBE, "what", "happened"}, true));
        // This still enters the continuation handler, which safely rejects an
        // absent or expired draft instead of submitting anything.
        assertTrue(ReportCommand.isDescriptionContinuation(
                new String[]{DESCRIBE, "what", "happened"}, false));
        assertFalse(ReportCommand.isDescriptionContinuation(new String[]{"notdescribe"}, true));
    }

    @Test
    void cancellationIsScopedToTheExistingReportDraft() {
        assertTrue(ReportCommand.isCancelContinuation(new String[]{CANCEL}, true));
        assertTrue(ReportCommand.isCancelContinuation(new String[]{"CANCEL"}, true));
        assertFalse(ReportCommand.isCancelContinuation(new String[]{CANCEL}, false));
        assertFalse(ReportCommand.isCancelContinuation(
                new String[]{CANCEL, "anotherPlayer"}, true));
        assertFalse(ReportCommand.isCancelContinuation(new String[]{DESCRIBE}, true));
    }
}
