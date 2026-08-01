package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportEvidencePurgeResult;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.domain.report.ReportSummary;

public final class JdbcReportStore implements ReportStore {
    private final JdbcReportSubmissionStore submissions;
    private final JdbcReportQueryStore queries;
    private final JdbcReportStateStore states;
    private final JdbcReportEvidenceMaintenance evidenceMaintenance;

    public JdbcReportStore(DataSource dataSource, ObjectMapper json) {
        this.submissions = new JdbcReportSubmissionStore(dataSource, json);
        this.queries = new JdbcReportQueryStore(dataSource);
        this.states = new JdbcReportStateStore(dataSource, json);
        this.evidenceMaintenance = new JdbcReportEvidenceMaintenance(dataSource);
    }

    @Override
    public ReportSubmissionResult submit(CreateReportRequest request) {
        return submissions.submit(request);
    }

    @Override
    public List<ReportSummary> list(ReportQueue queue, UUID actorId, int limit) {
        return queries.list(queue, actorId, limit);
    }

    @Override
    public Optional<ReportDetails> details(UUID reportId) {
        return queries.details(reportId);
    }

    @Override
    public ReportStateChangeResult changeState(ReportStateChangeRequest request) {
        return states.changeState(request);
    }

    @Override
    public ReportEvidencePurgeResult purgeExpiredEvidence(Instant now, int batchLimit) {
        return evidenceMaintenance.purgeExpired(now, batchLimit);
    }
}
