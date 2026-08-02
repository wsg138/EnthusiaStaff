package net.enthusia.staff.paper.report;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.ReportEvidencePurgeResult;
import net.enthusia.staff.domain.report.ReportPolicy;
import net.enthusia.staff.domain.report.ReportPolicyRuntime;

public final class ReportEvidenceMaintenance implements Runnable {
    private static final Duration NORMAL_INTERVAL = Duration.ofHours(1);
    private static final Duration BACKLOG_INTERVAL = Duration.ofMinutes(1);
    private static final Duration FAILURE_INTERVAL = Duration.ofMinutes(5);

    private final Clock clock;
    private final Supplier<ReportStore> reports;
    private final Supplier<ReportPolicy> policy;
    private final Logger logger;
    private final AtomicReference<Instant> nextRun = new AtomicReference<>(Instant.EPOCH);

    public ReportEvidenceMaintenance(Clock clock, Supplier<ReportStore> reports, Logger logger) {
        this(clock, reports, ReportPolicyRuntime::current, logger);
    }

    public ReportEvidenceMaintenance(
            Clock clock,
            Supplier<ReportStore> reports,
            Supplier<ReportPolicy> policy,
            Logger logger
    ) {
        if (clock == null || reports == null || policy == null || logger == null) {
            throw new IllegalArgumentException("report evidence maintenance dependencies are required");
        }
        this.clock = clock;
        this.reports = reports;
        this.policy = policy;
        this.logger = logger;
    }

    @Override
    public void run() {
        Instant now = clock.instant();
        Instant scheduled = nextRun.get();
        if (now.isBefore(scheduled) || !nextRun.compareAndSet(scheduled, now.plus(NORMAL_INTERVAL))) {
            return;
        }
        try {
            int batchLimit = currentPolicy().evidencePurgeBatchLimit();
            ReportEvidencePurgeResult result = reportStore().purgeExpiredEvidence(now, batchLimit);
            nextRun.set(now.plus(result.hasBacklogAt(batchLimit) ? BACKLOG_INTERVAL : NORMAL_INTERVAL));
            if (result.total() > 0 && logger.isLoggable(Level.INFO)) {
                logger.info("Purged " + result.total() + " expired report evidence records");
            }
        } catch (RuntimeException exception) {
            nextRun.set(now.plus(FAILURE_INTERVAL));
            logger.log(Level.WARNING, "Expired report evidence cleanup failed; a bounded retry is scheduled", exception);
        }
    }

    private ReportStore reportStore() {
        ReportStore store = reports.get();
        if (store == null) {
            throw new IllegalStateException("report persistence is unavailable");
        }
        return store;
    }

    private ReportPolicy currentPolicy() {
        return Objects.requireNonNull(policy.get(), "active report policy");
    }
}
