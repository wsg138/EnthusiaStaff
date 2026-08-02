package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRequestLifecycleConcurrencyIntegrationTest extends PunishmentRequestMariaDbSupport {
    @Test
    void concurrentIdenticalSubmissionCommitsOneLifecycleOccurrence() throws Exception {
        PunishmentApprovalRequest request = request(
                "b2-concurrent-identical-submission",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        try (MariaDbRuntime firstRuntime = MariaDb.initialize(databaseConfig());
             MariaDbRuntime secondRuntime = MariaDb.initialize(databaseConfig());
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<PunishmentRequestResult.Submitted> first = executor.submit(() -> {
                start.await();
                return assertInstanceOf(
                        PunishmentRequestResult.Submitted.class,
                        firstRuntime.punishmentRequestStore().submit(request)
                );
            });
            Future<PunishmentRequestResult.Submitted> second = executor.submit(() -> {
                start.await();
                return assertInstanceOf(
                        PunishmentRequestResult.Submitted.class,
                        secondRuntime.punishmentRequestStore().submit(request)
                );
            });
            start.countDown();
            List<PunishmentRequestResult.Submitted> results = List.of(first.get(), second.get());
            assertEquals(1L, results.stream().filter(result -> !result.replayed()).count());
            assertEquals(1L, results.stream().filter(PunishmentRequestResult.Submitted::replayed).count());
        }
        assertEquals(1, requestCount(request.requestId()));
        assertEquals(1, eventCount(request.requestId(), "SUBMITTED"));
        assertEquals(3, alertCount(request.requestId(), "REQUEST_SUBMITTED"));
        assertEquals(1, directDeliveryCount(request.requestId()));
        assertEquals(1, discordCount(request.requestId(), "PUNISHMENT_REQUEST_SUBMITTED"));
    }

    @Test
    void concurrentClaimAttemptsCommitOneFencedOccurrence() throws Exception {
        PunishmentApprovalRequest request = request(
                "b2-concurrent-claim",
                thirtyDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        Instant claimAt = NOW.plusSeconds(10);
        try (MariaDbRuntime firstRuntime = MariaDb.initialize(databaseConfig());
             MariaDbRuntime secondRuntime = MariaDb.initialize(databaseConfig());
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            firstRuntime.punishmentRequestStore().submit(request);
            CountDownLatch start = new CountDownLatch(1);
            Future<Optional<PunishmentApprovalLease>> first = executor.submit(() -> {
                start.await();
                return firstRuntime.punishmentRequestStore().acquire(
                        request.requestId(), MOD.id(), claimAt, claimAt.plus(Duration.ofMinutes(2)));
            });
            Future<Optional<PunishmentApprovalLease>> second = executor.submit(() -> {
                start.await();
                return secondRuntime.punishmentRequestStore().acquire(
                        request.requestId(), ADMIN.id(), claimAt, claimAt.plus(Duration.ofMinutes(2)));
            });
            start.countDown();
            List<Optional<PunishmentApprovalLease>> results = List.of(first.get(), second.get());
            assertEquals(1L, results.stream().filter(Optional::isPresent).count());
            assertEquals(1L, results.stream().filter(Optional::isEmpty).count());
        }
        assertEquals(1, eventCount(request.requestId(), "LEASE_ACQUIRED"));
        assertEquals(2, alertCount(request.requestId(), "REQUEST_CLAIMED"));
        assertEquals(1, discordCount(request.requestId(), "PUNISHMENT_REQUEST_CLAIMED"));
        assertEquals(1, leaseCount(request.requestId()));
    }

    @Test
    void existingCaseReplayDoesNotMaskFulfillmentFailure() throws Exception {
        UUID target = identifier("b2-replay-failure-target");
        String reason = "test.b2.replay.failure";
        CaseId caseId = new CaseId("A000000000000221");
        PunishmentApprovalRequest request = request(
                "b2-replay-failure-request",
                target,
                reason,
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentResult.Accepted original = runtime.moderationStore().createPunishment(plan(
                    caseId, target, reason, sevenDayBan(), NOW.plusSeconds(10)));
            assertTrue(!original.replayed());
            runtime.punishmentRequestStore().submit(request);
            installExternalAlertFailure();
            try {
                assertThrows(
                        ModerationPersistenceException.class,
                        () -> runtime.moderationStore().createPunishment(plan(
                                caseId, target, reason, sevenDayBan(), NOW.plusSeconds(20)))
                );
            } finally {
                dropExternalAlertFailure();
            }
            assertEquals(1, countCases(caseId));
            assertEquals(PunishmentRequestStatus.PENDING,
                    runtime.punishmentRequestStore().find(request.requestId()).orElseThrow().status());
            assertEquals(0, eventCount(request.requestId(), "FULFILLED_EXTERNALLY"));
            assertEquals(0, alertCount(request.requestId(), "REQUEST_EXTERNALLY_FULFILLED"));
            assertEquals(0, discordCount(
                    request.requestId(), "PUNISHMENT_REQUEST_FULFILLED_EXTERNALLY"));

            PunishmentResult.Accepted replay = runtime.moderationStore().createPunishment(plan(
                    caseId, target, reason, sevenDayBan(), NOW.plusSeconds(30)));
            assertTrue(replay.replayed());
            assertEquals(PunishmentRequestStatus.FULFILLED_EXTERNALLY,
                    runtime.punishmentRequestStore().find(request.requestId()).orElseThrow().status());
        }
    }

    @Test
    void externalPunishmentRacingApprovalProducesOneRequestTerminalTransition() throws Exception {
        UUID target = identifier("b2-external-approval-race-target");
        String reason = "test.b2.external.approval.race";
        PunishmentApprovalRequest request = request(
                "b2-external-approval-race",
                target,
                reason,
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId approvalCase = new CaseId("A000000000000222");
        CaseId externalCase = new CaseId("A000000000000223");
        Instant raceAt = NOW.plusSeconds(20);
        try (MariaDbRuntime firstRuntime = MariaDb.initialize(databaseConfig());
             MariaDbRuntime secondRuntime = MariaDb.initialize(databaseConfig());
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            PunishmentRequestStore requestStore = firstRuntime.punishmentRequestStore();
            requestStore.submit(request);
            PunishmentApprovalLease lease = acquire(requestStore, request, MOD, NOW.plusSeconds(10));
            CountDownLatch start = new CountDownLatch(1);
            Future<PunishmentRequestResult> approval = executor.submit(() -> {
                start.await();
                return firstRuntime.punishmentRequestStore().approve(
                        lease, MOD, approvalCase, raceAt);
            });
            Future<PunishmentResult.Accepted> external = executor.submit(() -> {
                start.await();
                return secondRuntime.moderationStore().createPunishment(plan(
                        externalCase, target, reason, sevenDayBan(), raceAt));
            });
            start.countDown();
            PunishmentRequestResult approvalResult = approval.get();
            external.get();
            PunishmentApprovalRequest resolved = requestStore.find(request.requestId()).orElseThrow();
            assertTrue(resolved.status() == PunishmentRequestStatus.APPROVED
                    || resolved.status() == PunishmentRequestStatus.FULFILLED_EXTERNALLY);
            if (resolved.status() == PunishmentRequestStatus.APPROVED) {
                assertInstanceOf(PunishmentRequestResult.Approved.class, approvalResult);
                assertEquals(1, eventCount(request.requestId(), "APPROVED"));
                assertEquals(0, eventCount(request.requestId(), "FULFILLED_EXTERNALLY"));
                assertEquals(2, alertCount(request.requestId(), "REQUEST_APPROVED"));
                assertEquals(1, discordCount(request.requestId(), "PUNISHMENT_REQUEST_APPROVED"));
            } else {
                assertInstanceOf(PunishmentRequestResult.Rejected.class, approvalResult);
                assertEquals(0, eventCount(request.requestId(), "APPROVED"));
                assertEquals(1, eventCount(request.requestId(), "FULFILLED_EXTERNALLY"));
                assertEquals(2, alertCount(
                        request.requestId(), "REQUEST_EXTERNALLY_FULFILLED"));
                assertEquals(1, discordCount(
                        request.requestId(), "PUNISHMENT_REQUEST_FULFILLED_EXTERNALLY"));
            }
            assertEquals(1, eventCount(request.requestId(), "APPROVED")
                    + eventCount(request.requestId(), "FULFILLED_EXTERNALLY"));
            assertEquals(0, leaseCount(request.requestId()));
            assertTrue(caseCountForTargetReason(target, reason) >= 1);
            assertTrue(caseCountForTargetReason(target, reason) <= 2);
        }
    }

    @Test
    void expirationRacingApprovalProducesOneRequestTerminalTransition() throws Exception {
        PunishmentApprovalRequest request = request(
                "b2-expiration-approval-race",
                sevenDayBan(),
                NOW.plusSeconds(30)
        );
        CaseId approvalCase = new CaseId("A000000000000224");
        try (MariaDbRuntime firstRuntime = MariaDb.initialize(databaseConfig());
             MariaDbRuntime secondRuntime = MariaDb.initialize(databaseConfig());
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            PunishmentRequestStore requestStore = firstRuntime.punishmentRequestStore();
            requestStore.submit(request);
            PunishmentApprovalLease lease = requestStore.acquire(
                    request.requestId(), MOD.id(), NOW.plusSeconds(5), NOW.plusSeconds(120))
                    .orElseThrow();
            CountDownLatch start = new CountDownLatch(1);
            Future<PunishmentRequestResult> approval = executor.submit(() -> {
                start.await();
                return firstRuntime.punishmentRequestStore().approve(
                        lease, MOD, approvalCase, NOW.plusSeconds(29));
            });
            Future<Integer> expiration = executor.submit(() -> {
                start.await();
                return secondRuntime.punishmentRequestStore().expire(NOW.plusSeconds(31), 100);
            });
            start.countDown();
            PunishmentRequestResult approvalResult = approval.get();
            int expired = expiration.get();
            PunishmentApprovalRequest resolved = requestStore.find(request.requestId()).orElseThrow();
            assertTrue(resolved.status() == PunishmentRequestStatus.APPROVED
                    || resolved.status() == PunishmentRequestStatus.EXPIRED);
            if (resolved.status() == PunishmentRequestStatus.APPROVED) {
                assertInstanceOf(PunishmentRequestResult.Approved.class, approvalResult);
                assertEquals(0, expired);
                assertEquals(1, countCases(approvalCase));
                assertEquals(1, eventCount(request.requestId(), "APPROVED"));
                assertEquals(0, eventCount(request.requestId(), "EXPIRED"));
            } else {
                assertInstanceOf(PunishmentRequestResult.Rejected.class, approvalResult);
                assertEquals(1, expired);
                assertEquals(0, countCases(approvalCase));
                assertEquals(0, eventCount(request.requestId(), "APPROVED"));
                assertEquals(1, eventCount(request.requestId(), "EXPIRED"));
            }
            assertEquals(1, eventCount(request.requestId(), "APPROVED")
                    + eventCount(request.requestId(), "EXPIRED"));
            assertEquals(0, leaseCount(request.requestId()));
        }
    }

    private static int requestCount(UUID requestId) throws SQLException {
        return countByUuid("SELECT COUNT(*) FROM punishment_requests WHERE request_id=?", requestId);
    }

    private static int alertCount(UUID requestId, String lifecycleEvent) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM staff_alerts
                     WHERE request_id=? AND lifecycle_event=?
                     """)) {
            statement.setBytes(1, uuidBytes(requestId));
            statement.setString(2, lifecycleEvent);
            return count(statement);
        }
    }

    private static int directDeliveryCount(UUID requestId) throws SQLException {
        return countByUuid("""
                SELECT COUNT(*) FROM staff_alert_deliveries d
                JOIN staff_alerts i ON i.alert_id=d.alert_id
                WHERE i.request_id=? AND i.audience='DIRECT_RECIPIENT'
                """, requestId);
    }

    private static int discordCount(UUID requestId, String eventType) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM discord_outbox
                     WHERE idempotency_key LIKE ? AND event_type=?
                     """)) {
            statement.setString(1, "punishment-request:" + requestId + ":%");
            statement.setString(2, eventType);
            return count(statement);
        }
    }

    private static int caseCountForTargetReason(UUID targetId, String reason) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM cases
                     WHERE target_id=? AND exact_reason_id=?
                     """)) {
            statement.setBytes(1, uuidBytes(targetId));
            statement.setString(2, reason);
            return count(statement);
        }
    }

    private static int countByUuid(String sql, UUID value) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) { // nosemgrep
            statement.setBytes(1, uuidBytes(value));
            return count(statement);
        }
    }

    private static int count(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static void installExternalAlertFailure() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS fail_b2_replay_external_alert");
            statement.execute("""
                    CREATE TRIGGER fail_b2_replay_external_alert
                    BEFORE INSERT ON staff_alerts FOR EACH ROW
                    BEGIN
                        IF NEW.lifecycle_event='REQUEST_EXTERNALLY_FULFILLED' THEN
                            SIGNAL SQLSTATE '45000'
                                SET MESSAGE_TEXT='forced replay fulfillment failure';
                        END IF;
                    END
                    """);
        }
    }

    private static void dropExternalAlertFailure() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS fail_b2_replay_external_alert");
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                DATABASE.getJdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword()
        );
    }

    private static byte[] uuidBytes(UUID value) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
}
