package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.NOW;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.accepted;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.apply;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.assertQueueContains;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.assertQueueExcludes;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.change;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.reject;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.request;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.requestWithEvidence;
import static net.enthusia.staff.integration.ReportIntegrationFixtures.stateChange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportMessage;
import net.enthusia.staff.domain.report.ReportMessagesPage;
import net.enthusia.staff.domain.report.ReportEvidencePurgeResult;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.JdbcReportStore;
import net.enthusia.staff.persistence.JdbcStaffBotReportIntentStore;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ReportStoreIntegrationTest {
    private static final int CONCURRENT_OPERATION_COUNT = 2;
    private static final String STAFFBOT_WORKER_A = "worker-a";
    private static final String STAFFBOT_DISCORD_MESSAGE_ID = "423456789012345678";
    private static final String STAFFBOT_WORKER_B = "worker-b";
    private static final String STAFFBOT_CREATED = "REPORT_CREATED";
    private static final String STAFFBOT_TEST_BODY = "synthetic";

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
            assertTrue(store.exists(accepted.reportId()));
            assertFalse(store.exists(UUID.randomUUID()));
            ReportMessagesPage messages = store.messages(accepted.reportId(), 1);
            assertEquals(1, messages.messages().size());
            assertEquals(ReportMessage.Origin.REPORTER_FOLLOWUP, messages.messages().getFirst().origin());
            assertEquals(reporterId, messages.messages().getFirst().actorId());
            assertEquals("Report description second", messages.messages().getFirst().body());
            assertFalse(messages.hasMore(), "An idempotent replay must not add duplicate messages");
            assertEquals(1L, discordCreationCount(accepted.reportId()));
            assertEquals(1L, staffBotIntentCount(accepted.reportId(), STAFFBOT_CREATED));
            assertEquals(1L, staffBotIntentCount(accepted.reportId(), "REPORT_MERGED"));
            assertEquals(2L, inertStaffBotIntentCount(accepted.reportId()));
            assertEquals(0L, armedStaffBotIntentCount(accepted.reportId()));
        }
    }

    @Test
    void staffBotIntentDeliveryIsInertByDefaultAndLeaseRecoveryRequiresReview() throws SQLException {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID reportId;
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            seedPlayers(reporterId, targetId);
            reportId = accepted(runtime.reportStore().submit(
                    request(reporterId, targetId, "report:intent-lease-" + reporterId,
                            NOW, STAFFBOT_TEST_BODY))).reportId();
        }
        try (HikariDataSource pool = MariaDb.open(databaseConfig(DATABASE))) {
            var intents = new JdbcStaffBotReportIntentStore(pool);
            assertTrue(intents.claimArmed(STAFFBOT_WORKER_A, 2, Duration.ofSeconds(30), NOW).isEmpty());
            assertEquals(1L, inertStaffBotIntentCount(reportId));
            armSyntheticIntent(reportId, NOW);

            var claimed = intents.claimArmed(STAFFBOT_WORKER_A, 2, Duration.ofSeconds(30), NOW);
            assertEquals(1, claimed.size());
            assertEquals(reportId, claimed.getFirst().reportId());
            assertEquals(STAFFBOT_CREATED, claimed.getFirst().eventType());
            assertEquals(1, claimed.getFirst().attemptCount());
            assertFalse(intents.delivered(claimed.getFirst().intentId(), STAFFBOT_WORKER_B,
                    NOW.plusSeconds(2), STAFFBOT_DISCORD_MESSAGE_ID), "Only the lease owner can acknowledge delivery");
            assertFalse(intents.delivered(claimed.getFirst().intentId(), STAFFBOT_WORKER_A,
                    NOW.plusSeconds(31), STAFFBOT_DISCORD_MESSAGE_ID), "An expired lease cannot acknowledge a send");
        }
        // The original worker has stopped. Reopen a fresh connection pool to
        // prove an abandoned lease cannot send twice after a real restart.
        try (HikariDataSource restartedPool = MariaDb.open(databaseConfig(DATABASE))) {
            var restarted = new JdbcStaffBotReportIntentStore(restartedPool);
            assertEquals(1, restarted.quarantineExpired(NOW.plusSeconds(31)));
            assertEquals(0, restarted.quarantineExpired(NOW.plusSeconds(35)));
            assertTrue(restarted.claimArmed(STAFFBOT_WORKER_B, 2, Duration.ofSeconds(30),
                    NOW.plusSeconds(40)).isEmpty(), "Ambiguous sends must not auto-retry");
            assertEquals(1L, staffBotStateCount(reportId, "REVIEW_REQUIRED"));
        }
    }

    @Test
    void staffBotIntentAcknowledgementIsLeaseBoundAndTerminal() throws SQLException {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID reportId;
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            seedPlayers(reporterId, targetId);
            reportId = accepted(runtime.reportStore().submit(
                    request(reporterId, targetId, "report:intent-delivered-" + reporterId,
                            NOW, STAFFBOT_TEST_BODY))).reportId();
        }
        try (HikariDataSource pool = MariaDb.open(databaseConfig(DATABASE))) {
            var intents = new JdbcStaffBotReportIntentStore(pool);
            armSyntheticIntent(reportId, NOW);
            var claim = intents.claimArmed(STAFFBOT_WORKER_A, 1, Duration.ofMinutes(1), NOW)
                    .getFirst();
            assertThrows(IllegalArgumentException.class, () ->
                    intents.delivered(claim.intentId(), STAFFBOT_WORKER_A,
                            NOW.plusSeconds(1), "not-a-message-id"));
            assertTrue(intents.delivered(claim.intentId(), STAFFBOT_WORKER_A,
                    NOW.plusSeconds(1), STAFFBOT_DISCORD_MESSAGE_ID));
            assertFalse(intents.delivered(claim.intentId(), STAFFBOT_WORKER_A,
                    NOW.plusSeconds(2), STAFFBOT_DISCORD_MESSAGE_ID));
            assertFalse(intents.reviewRequired(claim.intentId(), STAFFBOT_WORKER_A));
            assertEquals(0, intents.quarantineExpired(NOW.plusSeconds(120)));
            assertEquals(1L, staffBotStateCount(reportId, "DELIVERED"));
            assertEquals(STAFFBOT_DISCORD_MESSAGE_ID, staffBotDeliveredMessageId(reportId));
        }
    }

    @Test
    void reporterFollowupsAndStaffActionsStayDistinctEvenForSameActor() throws SQLException {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            seedPlayers(reporterId, targetId);
            ReportStore store = runtime.reportStore();
            UUID reportId = accepted(store.submit(
                    request(reporterId, targetId, "report:message-origin-initial", NOW, "original")
            )).reportId();
            accepted(store.submit(request(reporterId, targetId,
                    "report:message-origin-merged", NOW.plusSeconds(60), "follow-up")));
            long revision = store.details(reportId).orElseThrow().summary().revision();
            apply(store, stateChange(reportId, reporterId, ReportAction.CLAIM,
                    revision, "report-change:same-actor"));

            ReportMessagesPage page = store.messages(reportId, 1);
            assertEquals(2, page.messages().size());
            assertEquals(ReportMessage.Origin.STAFF_ACTION, page.messages().getFirst().origin());
            assertEquals(ReportMessage.Origin.REPORTER_FOLLOWUP, page.messages().getLast().origin());
            assertEquals(reporterId, page.messages().getFirst().actorId());
            assertEquals(reporterId, page.messages().getLast().actorId());
            assertTrue(page.messages().getFirst().body().startsWith("CLAIM:"));
            assertEquals("Report description follow-up", page.messages().getLast().body());
            assertFalse(page.hasMore());
        }
    }

    @Test
    void legacyCaseMessagesRemainUnverifiedAndPagesAreBounded() throws SQLException {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            seedPlayers(reporterId, targetId);
            ReportStore store = runtime.reportStore();
            UUID reportId = accepted(store.submit(
                    request(reporterId, targetId, "report:legacy-messages", NOW, "original")
            )).reportId();
            try (Connection connection = connection(DATABASE);
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO report_messages(message_id, report_id, actor_id, body, created_at)
                         VALUES (?, ?, ?, ?, ?)
                         """)) {
                for (int index = 0; index < 7; index++) {
                    statement.setBytes(1, uuidBytes(UUID.randomUUID()));
                    statement.setBytes(2, uuidBytes(reportId));
                    statement.setBytes(3, uuidBytes(reporterId));
                    statement.setString(4, "legacy-" + index);
                    statement.setTimestamp(5, java.sql.Timestamp.from(NOW.plusSeconds(10 + index)));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            ReportMessagesPage first = store.messages(reportId, 1);
            ReportMessagesPage second = store.messages(reportId, 2);
            assertEquals(5, first.messages().size());
            assertTrue(first.hasMore());
            assertEquals(2, second.messages().size());
            assertFalse(second.hasMore());
            assertEquals("legacy-0", first.messages().getFirst().body());
            assertEquals("legacy-6", second.messages().getLast().body());
            assertTrue(first.messages().stream().allMatch(message ->
                    message.origin() == ReportMessage.Origin.UNKNOWN));
            assertThrows(IllegalArgumentException.class, () -> store.messages(reportId, 0));
            assertThrows(IllegalArgumentException.class, () -> store.messages(reportId, 10_001));
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

            assertEquals(1L, reportChatEvidenceCount(reportId));
            assertEquals(1L, reportPrivateMessageEvidenceCount(reportId));
            assertEquals(1L, reportClientEvidenceCount(reportId));
            assertEquals(1L, clientEvidenceCount(targetId));
            assertTrue(store.details(reportId).orElseThrow().clientEvidenceSnapshots().isEmpty());

            ReportEvidencePurgeResult purged = store.purgeExpiredEvidence(NOW, 100);

            assertEquals(new ReportEvidencePurgeResult(1, 1, 1), purged);
            assertEquals(0L, reportChatEvidenceCount(reportId));
            assertEquals(0L, reportPrivateMessageEvidenceCount(reportId));
            assertEquals(0L, reportClientEvidenceCount(reportId));
            assertEquals(0L, clientEvidenceCount(targetId));
            assertEquals(new ReportEvidencePurgeResult(0, 0, 0), store.purgeExpiredEvidence(NOW, 100));
        }
    }

    @Test
    void reusedSubmissionKeyCannotReplayDifferentEvidence() throws SQLException {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            seedPlayers(reporterId, targetId);
            ReportStore store = runtime.reportStore();
            String key = "report:submission-conflict";
            ReportSubmissionResult.Accepted accepted = accepted(store.submit(
                    request(reporterId, targetId, key, NOW, "original")
            ));

            ReportSubmissionResult.Rejected conflict = assertInstanceOf(
                    ReportSubmissionResult.Rejected.class,
                    store.submit(request(reporterId, targetId, key, NOW, "changed"))
            );

            assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
            assertEquals(1L, reportCount(accepted.reportId()));
            assertEquals(0L, reportMessageCount(accepted.reportId()));
            assertEquals(1L, reportChatEvidenceCount(accepted.reportId()));
        }
    }

    @Test
    void unexpectedErrorsRollBackReportCreationAndStateChange() throws SQLException {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        CreateReportRequest request = request(
                reporterId,
                targetId,
                "report:rollback-error",
                NOW,
                "rollback"
        );

        try (MariaDbRuntime migrationRuntime = MariaDb.initialize(databaseConfig(DATABASE))) {
            assertTrue(migrationRuntime.reportStore() != null);
            seedPlayers(reporterId, targetId);
            insertPlayer(DATABASE, actorId, "RollbackActor", NOW);
        }
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig(DATABASE))) {
            ReportStore broken = new JdbcReportStore(dataSource, failingJson());
            assertThrows(AssertionError.class, () -> broken.submit(request));
        }
        assertEquals(0L, reportIdempotencyCount(request.idempotencyKey().value()));
        assertEquals(0L, reportSubmissionKeyCount(request.idempotencyKey().value()));

        UUID reportId;
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            reportId = accepted(runtime.reportStore().submit(request)).reportId();
        }
        ReportStateChangeRequest change = change(reportId, actorId, "report-change:rollback-error");
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig(DATABASE))) {
            ReportStore broken = new JdbcReportStore(dataSource, failingJson());
            assertThrows(AssertionError.class, () -> broken.changeState(change));
        }
        assertEquals(ReportState.OPEN.name(), reportState(reportId));
        assertEquals(0L, reportEventCount(reportId));
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            ReportStateChangeResult.Applied applied = apply(runtime.reportStore(), change);
            assertEquals(ReportState.CLAIMED, applied.state());
        }
    }

    private static void seedPlayers(UUID reporterId, UUID targetId) throws SQLException {
        insertPlayer(DATABASE, reporterId, "Reporter" + reporterId.toString().substring(0, 6), NOW);
        insertPlayer(DATABASE, targetId, "Target" + targetId.toString().substring(0, 8), NOW);
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
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM reports WHERE report_id = ?")) {
            return uuidCount(statement, reportId);
        }
    }

    private static long reportMessageCount(UUID reportId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM report_messages WHERE report_id = ?")) {
            return uuidCount(statement, reportId);
        }
    }

    private static long reportEventCount(UUID reportId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM report_events WHERE report_id = ?")) {
            return uuidCount(statement, reportId);
        }
    }

    private static long reportChatEvidenceCount(UUID reportId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM report_chat_snapshots WHERE report_id = ?")) {
            return uuidCount(statement, reportId);
        }
    }

    private static long reportPrivateMessageEvidenceCount(UUID reportId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM report_private_message_snapshots WHERE report_id = ?")) {
            return uuidCount(statement, reportId);
        }
    }

    private static long reportClientEvidenceCount(UUID reportId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM report_client_evidence_snapshots WHERE report_id = ?")) {
            return uuidCount(statement, reportId);
        }
    }

    private static long clientEvidenceCount(UUID playerId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM client_evidence_snapshots WHERE player_id = ?")) {
            return uuidCount(statement, playerId);
        }
    }

    private static String reportState(UUID reportId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT state FROM reports WHERE report_id = ?")) {
            statement.setBytes(1, uuidBytes(reportId));
            try (ResultSet result = statement.executeQuery()) {
                requireRow(result, "The report state query returned no row");
                return result.getString(1);
            }
        }
    }

    private static long reportIdempotencyCount(String idempotencyKey) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM reports WHERE idempotency_key = ?")) {
            return stringCount(statement, idempotencyKey);
        }
    }

    private static long reportSubmissionKeyCount(String idempotencyKey) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM report_submission_keys WHERE idempotency_key = ?")) {
            return stringCount(statement, idempotencyKey);
        }
    }

    private static long stringCount(PreparedStatement statement, String value) throws SQLException {
        statement.setString(1, value);
        try (ResultSet result = statement.executeQuery()) {
            requireRow(result, "The string count query returned no row");
            return result.getLong(1);
        }
    }

    private static long uuidCount(PreparedStatement statement, UUID value) throws SQLException {
        statement.setBytes(1, uuidBytes(value));
        try (ResultSet result = statement.executeQuery()) {
            requireRow(result, "The count query returned no row");
            return result.getLong(1);
        }
    }

    /** Test-only synthetic activation; production exposes NO activation API. */
    private static void armSyntheticIntent(UUID reportId, Instant when) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE staffbot_report_notification_intents
                     SET state = 'ARMED', available_at = ?
                     WHERE report_id = ? AND state = 'INERT'
                     """)) {
            update.setTimestamp(1, java.sql.Timestamp.from(when));
            update.setBytes(2, uuidBytes(reportId));
            assertEquals(1, update.executeUpdate());
        }
    }

    private static String staffBotDeliveredMessageId(UUID reportId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement query = connection.prepareStatement("""
                     SELECT discord_message_id
                       FROM staffbot_report_notification_intents
                      WHERE report_id = ? AND state = 'DELIVERED'
                     """)) {
            query.setBytes(1, uuidBytes(reportId));
            try (ResultSet rows = query.executeQuery()) {
                requireRow(rows, "No confirmed StaffBot Discord receipt was recorded");
                return rows.getString(1);
            }
        }
    }

    private static long staffBotIntentCount(UUID reportId, String eventType) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM staffbot_report_notification_intents
                     WHERE report_id = ? AND event_type = ?
                     """)) {
            statement.setBytes(1, uuidBytes(reportId));
            statement.setString(2, eventType);
            try (ResultSet result = statement.executeQuery()) {
                requireRow(result, "The StaffBot event count query returned no row");
                return result.getLong(1);
            }
        }
    }

    private static long inertStaffBotIntentCount(UUID reportId) throws SQLException {
        return staffBotStateCount(reportId, "INERT");
    }

    private static long armedStaffBotIntentCount(UUID reportId) throws SQLException {
        return staffBotStateCount(reportId, "ARMED");
    }

    private static long staffBotStateCount(UUID reportId, String state) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM staffbot_report_notification_intents
                     WHERE report_id = ? AND state = ?
                     """)) {
            statement.setBytes(1, uuidBytes(reportId));
            statement.setString(2, state);
            try (ResultSet result = statement.executeQuery()) {
                requireRow(result, "The StaffBot state count query returned no row");
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

    private static ObjectMapper failingJson() {
        return new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new AssertionError("simulated process failure while storing report JSON");
            }
        };
    }
}
