package net.enthusia.staff.paper.report;

import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportSummary;

final class ReportGuiAccess {
    private ReportGuiAccess() {
    }

    static List<ReportAction> actions(ReportSummary summary, UUID actorId) {
        if (summary == null || actorId == null) {
            throw new IllegalArgumentException("report GUI action context must be present");
        }
        if (summary.state() == ReportState.OPEN) {
            return List.of(ReportAction.CLAIM);
        }
        if (summary.state() == ReportState.CLAIMED) {
            return summary.assignedTo().filter(actorId::equals).isPresent()
                    ? List.of(ReportAction.AWAIT_REVIEW, ReportAction.CLOSE, ReportAction.NO_VIOLATION)
                    : List.of(ReportAction.CLOSE, ReportAction.NO_VIOLATION);
        }
        if (summary.state() == ReportState.AWAITING_REVIEW) {
            return List.of(ReportAction.CLOSE, ReportAction.NO_VIOLATION);
        }
        return List.of();
    }
}
