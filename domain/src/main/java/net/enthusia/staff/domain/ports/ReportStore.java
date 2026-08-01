package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.domain.report.ReportSummary;

public interface ReportStore {
    ReportSubmissionResult submit(CreateReportRequest request);

    List<ReportSummary> list(ReportQueue queue, UUID actorId, int limit);

    Optional<ReportDetails> details(UUID reportId);

    ReportStateChangeResult changeState(ReportStateChangeRequest request);

    int purgeExpiredEvidence(Instant now, int batchLimit);
}
