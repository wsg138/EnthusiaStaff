package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.NOW;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.accepted;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.apply;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.requestWithEvidence;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.stateChange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.SQLException;
import java.util.UUID;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ReportRestartIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_report_restart_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void reportEvidenceAndRevisionRemainDurableAcrossRuntimeRestart() throws SQLException {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        insertPlayer(DATABASE, reporterId, "RestartReporter", NOW);
        insertPlayer(DATABASE, targetId, "RestartTarget", NOW);
        insertPlayer(DATABASE, actorId, "RestartStaff", NOW);

        UUID reportId;
        try (MariaDbRuntime firstRuntime = MariaDb.initialize(databaseConfig(DATABASE))) {
            ReportStore store = firstRuntime.reportStore();
            reportId = accepted(store.submit(requestWithEvidence(
                    reporterId,
                    targetId,
                    "report:restart",
                    NOW,
                    "restart durability"
            ))).reportId();
            ReportDetails created = store.details(reportId).orElseThrow();
            assertEquals(ReportState.OPEN, created.summary().state());
            assertFalse(created.publicChatSnapshots().isEmpty());
            assertFalse(created.privateMessageSnapshots().isEmpty());
            assertFalse(created.clientEvidenceSnapshots().isEmpty());
        }

        try (MariaDbRuntime secondRuntime = MariaDb.initialize(databaseConfig(DATABASE))) {
            ReportStore store = secondRuntime.reportStore();
            ReportDetails restored = store.details(reportId).orElseThrow();
            assertEquals(0L, restored.summary().revision());
            assertFalse(restored.publicChatSnapshots().isEmpty());
            assertFalse(restored.privateMessageSnapshots().isEmpty());
            assertFalse(restored.clientEvidenceSnapshots().isEmpty());
            assertEquals(1L, apply(store, stateChange(
                    reportId,
                    actorId,
                    ReportAction.CLAIM,
                    restored.summary().revision(),
                    "report-change:restart:claim"
            )).revision());
        }

        try (MariaDbRuntime thirdRuntime = MariaDb.initialize(databaseConfig(DATABASE))) {
            ReportDetails restored = thirdRuntime.reportStore().details(reportId).orElseThrow();
            assertEquals(ReportState.CLAIMED, restored.summary().state());
            assertEquals(1L, restored.summary().revision());
            assertEquals(actorId, restored.summary().assignedTo().orElseThrow());
            assertFalse(restored.publicChatSnapshots().isEmpty());
            assertFalse(restored.privateMessageSnapshots().isEmpty());
            assertFalse(restored.clientEvidenceSnapshots().isEmpty());
        }
    }
}
