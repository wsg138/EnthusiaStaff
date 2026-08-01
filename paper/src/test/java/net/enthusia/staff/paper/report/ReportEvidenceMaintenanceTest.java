package net.enthusia.staff.paper.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.domain.report.ReportSummary;
import org.junit.jupiter.api.Test;

final class ReportEvidenceMaintenanceTest {
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void retriesBacklogAfterOneMinuteThenReturnsToHourlyCadence() {
        MutableClock clock = new MutableClock(NOW);
        CountingReportStore store = new CountingReportStore();
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        ReportEvidenceMaintenance maintenance = new ReportEvidenceMaintenance(clock, () -> store, logger);

        maintenance.run();
        maintenance.run();
        assertEquals(1, store.purgeCalls);

        clock.advance(Duration.ofMinutes(1));
        maintenance.run();
        assertEquals(2, store.purgeCalls);

        clock.advance(Duration.ofMinutes(59));
        maintenance.run();
        assertEquals(2, store.purgeCalls);

        clock.advance(Duration.ofMinutes(1));
        maintenance.run();
        assertEquals(3, store.purgeCalls);
    }

    private static final class CountingReportStore implements ReportStore {
        private int purgeCalls;

        @Override
        public ReportSubmissionResult submit(CreateReportRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReportSummary> list(ReportQueue queue, UUID actorId, int limit) {
            return List.of();
        }

        @Override
        public Optional<ReportDetails> details(UUID reportId) {
            return Optional.empty();
        }

        @Override
        public ReportStateChangeResult changeState(ReportStateChangeRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int purgeExpiredEvidence(Instant now, int batchLimit) {
            purgeCalls++;
            return purgeCalls == 1 ? batchLimit : 0;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
