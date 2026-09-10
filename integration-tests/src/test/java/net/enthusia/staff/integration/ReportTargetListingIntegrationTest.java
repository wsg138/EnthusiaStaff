package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.NOW;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.accepted;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.apply;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.request;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.stateChange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportSummary;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ReportTargetListingIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_report_target_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void returnsOnlyActiveReportsForTheRequestedTargetInNewestFirstOrder() throws SQLException {
        UUID firstReporterId = UUID.randomUUID();
        UUID secondReporterId = UUID.randomUUID();
        UUID closedReporterId = UUID.randomUUID();
        UUID otherReporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID otherTargetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            insertPlayers(
                    firstReporterId,
                    secondReporterId,
                    closedReporterId,
                    otherReporterId,
                    targetId,
                    otherTargetId,
                    actorId
            );
            ReportStore store = runtime.reportStore();
            UUID olderReportId = accepted(store.submit(request(
                    firstReporterId, targetId, "report:target-list:older", NOW.minusSeconds(60), "older"
            ))).reportId();
            UUID newerReportId = accepted(store.submit(request(
                    secondReporterId, targetId, "report:target-list:newer", NOW, "newer"
            ))).reportId();
            UUID closedReportId = accepted(store.submit(request(
                    closedReporterId, targetId, "report:target-list:closed", NOW.plusSeconds(10), "closed"
            ))).reportId();
            accepted(store.submit(request(
                    otherReporterId, otherTargetId, "report:target-list:other", NOW.plusSeconds(20), "other"
            )));
            apply(store, stateChange(
                    closedReportId, actorId, ReportAction.CLAIM, 0L, "report:target-list:claim"
            ));
            apply(store, stateChange(
                    closedReportId, actorId, ReportAction.AWAIT_REVIEW, 1L, "report:target-list:review"
            ));
            apply(store, stateChange(
                    closedReportId, actorId, ReportAction.CLOSE, 2L, "report:target-list:close"
            ));

            List<ReportSummary> active = store.listActiveForTarget(targetId, 10);

            assertEquals(List.of(newerReportId, olderReportId), active.stream()
                    .map(ReportSummary::reportId)
                    .toList());
            assertEquals(List.of(newerReportId), store.listActiveForTarget(targetId, 1).stream()
                    .map(ReportSummary::reportId)
                    .toList());
            assertThrows(IllegalArgumentException.class, () -> store.listActiveForTarget(targetId, 0));
        }
    }

    private static void insertPlayers(UUID... playerIds) throws SQLException {
        int suffix = 0;
        for (UUID playerId : playerIds) {
            insertPlayer(DATABASE, playerId, "ReportTarget" + suffix++, NOW);
        }
    }
}
