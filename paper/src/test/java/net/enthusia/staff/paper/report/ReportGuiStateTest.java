package net.enthusia.staff.paper.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportSummary;
import org.junit.jupiter.api.Test;

final class ReportGuiStateTest {
    @Test
    void queueStateCopiesItsBoundedReports() {
        List<ReportSummary> reports = new ArrayList<>();
        reports.add(summary());

        ReportGuiState.Queue state = new ReportGuiState.Queue(
                UUID.randomUUID(), ReportQueue.OPEN, reports, 0
        );
        reports.clear();

        assertEquals(1, state.reports().size());
    }

    @Test
    void queueStateRejectsMoreThanDatabaseBound() {
        List<ReportSummary> reports = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            reports.add(summary());
        }

        assertThrows(IllegalArgumentException.class, () -> new ReportGuiState.Queue(
                UUID.randomUUID(), ReportQueue.OPEN, reports, 0
        ));
    }

    @Test
    void reviewStateTrimsNoteAndPreservesDisplayedRevision() {
        ReportDetails details = details();
        ReportGuiState.Review state = new ReportGuiState.Review(
                UUID.randomUUID(),
                ReportQueue.CLAIMED_BY_ME,
                0,
                details,
                ReportAction.CLOSE,
                "  investigated and confirmed  ",
                UUID.randomUUID()
        );

        assertEquals("investigated and confirmed", state.note());
        assertEquals(details.summary().revision(), state.details().summary().revision());
    }

    @Test
    void reviewStateRejectsOversizedNote() {
        assertThrows(IllegalArgumentException.class, () -> new ReportGuiState.Review(
                UUID.randomUUID(),
                ReportQueue.OPEN,
                0,
                details(),
                ReportAction.CLAIM,
                "x".repeat(2_001),
                UUID.randomUUID()
        ));
    }

    private static ReportDetails details() {
        return new ReportDetails(
                summary(),
                "The player repeatedly advertised in public chat.",
                Optional.of("minecraft:overworld"),
                Optional.of("1,64,1"),
                Optional.of("2,64,2"),
                List.of("public"),
                List.of(),
                List.of()
        );
    }

    private static ReportSummary summary() {
        return new ReportSummary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "chat.spam",
                ReportState.CLAIMED,
                Optional.of(UUID.randomUUID()),
                "smp",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"),
                7
        );
    }
}
