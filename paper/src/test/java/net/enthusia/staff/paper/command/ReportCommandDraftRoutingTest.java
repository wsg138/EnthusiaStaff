package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ReportCommandDraftRoutingTest {
    @Test
    void validOneWordPlayerNamesAreNotReservedWhenNoDraftExists() {
        assertFalse(ReportCommand.isDescriptionContinuation(new String[]{"describe"}, false));
        assertFalse(ReportCommand.isCancelContinuation(new String[]{"cancel"}, false));
        assertFalse(ReportCommand.isDescriptionContinuation(new String[]{"Describe"}, false));
        assertFalse(ReportCommand.isCancelContinuation(new String[]{"Cancel"}, false));
    }

    @Test
    void selectedDescriptionRequiresDraftOrExplicitArguments() {
        assertTrue(ReportCommand.isDescriptionContinuation(new String[]{"describe"}, true));
        assertTrue(ReportCommand.isDescriptionContinuation(
                new String[]{"describe", "what", "happened"}, true));
        // This still enters the continuation handler, which safely rejects an
        // absent or expired draft instead of submitting anything.
        assertTrue(ReportCommand.isDescriptionContinuation(
                new String[]{"describe", "what", "happened"}, false));
        assertFalse(ReportCommand.isDescriptionContinuation(new String[]{"notdescribe"}, true));
    }

    @Test
    void cancellationIsScopedToTheExistingReportDraft() {
        assertTrue(ReportCommand.isCancelContinuation(new String[]{"cancel"}, true));
        assertTrue(ReportCommand.isCancelContinuation(new String[]{"CANCEL"}, true));
        assertFalse(ReportCommand.isCancelContinuation(new String[]{"cancel"}, false));
        assertFalse(ReportCommand.isCancelContinuation(
                new String[]{"cancel", "anotherPlayer"}, true));
        assertFalse(ReportCommand.isCancelContinuation(new String[]{"describe"}, true));
    }
}
