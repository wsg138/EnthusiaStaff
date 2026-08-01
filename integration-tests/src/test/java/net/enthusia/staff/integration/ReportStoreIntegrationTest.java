package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ReportStoreIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String REASON_ID = "chat.abuse";
    private static final String SERVER_ID = "paper-report-test";
    private static final int CONCURRENT_OPERATION_COUNT = 2;

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_reports_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows() throws SQLException {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            seedPlayers(reporterId, targetId);
            ReportStore store = runtime.reportStore();
            CreateReportRequest first = request(reporterId, targetId, "report:first", NOW, "first");
            ReportSubmissionResult.Accepted accepted = assertInstanceOf(
                    ReportSubmissionResult.Accepted.class,
                    store.submit(first)
            );
            CreateReportRequest duplicate = request(
                    reporterId,
                    targetId,
                    "report:duplicate",
                    NOW.plusSeconds(60),
                    "second"
            );
            ReportSubmissionResult.Accepted merged = assertInstanceOf(
                    ReportSubmissionResult.Accepted.class,
                    store.submit(duplicate)
            );
            ReportSubmissionResult.Accepted replayed = assertInstanceOf(
                    ReportSubmissionResult.Accepted.class,
                    store.submit(duplicate)
            );

            assertEquals(accepted.reportId(), merged.reportId());
            assertTrue(merged.merged());
            assertTrue(replayed.merged());
            assertTrue(replayed.replayed());
            ReportDetails details = store.details(accepted.reportId()).orElseThrow();
            assertEquals(2, details.publicChatSnapshots().size());
            assertEquals(2, details.privateMessageSnapshots().size());
            assertTrue(details.publicChatSnapshots().getFirst().contains("Public evidence first"));
            assertTrue(details.publicChatSnapshots().getFirst().contains(NOW.toString()));
            assertTrue(details.privateMessageSnapshots().getFirst().contains("Private evidence first"));
            assertEquals(1L, reportCount(accepted.reportId()));
            assertEquals(1L, reportMessageCount(accepted.reportId()));
            assertEquals(1L, discordCreationCount(accepted.reportId()));
        }
    }

    @Test
    void concurrentIdenticalStateChangeReturnsOneCommitAndOneReplay() throws Exception {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE));
             ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_OPERATION_COUNT);
             Connection blocker = connection(DATABASE)) {
            seedPlayers(reporterId, targetId);
            insertPlayer(DATABASE, actorId, "ReportActor", NOW);
            ReportStore store = runtime.reportStore();
            UUID reportId = accepted(store.submit(
                    request(reporterId, targetId, "report:concurrent", NOW, "concurrent")
            )).reportId();
            ReportStateChangeRequest change = change(reportId, actorId, "report-change:concurrent");
            lockReport(blocker, reportId);
            CountDownLatch ready = new CountDownLatch(CONCURRENT_OPERATION_COUNT);
            CountDownLatch start = new CountDownLatch(1);
            Future<ReportStateChangeResult> first = executor.submit(
                    () -> changeWhenReleased(store, change, ready, start)
            );
            Future<ReportStateChangeResult> second = executor.submit(
                    () -> changeWhenReleased(store, change, ready, start)
            );

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            try {
                awaitBlockedReportQueries();
            } finally {
                blocker.rollback();
            }
            List<ReportStateChangeResult.Applied> outcomes = List.of(
                    assertInstanceOf(ReportStateChangeResult.Applied.class, first.get(20, TimeUnit.SECONDS)),
                    assertInstanceOf(ReportStateChangeResult.Applied.class, second.get(20, TimeUnit.SECONDS))
            );

            assertEquals(1, outcomes.stream().filter(result -> !result.replayed()).count());
            assertEquals(1, outcomes.stream().filter(ReportStateChangeResult.Applied::replayed).count());
            assertEquals(1L, reportEventCount(reportId));
            assertEquals(ReportState.CLAIMED, store.details(reportId).orElseThrow().summary().state());
        }
    }

    @Test
    void reusedStateChangeKeyCannotReplayADifferentAction() throws SQLException {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            seedPlayers(reporterId, targetId);
            insertPlayer(DATABASE, actorId, "ConflictActor", NOW);
            ReportStore store = runtime.reportStore();
            UUID reportId = accepted(store.submit(
                    request(reporterId, targetId, "report:conflict", NOW, "conflict")
            )).reportId();
            String key = "report-change:conflict";
            ReportStateChangeResult.Applied applied = assertInstanceOf(
                    ReportStateChangeResult.Applied.class,
                    store.changeState(change(reportId, actorId, key))
            );
            ReportStateChangeResult.Rejected rejected = assertInstanceOf(
                    ReportStateChangeResult.Rejected.class,
                    store.changeState(new ReportStateChangeRequest(
                            reportId,
                            actorId,
                            ReportAction.AWAIT_REVIEW,
                            applied.revision(),
                            "Different action",
                            new IdempotencyKey(key),
                            NOW.plusSeconds(2)
                    ))
            );

            assertEquals("IDEMPOTENCY_CONFLICT", rejected.code());
            assertEquals(1L, reportEventCount(reportId));
            assertEquals(ReportState.CLAIMED, store.details(reportId).orElseThrow().summary().state());
        }
    }

    @Test
    void stateLifecycleEnforcesAssignmentRevisionAndQueues() throws SQLException {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID otherActorId = UUID.randomUUID();

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            seedPlayers(reporterId, targetId);
            insertPlayer(DATABASE, actorId, "LifecycleActor", NOW);
            insertPlayer(DATABASE, otherActorId, "OtherActor", NOW);
            ReportStore store = runtime.reportStore();
            UUID reportId = accepted(store.submit(
                    request(reporterId, targetId, "report:lifecycle", NOW, "lifecycle")
            )).reportId();

            assertQueueContains(store, ReportQueue.OPEN, actorId, reportId);
            ReportStateChangeResult.Applied claimed = apply(store, stateChange(
                    reportId, actorId, ReportAction.CLAIM, 0L, "report-change:lifecycle:claim"
            ));
            assertEquals(1L, claimed.revision());
            assertQueueContains(store, ReportQueue.CLAIMED_BY_ME, actorId, reportId);
            assertQueueExcludes(store, ReportQueue.CLAIMED_BY_ME, otherActorId, reportId);

            ReportStateChangeResult.Rejected notAssigned = reject(store, stateChange(
                    reportId, otherActorId, ReportAction.AWAIT_REVIEW, 1L, "report-change:lifecycle:wrong-actor"
            ));
            assertEquals("NOT_ASSIGNEE", notAssigned.code());
            ReportStateChangeResult.Applied awaiting = apply(store, stateChange(
                    reportId, actorId, ReportAction.AWAIT_REVIEW, 1L, "report-change:lifecycle:await"
            ));
            assertEquals(2L, awaiting.revision());
            assertQueueContains(store, ReportQueue.AWAITING_REVIEW, actorId, reportId);

            ReportStateChangeResult.Rejected stale = reject(store, stateChange(
                    reportId, actorId, ReportAction.CLOSE, 1L, "report-change:lifecycle:stale"
            ));
            assertEquals("STALE_REVISION", stale.code());
            ReportStateChangeResult.Applied closed = apply(store, stateChange(
                    reportId, actorId, ReportAction.CLOSE, 2L, "report-change:lifecycle:close"
            ));
            assertEquals(ReportState.CLOSED, closed.state());
            assertQueueContains(store, ReportQueue.RECENTLY_CLOSED, actorId, reportId);
        }
    }

    @Test
    void expiredEvidenceIsPhysicallyPurgedInBoundedBatches() throws SQLException {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            seedPlayers(reporterId, targetId);
            ReportStore store = runtime.reportStore();
            Instant expiredAt = NOW.minus(Duration.ofDays(9));
            UUID reportId = accepted(store.submit(requestWithEvidence(
                    reporterId,
                    targetId,
                    "report:retention",
                    expiredAt,
                    "retention"
            ))).reportId();

            assertEquals(1L, reportEvidenceCount("report_chat_snapshots", reportId));
            assertEquals(1L, reportEvidenceCount("report_private_message_snapshots", reportId));
            assertEquals(1L, reportEvidenceCount("report_client_evidence_snapshots", reportId));
            assertEquals(1L, clientEvidenceCount(targetId));

            int purged = store.purgeExpiredEvidence(NOW, 100);

            assertEquals(3, purged);
            assertEquals(0L, reportEvidenceCount("report_chat_snapshots", reportId));
            assertEquals(0L, reportEvidenceCount("report_private_message_snapshots", reportId));
            assertEquals(0L, reportEvidenceCount("report_client_evidence_snapshots", reportId));
            assertEquals(0L, clientEvidenceCount(targetId));
            assertEquals(0, store.purgeExpiredEvidence(NOW, 100));
        }
    }

    private static void seedPlayers(UUID reporterId, UUID targetId) throws SQLException {
        insertPlayer(DATABASE, reporterId, "Reporter" + reporterId.toString().substring(0, 6), NOW);
        insertPlayer(DATABASE, targetId, "Target" + targetId.toString().substring(0, 8), NOW);
    }

    private static CreateReportRequest request(
            UUID reporterId,
            UUID targetId,
            String idempotencyKey,
            Instant createdAt,
            String evidenceLabel
    ) {
        return new CreateReportRequest(
                new IdempotencyKey(idempotencyKey),
                reporterId,
                targetId,
                REASON_ID,
                "Report description " + evidenceLabel,
                SERVER_ID,
                Optional.of("minecraft:overworld"),
                Optional.of("1,64,1"),
                Optional.of("2,64,2"),
                createdAt,
                List.of(new CreateReportRequest.ChatContextMessage(
                        reporterId,
                        "Reporter",
                        "Public evidence " + evidenceLabel,
                        createdAt
                )),
                List.of(new CreateReportRequest.PrivateMessageContextMessage(
                        reporterId,
                        "Reporter",
                        targetId,
                        "Target",
                        "Private evidence " + evidenceLabel,
                        createdAt
                )),
                Optional.empty()
        );
    }

    private static CreateReportRequest requestWithEvidence(
            UUID reporterId,
            UUID targetId,
            String idempotencyKey,
            Instant createdAt,
            String evidenceLabel
    ) {
        CreateReportRequest base = request(reporterId, targetId, idempotencyKey, createdAt, evidenceLabel);
        return new CreateReportRequest(
                base.idempotencyKey(),
                base.reporterId(),
                base.targetId(),
                base.reasonId(),
                base.description(),
                base.serverId(),
                base.worldId(),
                base.reporterCoordinates(),
                base.targetCoordinates(),
                base.createdAt(),
                base.publicChatContext(),
                base.privateMessageContext(),
                Optional.of(clientEvidence(targetId, createdAt))
        );
    }

    private static ClientEvidenceSnapshot clientEvidence(UUID targetId, Instant capturedAt) {
        return new ClientEvidenceSnapshot(
                targetId,
                capturedAt,
                PlayerPlatform.JAVA,
                Optional.of(774),
                Optional.of("1.21.11"),
                Optional.of("vanilla"),
                IntegrationAvailability.AVAILABLE,
                Optional.of("5.10.0"),
                IntegrationAvailability.AVAILABLE,
                false,
                Optional.empty(),
                Optional.empty(),
                IntegrationAvailability.NOT_INSTALLED,
                IntegrationAvailability.NOT_INSTALLED,
                Optional.empty(),
                IntegrationAvailability.NOT_INSTALLED,
                Optional.empty()
        );
    }

    private static ReportStateChangeRequest change(UUID reportId, UUID actorId, String key) {
        return stateChange(reportId, actorId, ReportAction.CLAIM, 0L, key);
    }

    private static ReportStateChangeRequest stateChange(
            UUID reportId,
            UUID actorId,
            ReportAction action,
            long revision,
            String key
    ) {
        return new ReportStateChangeRequest(
                reportId,
                actorId,
                action,
                revision,
                "Investigating report",
                new IdempotencyKey(key),
                NOW.plusSeconds(revision + 1)
        );
    }

    private static ReportSubmissionResult.Accepted accepted(ReportSubmissionResult result) {
        return assertInstanceOf(ReportSubmissionResult.Accepted.class, result);
    }

    private static ReportStateChangeResult.Applied apply(
            ReportStore store,
            ReportStateChangeRequest request
    ) {
        return assertInstanceOf(ReportStateChangeResult.Applied.class, store.changeState(request));
    }

    private static ReportStateChangeResult.Rejected reject(
            ReportStore store,
            ReportStateChangeRequest request
    ) {
        return assertInstanceOf(ReportStateChangeResult.Rejected.class, store.changeState(request));
    }

    private static void assertQueueContains(
            ReportStore store,
            ReportQueue queue,
            UUID actorId,
            UUID reportId
    ) {
        assertTrue(store.list(queue, actorId, 100).stream()
                .anyMatch(report -> report.reportId().equals(reportId)));
    }

    private static void assertQueueExcludes(
            ReportStore store,
            ReportQueue queue,
            UUID actorId,
            UUID reportId
    ) {
        assertTrue(store.list(queue, actorId, 100).stream()
                .noneMatch(report -> report.reportId().equals(reportId)));
    }

    private static ReportStateChangeResult changeWhenReleased(
            ReportStore store,
            ReportStateChangeRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent report state change was not released");
        }
        return store.changeState(request);
    }

    private static void lockReport(Connection connection, UUID reportId) throws SQLException {
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT report_id FROM reports WHERE report_id = ? FOR UPDATE")) {
            statement.setBytes(1, uuidBytes(reportId));
            try (ResultSet result = statement.executeQuery()) {
                requireRow(result, "The report row disappeared before the concurrency test");
            }
        }
    }

    private static void awaitBlockedReportQueries() throws SQLException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (blockedReportQueryCount() >= CONCURRENT_OPERATION_COUNT) {
                return;
            }
            Thread.sleep(25);
        }
        throw new IllegalStateException("Concurrent report transactions did not reach the row lock");
    }

    private static int blockedReportQueryCount() throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("SHOW FULL PROCESSLIST");
             ResultSet result = statement.executeQuery()) {
            int count = 0;
            while (result.next()) {
                String query = result.getString("Info");
                if (query != null && query.contains("SELECT target_id, state, assigned_to, revision FROM reports")) {
                    count++;
                }
            }
            return count;
        }
    }

    private static long reportCount(UUID reportId) throws SQLException {
        return uuidCount("SELECT COUNT(*) FROM reports WHERE report_id = ?", reportId);
    }

    private static long reportMessageCount(UUID reportId) throws SQLException {
        return uuidCount("SELECT COUNT(*) FROM report_messages WHERE report_id = ?", reportId);
    }

    private static long reportEventCount(UUID reportId) throws SQLException {
        return uuidCount("SELECT COUNT(*) FROM report_events WHERE report_id = ?", reportId);
    }

    private static long reportEvidenceCount(String table, UUID reportId) throws SQLException {
        return uuidCount("SELECT COUNT(*) FROM " + table + " WHERE report_id = ?", reportId);
    }

    private static long clientEvidenceCount(UUID playerId) throws SQLException {
        return uuidCount("SELECT COUNT(*) FROM client_evidence_snapshots WHERE player_id = ?", playerId);
    }

    private static long uuidCount(String sql, UUID value) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, uuidBytes(value));
            try (ResultSet result = statement.executeQuery()) {
                requireRow(result, "The count query returned no row");
                return result.getLong(1);
            }
        }
    }

    private static long discordCreationCount(UUID reportId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM discord_outbox WHERE idempotency_key = ?
                     """)) {
            statement.setString(1, "report:" + reportId + ":discord");
            try (ResultSet result = statement.executeQuery()) {
                requireRow(result, "The Discord count query returned no row");
                return result.getLong(1);
            }
        }
    }

    private static void requireRow(ResultSet result, String message) throws SQLException {
        if (!result.next()) {
            throw new SQLException(message);
        }
    }
}
