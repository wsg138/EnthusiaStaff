package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRequestLifecycleNotificationsIntegrationTest extends PunishmentRequestMariaDbSupport {
    @Test
    void submissionAndRepeatableClaimsPersistExactlyOneOccurrenceEach() throws Exception {
        PunishmentApprovalRequest request = request(
                "b2-submit-claim",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            PunishmentRequestResult.Submitted submitted = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    store.submit(request)
            );
            assertFalse(submitted.replayed());
            assertEquals(3, alertCount(request.requestId(), "REQUEST_SUBMITTED", null));
            assertEquals(1, directDeliveryCount(request.requestId()));
            assertEquals(1, eventCount(request.requestId(), "SUBMITTED"));
            assertEquals(1, discordCount(request.requestId(), "PUNISHMENT_REQUEST_SUBMITTED"));
            assertReviewerIntent(request, "MOD", "PUBLIC", DEVELOPER.id());

            PunishmentRequestResult.Submitted replay = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    store.submit(request)
            );
            assertTrue(replay.replayed());
            assertEquals(3, alertCount(request.requestId(), "REQUEST_SUBMITTED", null));
            assertEquals(1, directDeliveryCount(request.requestId()));
            assertEquals(1, eventCount(request.requestId(), "SUBMITTED"));
            assertEquals(1, discordCount(request.requestId(), "PUNISHMENT_REQUEST_SUBMITTED"));

            Instant firstClaimAt = NOW.plusSeconds(10);
            PunishmentApprovalLease first = store.acquire(
                    request.requestId(),
                    MOD.id(),
                    firstClaimAt,
                    firstClaimAt.plus(Duration.ofMinutes(2))
            ).orElseThrow();
            PunishmentApprovalLease retry = store.acquire(
                    request.requestId(),
                    MOD.id(),
                    firstClaimAt.plusSeconds(10),
                    firstClaimAt.plus(Duration.ofMinutes(3))
            ).orElseThrow();
            assertEquals(first.fenceToken(), retry.fenceToken());
            assertEquals(first.leaseExpiresAt(), retry.leaseExpiresAt());
            assertTrue(store.acquire(
                    request.requestId(),
                    ADMIN.id(),
                    firstClaimAt.plusSeconds(20),
                    firstClaimAt.plus(Duration.ofMinutes(3))
            ).isEmpty());
            assertEquals(1, eventCount(request.requestId(), "LEASE_ACQUIRED"));
            assertEquals(2, alertCount(request.requestId(), "REQUEST_CLAIMED", null));
            assertEquals(1, discordCount(request.requestId(), "PUNISHMENT_REQUEST_CLAIMED"));

            Instant laterClaimAt = first.leaseExpiresAt().plusSeconds(1);
            PunishmentApprovalLease later = store.acquire(
                    request.requestId(),
                    ADMIN.id(),
                    laterClaimAt,
                    laterClaimAt.plus(Duration.ofMinutes(2))
            ).orElseThrow();
            assertTrue(later.fenceToken() > first.fenceToken());
            assertEquals(2, eventCount(request.requestId(), "LEASE_ACQUIRED"));
            assertEquals(4, alertCount(request.requestId(), "REQUEST_CLAIMED", null));
            assertEquals(2, discordCount(request.requestId(), "PUNISHMENT_REQUEST_CLAIMED"));
            assertEquals(3, directDeliveryCount(request.requestId()));
            assertEquals(1, claimActorCount(request.requestId(), MOD.id(), first.fenceToken()));
            assertEquals(1, claimActorCount(request.requestId(), ADMIN.id(), later.fenceToken()));
        }
    }

    @Test
    void approvalAndDenialCloseReviewerWorkAndCreateFinalOutcomes() throws Exception {
        PunishmentApprovalRequest approvedRequest = request(
                "b2-approval-notifications",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        PunishmentApprovalRequest deniedRequest = request(
                "b2-denial-notifications",
                thirtyDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId caseId = new CaseId("A000000000000201");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(approvedRequest);
            store.submit(deniedRequest);
            UUID approvedReviewerAlert = materializeReviewerDelivery(approvedRequest.requestId(), ADMIN.id());
            UUID deniedReviewerAlert = materializeReviewerDelivery(deniedRequest.requestId(), ADMIN.id());

            PunishmentApprovalLease approvalLease = acquire(store, approvedRequest, MOD, NOW.plusSeconds(10));
            PunishmentRequestResult.Approved approved = assertInstanceOf(
                    PunishmentRequestResult.Approved.class,
                    store.approve(approvalLease, MOD, caseId, NOW.plusSeconds(20))
            );
            assertFalse(approved.replayed());
            assertEquals(PunishmentRequestStatus.APPROVED, approved.request().status());
            assertEquals("CLOSED", intentState(approvedReviewerAlert));
            assertEquals("CANCELLED", deliveryState(approvedReviewerAlert, ADMIN.id()));
            assertEquals(2, alertCount(approvedRequest.requestId(), "REQUEST_APPROVED", null));
            assertEquals(1, discordCount(approvedRequest.requestId(), "PUNISHMENT_REQUEST_APPROVED"));
            assertEquals(1, eventCount(approvedRequest.requestId(), EVENT_APPROVED));
            assertEquals(0, leaseCount(approvedRequest.requestId()));
            assertEquals(1, countCases(caseId));

            PunishmentApprovalLease denialLease = acquire(store, deniedRequest, MOD, NOW.plusSeconds(30));
            PunishmentRequestResult.Denied denied = assertInstanceOf(
                    PunishmentRequestResult.Denied.class,
                    store.deny(
                            denialLease,
                            MOD,
                            "The available evidence did not support the requested punishment",
                            NOW.plusSeconds(40)
                    )
            );
            assertFalse(denied.replayed());
            assertEquals(PunishmentRequestStatus.DENIED, denied.request().status());
            assertEquals("CLOSED", intentState(deniedReviewerAlert));
            assertEquals("CANCELLED", deliveryState(deniedReviewerAlert, ADMIN.id()));
            assertEquals(2, alertCount(deniedRequest.requestId(), "REQUEST_DENIED", null));
            assertEquals(1, discordCount(deniedRequest.requestId(), "PUNISHMENT_REQUEST_DENIED"));
            assertEquals(1, eventCount(deniedRequest.requestId(), "DENIED"));
            assertEquals(0, leaseCount(deniedRequest.requestId()));
        }
    }

    @Test
    void directPunishmentFulfillsMatchingRequestWithFullLifecyclePersistence() throws Exception {
        UUID target = identifier("b2-external-target");
        String reason = "test.b2.external";
        PunishmentApprovalRequest matching = request(
                "b2-external-match",
                target,
                reason,
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        PunishmentApprovalRequest different = request(
                "b2-external-different",
                target,
                reason,
                thirtyDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId caseId = new CaseId("A000000000000202");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(matching);
            store.submit(different);
            UUID reviewerAlert = materializeReviewerDelivery(matching.requestId(), ADMIN.id());
            acquire(store, matching, MOD, NOW.plusSeconds(10));

            runtime.moderationStore().createPunishment(plan(
                    caseId,
                    target,
                    reason,
                    sevenDayBan(),
                    NOW.plusSeconds(20)
            ));

            PunishmentApprovalRequest fulfilled = store.find(matching.requestId()).orElseThrow();
            assertEquals(PunishmentRequestStatus.FULFILLED_EXTERNALLY, fulfilled.status());
            assertEquals(caseId, fulfilled.resultingCaseId());
            assertEquals(PunishmentRequestStatus.PENDING,
                    store.find(different.requestId()).orElseThrow().status());
            assertEquals("CLOSED", intentState(reviewerAlert));
            assertEquals("CANCELLED", deliveryState(reviewerAlert, ADMIN.id()));
            assertEquals(2, alertCount(
                    matching.requestId(), "REQUEST_EXTERNALLY_FULFILLED", null));
            assertEquals(1, discordCount(
                    matching.requestId(), "PUNISHMENT_REQUEST_FULFILLED_EXTERNALLY"));
            assertEquals(1, eventCount(matching.requestId(), "FULFILLED_EXTERNALLY"));
            assertEquals(0, leaseCount(matching.requestId()));
            assertEquals(0, eventCount(different.requestId(), "FULFILLED_EXTERNALLY"));
        }
    }

    @Test
    void readsStayReadOnlyAndExpirationUsesDeterministicBoundedBatches() throws Exception {
        List<PunishmentApprovalRequest> requests = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            requests.add(request(
                    "b2-expiration-batch-" + index,
                    sevenDayBan(),
                    NOW.plusSeconds(index + 1L)
            ));
        }
        Instant expirationRun = NOW.plusSeconds(10);
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            requests.forEach(store::submit);
            for (PunishmentApprovalRequest request : requests) {
                assertEquals(PunishmentRequestStatus.PENDING,
                        store.find(request.requestId()).orElseThrow().status());
            }
            assertTrue(store.pending(expirationRun, 10).stream()
                    .noneMatch(value -> requests.stream()
                            .anyMatch(request -> request.requestId().equals(value.requestId()))));
            assertEquals(2, store.expire(expirationRun, 2));
            assertEquals(1, requests.stream()
                    .filter(request -> store.find(request.requestId()).orElseThrow().status()
                            == PunishmentRequestStatus.PENDING)
                    .count());
            assertEquals(1, store.expire(expirationRun, 2));
            assertEquals(0, store.expire(expirationRun, 2));
            for (PunishmentApprovalRequest request : requests) {
                assertEquals(PunishmentRequestStatus.EXPIRED,
                        store.find(request.requestId()).orElseThrow().status());
                assertEquals(1, eventCount(request.requestId(), "EXPIRED"));
                assertEquals(2, alertCount(request.requestId(), "REQUEST_EXPIRED", null));
                assertEquals(1, discordCount(request.requestId(), "PUNISHMENT_REQUEST_EXPIRED"));
            }
        }
    }

    @Test
    void twoServersExpireEachRequestOnlyOnce() throws Exception {
        List<PunishmentApprovalRequest> requests = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            requests.add(request(
                    "b2-concurrent-expiration-" + index,
                    thirtyDayBan(),
                    NOW.plusSeconds(20L + index)
            ));
        }
        Instant expirationRun = NOW.plusSeconds(30);
        try (MariaDbRuntime firstRuntime = MariaDb.initialize(databaseConfig());
             MariaDbRuntime secondRuntime = MariaDb.initialize(databaseConfig());
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            requests.forEach(firstRuntime.punishmentRequestStore()::submit);
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return firstRuntime.punishmentRequestStore().expire(expirationRun, 2);
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return secondRuntime.punishmentRequestStore().expire(expirationRun, 2);
            });
            start.countDown();
            assertEquals(4, first.get() + second.get());
            for (PunishmentApprovalRequest request : requests) {
                assertEquals(PunishmentRequestStatus.EXPIRED,
                        firstRuntime.punishmentRequestStore()
                                .find(request.requestId()).orElseThrow().status());
                assertEquals(1, eventCount(request.requestId(), "EXPIRED"));
                assertEquals(1, discordCount(request.requestId(), "PUNISHMENT_REQUEST_EXPIRED"));
            }
        }
    }

    private static void assertReviewerIntent(
            PunishmentApprovalRequest request,
            String minimumRank,
            String visibility,
            UUID excludedRecipient
    ) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT minimum_rank, visibility, excluded_recipient_id
                     FROM staff_alerts
                     WHERE request_id = ? AND lifecycle_event = 'REQUEST_SUBMITTED'
                       AND audience = 'ELIGIBLE_REVIEWERS'
                     """)) {
            statement.setBytes(1, uuidBytes(request.requestId()));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(minimumRank, result.getString("minimum_rank"));
                assertEquals(visibility, result.getString("visibility"));
                assertEquals(excludedRecipient,
                        uuid(result.getBytes("excluded_recipient_id")));
            }
        }
    }

    private static UUID materializeReviewerDelivery(UUID requestId, UUID reviewerId)
            throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO staff_alert_deliveries(
                         alert_id, recipient_id, state, attempt_count,
                         available_at, created_at, updated_at
                     )
                     SELECT alert_id, ?, 'PENDING', 0, created_at, created_at, created_at
                     FROM staff_alerts
                     WHERE request_id = ? AND lifecycle_event = 'REQUEST_SUBMITTED'
                       AND audience = 'ELIGIBLE_REVIEWERS'
                     """)) {
            statement.setBytes(1, uuidBytes(reviewerId));
            statement.setBytes(2, uuidBytes(requestId));
            assertEquals(1, statement.executeUpdate());
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT alert_id FROM staff_alerts
                     WHERE request_id = ? AND lifecycle_event = 'REQUEST_SUBMITTED'
                       AND audience = 'ELIGIBLE_REVIEWERS'
                     """)) {
            statement.setBytes(1, uuidBytes(requestId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return uuid(result.getBytes(1));
            }
        }
    }

    private static int alertCount(UUID requestId, String lifecycleEvent, String audience)
            throws SQLException {
        String sql = audience == null
                ? "SELECT COUNT(*) FROM staff_alerts WHERE request_id=? AND lifecycle_event=?"
                : "SELECT COUNT(*) FROM staff_alerts WHERE request_id=? AND lifecycle_event=? AND audience=?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, uuidBytes(requestId));
            statement.setString(2, lifecycleEvent);
            if (audience != null) {
                statement.setString(3, audience);
            }
            return count(statement);
        }
    }

    private static int directDeliveryCount(UUID requestId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM staff_alert_deliveries d
                     JOIN staff_alerts i ON i.alert_id=d.alert_id
                     WHERE i.request_id=? AND i.audience='DIRECT_RECIPIENT'
                     """)) {
            statement.setBytes(1, uuidBytes(requestId));
            return count(statement);
        }
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

    private static int claimActorCount(UUID requestId, UUID actorId, long fenceToken)
            throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM staff_alerts
                     WHERE request_id=? AND lifecycle_event='REQUEST_CLAIMED'
                       AND lifecycle_actor_id=?
                       AND occurrence_key=?
                     """)) {
            statement.setBytes(1, uuidBytes(requestId));
            statement.setBytes(2, uuidBytes(actorId));
            statement.setString(3, "operation-lease-fence:" + fenceToken);
            return count(statement);
        }
    }

    private static String intentState(UUID alertId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT intent_state FROM staff_alerts WHERE alert_id=?")) {
            statement.setBytes(1, uuidBytes(alertId));
            return singleString(statement);
        }
    }

    private static String deliveryState(UUID alertId, UUID recipientId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state FROM staff_alert_deliveries
                     WHERE alert_id=? AND recipient_id=?
                     """)) {
            statement.setBytes(1, uuidBytes(alertId));
            statement.setBytes(2, uuidBytes(recipientId));
            return singleString(statement);
        }
    }

    private static int count(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static String singleString(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getString(1);
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

    private static UUID uuid(byte[] value) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
