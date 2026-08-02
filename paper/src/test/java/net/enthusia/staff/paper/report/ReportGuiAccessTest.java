package net.enthusia.staff.paper.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportSummary;
import org.junit.jupiter.api.Test;

final class ReportGuiAccessTest {
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-01-01T01:00:00Z");

    @Test
    void openReportsCanOnlyBeClaimed() {
        UUID actor = UUID.randomUUID();

        assertEquals(List.of(ReportAction.CLAIM), ReportGuiAccess.actions(
                summary(ReportState.OPEN, Optional.empty()), actor
        ));
    }

    @Test
    void assigneeCanRequestReviewOrResolveClaimedReport() {
        UUID actor = UUID.randomUUID();

        assertEquals(
                List.of(ReportAction.AWAIT_REVIEW, ReportAction.CLOSE, ReportAction.NO_VIOLATION),
                ReportGuiAccess.actions(summary(ReportState.CLAIMED, Optional.of(actor)), actor)
        );
    }

    @Test
    void otherStaffCannotRequestReviewForClaimedReport() {
        UUID assignee = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        assertEquals(
                List.of(ReportAction.CLOSE, ReportAction.NO_VIOLATION),
                ReportGuiAccess.actions(summary(ReportState.CLAIMED, Optional.of(assignee)), actor)
        );
    }

    @Test
    void completedReportsExposeNoMutationActions() {
        UUID actor = UUID.randomUUID();

        assertEquals(List.of(), ReportGuiAccess.actions(
                summary(ReportState.CLOSED, Optional.of(actor)), actor
        ));
        assertEquals(List.of(), ReportGuiAccess.actions(
                summary(ReportState.NO_VIOLATION, Optional.of(actor)), actor
        ));
    }

    private static ReportSummary summary(ReportState state, Optional<UUID> assignedTo) {
        return new ReportSummary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "chat.spam",
                state,
                assignedTo,
                "smp",
                CREATED,
                UPDATED,
                4
        );
    }
}
