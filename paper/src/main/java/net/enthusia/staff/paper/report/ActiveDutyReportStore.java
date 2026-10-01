package net.enthusia.staff.paper.report;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportEvidencePurgeResult;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.domain.report.ReportSummary;

/** Preserves report reads/submission while requiring active Staff Mode for staff state mutations. */
public final class ActiveDutyReportStore implements ReportStore {
    private static final UUID CONSOLE_ACTOR_ID = new UUID(0L, 0L);

    private final ReportStore delegate;
    private final Predicate<UUID> activeDuty;

    public ActiveDutyReportStore(ReportStore delegate, Predicate<UUID> activeDuty) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.activeDuty = Objects.requireNonNull(activeDuty, "activeDuty");
    }

    @Override
    public ReportSubmissionResult submit(CreateReportRequest request) {
        return delegate.submit(request);
    }

    @Override
    public List<ReportSummary> list(ReportQueue queue, UUID actorId, int limit) {
        return delegate.list(queue, actorId, limit);
    }

    @Override
    public List<ReportSummary> listActiveForTarget(UUID targetId, int limit) {
        return delegate.listActiveForTarget(targetId, limit);
    }

    @Override
    public Optional<ReportDetails> details(UUID reportId) {
        return delegate.details(reportId);
    }

    @Override
    public ReportStateChangeResult changeState(ReportStateChangeRequest request) {
        Objects.requireNonNull(request, "request");
        if (!CONSOLE_ACTOR_ID.equals(request.actorId()) && !activeDuty.test(request.actorId())) {
            return new ReportStateChangeResult.Rejected(
                    "ACTIVE_DUTY_REQUIRED",
                    "Enter Staff Mode before changing report state."
            );
        }
        return delegate.changeState(request);
    }

    @Override
    public ReportEvidencePurgeResult purgeExpiredEvidence(Instant now, int batchLimit) {
        return delegate.purgeExpiredEvidence(now, batchLimit);
    }
}
