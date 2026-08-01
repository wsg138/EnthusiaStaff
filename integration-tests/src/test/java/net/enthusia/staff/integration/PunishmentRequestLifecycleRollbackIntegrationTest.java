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
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRequestLifecycleRollbackIntegrationTest extends PunishmentRequestMariaDbSupport {
    @Test
    void submissionRollsBackForAlertDeliveryAndDiscordInsertionFailures() throws Exception {
        for (FailurePoint point : List.of(
                FailurePoint.SUBMISSION_ALERT,
                FailurePoint.DIRECT_DELIVERY,
                FailurePoint.SUBMISSION_DISCORD
        )) {
            PunishmentApprovalRequest request = request(
                    "b2-submit-rollback-" + point.name().toLowerCase(java.util.Locale.ROOT),
                    sevenDayBan(),
                    NOW.plus(Duration.ofDays(7))
            );
            try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
                install(point);
                try {
                    assertThrows(
                            ModerationPersistenceException.class,
                            () -> runtime.punishmentRequestStore().submit(request)
                    );
                } finally {
                    drop(point);
                }
                assertEquals(0, requestCount(request.requestId()));
                assertEquals(0, eventCount(request.requestId(), "SUBMITTED"));
                assertEquals(0, alertCount(request.requestId(), "REQUEST_SUBMITTED"));
                assertEquals(0, deliveryCount(request.requestId()));
                assertEquals(0, discordCount(request.requestId(), "PUNISHMENT_REQUEST_SUBMITTED"));

                PunishmentRequestResult.Submitted retry = assertInstanceOf(
                        PunishmentRequestResult.Submitted.class,
                        runtime.punishmentRequestStore().submit(request)
                );
                assertEquals(false, retry.replayed());
                assertEquals(1, requestCount(request.requestId()));
                assertEquals(3, alertCount(request.requestId(), "REQUEST_SUBMITTED"));
            }
        }
    }

    @Test
    void approvalNotificationFailureRollsBackCaseResolutionEventsAndLeaseRelease() throws Exception {
        PunishmentApprovalRequest request = request(
                "b2-approval-alert-rollback",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId caseId = new CaseId("A000000000000211");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(request);
            PunishmentApprovalLease lease = acquire(store, request, MOD, NOW.plusSeconds(10));
            install(FailurePoint.APPROVAL_ALERT);
            try {
                assertThrows(
                        ModerationPersistenceException.class,
                        () -> store.approve(lease, MOD, caseId, NOW.plusSeconds(20))
                );
            } finally {
                drop(FailurePoint.APPROVAL_ALERT);
            }
            assertEquals(0, countCases(caseId));
            assertEquals(PunishmentRequestStatus.PENDING,
                    store.find(request.requestId()).orElseThrow().status());
            assertEquals(0, eventCount(request.requestId(), EVENT_APPROVED));
            assertEquals(0, alertCount(request.requestId(), "REQUEST_APPROVED"));
            assertEquals(0, discordCount(request.requestId(), "PUNISHMENT_REQUEST_APPROVED"));
            assertEquals(1, leaseCount(request.requestId()));
            assertEquals("ACTIVE", reviewerIntentState(request.requestId()));

            assertInstanceOf(
                    PunishmentRequestResult.Approved.class,
                    store.approve(lease, MOD, caseId, NOW.plusSeconds(30))
            );
            assertEquals(1, countCases(caseId));
            assertEquals(PunishmentRequestStatus.APPROVED,
                    store.find(request.requestId()).orElseThrow().status());
        }
    }

    @Test
    void denialNotificationFailureRollsBackResolutionEventAndLeaseRelease() throws Exception {
        PunishmentApprovalRequest request = request(
                "b2-denial-alert-rollback",
                thirtyDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(request);
            PunishmentApprovalLease lease = acquire(store, request, MOD, NOW.plusSeconds(10));
            install(FailurePoint.DENIAL_ALERT);
            try {
                assertThrows(
                        ModerationPersistenceException.class,
                        () -> store.deny(
                                lease,
                                MOD,
                                "Rollback denial notification insertion",
                                NOW.plusSeconds(20)
                        )
                );
            } finally {
                drop(FailurePoint.DENIAL_ALERT);
            }
            assertEquals(PunishmentRequestStatus.PENDING,
                    store.find(request.requestId()).orElseThrow().status());
            assertEquals(0, eventCount(request.requestId(), "DENIED"));
            assertEquals(0, alertCount(request.requestId(), "REQUEST_DENIED"));
            assertEquals(0, discordCount(request.requestId(), "PUNISHMENT_REQUEST_DENIED"));
            assertEquals(1, leaseCount(request.requestId()));
            assertEquals("ACTIVE", reviewerIntentState(request.requestId()));

            assertInstanceOf(
                    PunishmentRequestResult.Denied.class,
                    store.deny(
                            lease,
                            MOD,
                            "The denial succeeds after the transaction can persist notifications",
                            NOW.plusSeconds(30)
                    )
            );
            assertEquals(PunishmentRequestStatus.DENIED,
                    store.find(request.requestId()).orElseThrow().status());
        }
    }

    @Test
    void externalFulfillmentNotificationFailureRollsBackPunishmentAndRequestTransition() throws Exception {
        UUID target = identifier("b2-external-rollback-target");
        String reason = "test.b2.external.rollback";
        PunishmentApprovalRequest request = request(
                "b2-external-rollback",
                target,
                reason,
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId caseId = new CaseId("A000000000000212");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            runtime.punishmentRequestStore().submit(request);
            install(FailurePoint.EXTERNAL_ALERT);
            try {
                assertThrows(
                        ModerationPersistenceException.class,
                        () -> runtime.moderationStore().createPunishment(plan(
                                caseId,
                                target,
                                reason,
                                sevenDayBan(),
                                NOW.plusSeconds(20)
                        ))
                );
            } finally {
                drop(FailurePoint.EXTERNAL_ALERT);
            }
            assertEquals(0, countCases(caseId));
            assertEquals(PunishmentRequestStatus.PENDING,
                    runtime.punishmentRequestStore().find(request.requestId()).orElseThrow().status());
            assertEquals(0, eventCount(request.requestId(), "FULFILLED_EXTERNALLY"));
            assertEquals(0, alertCount(request.requestId(), "REQUEST_EXTERNALLY_FULFILLED"));
            assertEquals(0, discordCount(
                    request.requestId(), "PUNISHMENT_REQUEST_FULFILLED_EXTERNALLY"));

            runtime.moderationStore().createPunishment(plan(
                    caseId,
                    target,
                    reason,
                    sevenDayBan(),
                    NOW.plusSeconds(30)
            ));
            assertEquals(1, countCases(caseId));
            assertEquals(PunishmentRequestStatus.FULFILLED_EXTERNALLY,
                    runtime.punishmentRequestStore().find(request.requestId()).orElseThrow().status());
        }
    }

    @Test
    void expirationNotificationFailureRollsBackTheBoundedTransition() throws Exception {
        PunishmentApprovalRequest request = request(
                "b2-expiration-alert-rollback",
                sevenDayBan(),
                NOW.plusSeconds(1)
        );
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(request);
            install(FailurePoint.EXPIRATION_ALERT);
            try {
                assertThrows(
                        ModerationPersistenceException.class,
                        () -> store.expire(NOW.plusSeconds(2), 100)
                );
            } finally {
                drop(FailurePoint.EXPIRATION_ALERT);
            }
            assertEquals(PunishmentRequestStatus.PENDING,
                    store.find(request.requestId()).orElseThrow().status());
            assertEquals(0, eventCount(request.requestId(), "EXPIRED"));
            assertEquals(0, alertCount(request.requestId(), "REQUEST_EXPIRED"));
            assertEquals(0, discordCount(request.requestId(), "PUNISHMENT_REQUEST_EXPIRED"));

            assertTrue(store.expire(NOW.plusSeconds(2), 100) >= 1);
            assertEquals(PunishmentRequestStatus.EXPIRED,
                    store.find(request.requestId()).orElseThrow().status());
        }
    }

    @Test
    void childReconciliationFailureRollsBackApprovalAndReviewerClosure() throws Exception {
        PunishmentApprovalRequest request = request(
                "b2-reconciliation-rollback",
                thirtyDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId caseId = new CaseId("A000000000000213");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(request);
            UUID reviewerAlert = materializeReviewerDelivery(request.requestId(), ADMIN.id());
            PunishmentApprovalLease lease = acquire(store, request, MOD, NOW.plusSeconds(10));
            install(FailurePoint.CANCELLATION);
            try {
                assertThrows(
                        ModerationPersistenceException.class,
                        () -> store.approve(lease, MOD, caseId, NOW.plusSeconds(20))
                );
            } finally {
                drop(FailurePoint.CANCELLATION);
            }
            assertEquals(0, countCases(caseId));
            assertEquals(PunishmentRequestStatus.PENDING,
                    store.find(request.requestId()).orElseThrow().status());
            assertEquals(0, eventCount(request.requestId(), EVENT_APPROVED));
            assertEquals(1, leaseCount(request.requestId()));
            assertEquals("ACTIVE", intentState(reviewerAlert));
            assertEquals("PENDING", deliveryState(reviewerAlert, ADMIN.id()));

            assertInstanceOf(
                    PunishmentRequestResult.Approved.class,
                    store.approve(lease, MOD, caseId, NOW.plusSeconds(30))
            );
            assertEquals("CLOSED", intentState(reviewerAlert));
            assertEquals("CANCELLED", deliveryState(reviewerAlert, ADMIN.id()));
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
                     WHERE request_id=? AND lifecycle_event='REQUEST_SUBMITTED'
                       AND audience='ELIGIBLE_REVIEWERS'
                     """)) {
            statement.setBytes(1, uuidBytes(reviewerId));
            statement.setBytes(2, uuidBytes(requestId));
            assertEquals(1, statement.executeUpdate());
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT alert_id FROM staff_alerts
                     WHERE request_id=? AND lifecycle_event='REQUEST_SUBMITTED'
                       AND audience='ELIGIBLE_REVIEWERS'
                     """)) {
            statement.setBytes(1, uuidBytes(requestId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return uuid(result.getBytes(1));
            }
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

    private static int deliveryCount(UUID requestId) throws SQLException {
        return countByUuid("""
                SELECT COUNT(*) FROM staff_alert_deliveries d
                JOIN staff_alerts i ON i.alert_id=d.alert_id
                WHERE i.request_id=?
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

    private static String reviewerIntentState(UUID requestId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT intent_state FROM staff_alerts
                     WHERE request_id=? AND lifecycle_event='REQUEST_SUBMITTED'
                       AND audience='ELIGIBLE_REVIEWERS'
                     """)) {
            statement.setBytes(1, uuidBytes(requestId));
            return singleString(statement);
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

    private static int countByUuid(String sql, UUID value) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
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

    private static String singleString(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static void install(FailurePoint point) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS " + point.triggerName());
            statement.execute(point.createSql());
        }
    }

    private static void drop(FailurePoint point) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS " + point.triggerName());
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

    private enum FailurePoint {
        SUBMISSION_ALERT(
                "fail_b2_submission_alert",
                """
                CREATE TRIGGER fail_b2_submission_alert
                BEFORE INSERT ON staff_alerts FOR EACH ROW
                BEGIN
                    IF NEW.lifecycle_event='REQUEST_SUBMITTED' AND NEW.request_id IS NOT NULL THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced submission alert failure';
                    END IF;
                END
                """
        ),
        DIRECT_DELIVERY(
                "fail_b2_direct_delivery",
                """
                CREATE TRIGGER fail_b2_direct_delivery
                BEFORE INSERT ON staff_alert_deliveries FOR EACH ROW
                BEGIN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced direct delivery failure';
                END
                """
        ),
        SUBMISSION_DISCORD(
                "fail_b2_submission_discord",
                """
                CREATE TRIGGER fail_b2_submission_discord
                BEFORE INSERT ON discord_outbox FOR EACH ROW
                BEGIN
                    IF NEW.event_type='PUNISHMENT_REQUEST_SUBMITTED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced submission Discord failure';
                    END IF;
                END
                """
        ),
        APPROVAL_ALERT(
                "fail_b2_approval_alert",
                """
                CREATE TRIGGER fail_b2_approval_alert
                BEFORE INSERT ON staff_alerts FOR EACH ROW
                BEGIN
                    IF NEW.lifecycle_event='REQUEST_APPROVED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced approval alert failure';
                    END IF;
                END
                """
        ),
        DENIAL_ALERT(
                "fail_b2_denial_alert",
                """
                CREATE TRIGGER fail_b2_denial_alert
                BEFORE INSERT ON staff_alerts FOR EACH ROW
                BEGIN
                    IF NEW.lifecycle_event='REQUEST_DENIED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced denial alert failure';
                    END IF;
                END
                """
        ),
        EXPIRATION_ALERT(
                "fail_b2_expiration_alert",
                """
                CREATE TRIGGER fail_b2_expiration_alert
                BEFORE INSERT ON staff_alerts FOR EACH ROW
                BEGIN
                    IF NEW.lifecycle_event='REQUEST_EXPIRED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced expiration alert failure';
                    END IF;
                END
                """
        ),
        EXTERNAL_ALERT(
                "fail_b2_external_alert",
                """
                CREATE TRIGGER fail_b2_external_alert
                BEFORE INSERT ON staff_alerts FOR EACH ROW
                BEGIN
                    IF NEW.lifecycle_event='REQUEST_EXTERNALLY_FULFILLED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced external alert failure';
                    END IF;
                END
                """
        ),
        CANCELLATION(
                "fail_b2_delivery_cancel",
                """
                CREATE TRIGGER fail_b2_delivery_cancel
                BEFORE UPDATE ON staff_alert_deliveries FOR EACH ROW
                BEGIN
                    IF NEW.state='CANCELLED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced delivery reconciliation failure';
                    END IF;
                END
                """
        );

        private final String triggerName;
        private final String createSql;

        FailurePoint(String triggerName, String createSql) {
            this.triggerName = triggerName;
            this.createSql = createSql;
        }

        String triggerName() {
            return triggerName;
        }

        String createSql() {
            return createSql;
        }
    }
}
