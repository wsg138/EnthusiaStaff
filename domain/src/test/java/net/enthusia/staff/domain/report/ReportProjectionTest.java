package net.enthusia.staff.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.IdempotencyKey;
import org.junit.jupiter.api.Test;

final class ReportProjectionTest {
    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void validProjectionCopiesCollectionsAndCarriesState() {
        ReportSummary summary = summary();
        List<String> publicChat = new ArrayList<>(List.of("public"));
        List<String> privateChat = new ArrayList<>(List.of("private"));
        List<String> evidence = new ArrayList<>(List.of("client"));

        ReportDetails details = new ReportDetails(
                summary,
                "Detailed report",
                Optional.of("world"),
                Optional.of("1,2,3"),
                Optional.empty(),
                publicChat,
                privateChat,
                evidence
        );
        publicChat.clear();
        privateChat.clear();
        evidence.clear();

        assertEquals(List.of("public"), details.publicChatSnapshots());
        assertEquals(List.of("private"), details.privateMessageSnapshots());
        assertEquals(List.of("client"), details.clientEvidenceSnapshots());
        assertThrows(UnsupportedOperationException.class, () -> details.publicChatSnapshots().add("other"));

        ReportStateChangeRequest request = new ReportStateChangeRequest(
                summary.reportId(),
                UUID.randomUUID(),
                ReportAction.CLAIM,
                summary.revision(),
                "claiming report",
                new IdempotencyKey("report-claim-1"),
                NOW
        );
        assertEquals(ReportAction.CLAIM, request.action());
        assertEquals(ReportState.OPEN, summary.state());
        assertEquals(5, ReportQueue.values().length);

        ReportStateChangeResult.Applied applied =
                new ReportStateChangeResult.Applied(ReportState.CLAIMED, 4L, false);
        ReportStateChangeResult.Rejected rejected =
                new ReportStateChangeResult.Rejected("REVISION_CONFLICT", "report changed");
        assertEquals(4L, applied.revision());
        assertEquals("REVISION_CONFLICT", rejected.code());
    }

    @Test
    void invalidProjectionFieldsAreRejected() {
        ReportSummary summary = summary();
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportSummary(
                        summary.reportId(),
                        summary.reporterId(),
                        summary.targetId(),
                        " ",
                        ReportState.OPEN,
                        Optional.empty(),
                        "SMP",
                        NOW,
                        NOW,
                        0L
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportDetails(
                        summary,
                        " ",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportStateChangeRequest(
                        summary.reportId(),
                        UUID.randomUUID(),
                        ReportAction.CLOSE,
                        -1L,
                        "note",
                        new IdempotencyKey("report-close-1"),
                        NOW
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportStateChangeResult.Applied(null, 0L, false)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportStateChangeResult.Rejected(" ", "message")
        );
    }

    private static ReportSummary summary() {
        return new ReportSummary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "chat.abuse",
                ReportState.OPEN,
                Optional.empty(),
                "SMP",
                NOW,
                NOW,
                3L
        );
    }
}
