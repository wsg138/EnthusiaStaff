package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportEvidencePurgeResult;
import net.enthusia.staff.domain.report.ReportMessagesPage;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.domain.report.ReportSummary;

public interface ReportStore {
    ReportSubmissionResult submit(CreateReportRequest request);

    List<ReportSummary> list(ReportQueue queue, UUID actorId, int limit);

    List<ReportSummary> listActiveForTarget(UUID targetId, int limit);

    Optional<ReportDetails> details(UUID reportId);

    /** Lightweight existence check; never load confidential snapshots just to check an ID. */
    default boolean exists(UUID reportId) {
        throw new UnsupportedOperationException("Report existence lookup is unavailable");
    }

    /** A bounded staff-only chronological history; implementations must fail closed if unsupported. */
    default ReportMessagesPage messages(UUID reportId, int page) {
        throw new UnsupportedOperationException("Report message history is unavailable");
    }

    ReportStateChangeResult changeState(ReportStateChangeRequest request);

    ReportEvidencePurgeResult purgeExpiredEvidence(Instant now, int batchLimit);
}
