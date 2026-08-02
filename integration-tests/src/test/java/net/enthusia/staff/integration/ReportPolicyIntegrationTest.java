package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.NOW;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.UUID;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.ReportPolicy;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ReportPolicyIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_report_policy_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void activePolicyControlsOpenReportAndQueryLimits() {
        UUID reporterId = UUID.randomUUID();
        UUID firstTarget = UUID.randomUUID();
        UUID secondTarget = UUID.randomUUID();
        ReportPolicy policy = new ReportPolicy(
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofMinutes(5),
                1,
                1,
                Duration.ofDays(2),
                Duration.ofDays(2),
                25
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE), () -> policy)) {
            insertPlayer(DATABASE, reporterId, "PolicyReporter", NOW);
            insertPlayer(DATABASE, firstTarget, "PolicyTargetOne", NOW);
            insertPlayer(DATABASE, secondTarget, "PolicyTargetTwo", NOW);
            ReportStore store = runtime.reportStore();

            assertInstanceOf(
                    ReportSubmissionResult.Accepted.class,
                    store.submit(request(reporterId, firstTarget, "policy:first", NOW, "first"))
            );
            ReportSubmissionResult.Rejected rejected = assertInstanceOf(
                    ReportSubmissionResult.Rejected.class,
                    store.submit(request(
                            reporterId,
                            secondTarget,
                            "policy:second",
                            NOW.plusSeconds(2),
                            "second"
                    ))
            );

            assertEquals("OPEN_REPORT_LIMIT", rejected.code());
            assertEquals(1, store.list(ReportQueue.OPEN, reporterId, 1).size());
            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.list(ReportQueue.OPEN, reporterId, 2)
            );
        }
    }
}
