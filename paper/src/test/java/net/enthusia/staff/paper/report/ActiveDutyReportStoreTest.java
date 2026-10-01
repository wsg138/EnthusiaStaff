package net.enthusia.staff.paper.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportEvidencePurgeResult;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.domain.report.ReportSummary;
import org.junit.jupiter.api.Test;

class ActiveDutyReportStoreTest {
    private static final UUID REPORT_ID = UUID.fromString("52000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("52000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-09-30T20:00:00Z");

    @Test
    void inactiveActorCannotReachReportMutationStore() {
        CountingStore delegate = new CountingStore();
        ActiveDutyReportStore store = new ActiveDutyReportStore(delegate, ignored -> false);

        ReportStateChangeResult result = store.changeState(request());

        ReportStateChangeResult.Rejected rejected = assertInstanceOf(ReportStateChangeResult.Rejected.class, result);
        assertEquals("ACTIVE_DUTY_REQUIRED", rejected.code());
        assertEquals(0, delegate.changes.get());
    }

    @Test
    void activeActorStillUsesUnderlyingReportMutationStore() {
        CountingStore delegate = new CountingStore();
        ActiveDutyReportStore store = new ActiveDutyReportStore(delegate, ACTOR_ID::equals);

        ReportStateChangeResult result = store.changeState(request());

        assertInstanceOf(ReportStateChangeResult.Applied.class, result);
        assertEquals(1, delegate.changes.get());
    }

    private static ReportStateChangeRequest request() {
        return new ReportStateChangeRequest(
                REPORT_ID,
                ACTOR_ID,
                ReportAction.CLOSE,
                1L,
                "resolved in test",
                new IdempotencyKey("report-duty-test"),
                NOW
        );
    }

    private static final class CountingStore implements ReportStore {
        private final AtomicInteger changes = new AtomicInteger();

        @Override
        public ReportSubmissionResult submit(CreateReportRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReportSummary> list(ReportQueue queue, UUID actorId, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReportSummary> listActiveForTarget(UUID targetId, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ReportDetails> details(UUID reportId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReportStateChangeResult changeState(ReportStateChangeRequest request) {
            changes.incrementAndGet();
            return new ReportStateChangeResult.Applied(ReportState.CLOSED, 2L, false);
        }

        @Override
        public ReportEvidencePurgeResult purgeExpiredEvidence(Instant now, int batchLimit) {
            throw new UnsupportedOperationException();
        }
    }
}
