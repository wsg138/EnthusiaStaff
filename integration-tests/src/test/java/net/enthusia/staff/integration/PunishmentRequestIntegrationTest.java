package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentProposal;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRequestIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-30T14:00:00Z");
    private static final Actor HELPER = actor("request-helper", StaffRank.HELPER);
    private static final Actor MOD = actor("request-mod", StaffRank.MOD);

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_punishment_requests_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void pendingRequestSurvivesRestartAndDuplicateSubmissionReplays() {
        PunishmentApprovalRequest initial = request(
                "restart",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestResult.Submitted submitted = runtime.punishmentRequestStore().submit(initial);
            assertFalse(submitted.replayed());
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            assertEquals(initial, store.find(initial.requestId()).orElseThrow());
            PunishmentRequestResult.Submitted replay = store.submit(initial);
            assertTrue(replay.replayed());
            assertEquals(initial.requestId(), replay.request().requestId());
        }
    }

    @Test
    void staleFenceCannotApproveAndCurrentFenceCommitsAtomically() throws SQLException {
        PunishmentApprovalRequest pending = request(
                "approval",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        Instant approvalTime = NOW.plus(Duration.ofHours(2));
        CaseId caseId = new CaseId("A000000000000001");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(pending);
            PunishmentApprovalLease first = acquire(store, pending, MOD, NOW);
            PunishmentApprovalLease current = acquire(store, pending, MOD, NOW.plusSeconds(1));
            assertTrue(current.fenceToken() > first.fenceToken());

            PunishmentRequestResult.Rejected stale = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.approve(first, MOD, caseId, approvalTime)
            );
            assertEquals("STALE_LEASE", stale.code());
            assertEquals(0, countCases(caseId));

            PunishmentRequestResult.Approved approved = assertInstanceOf(
                    PunishmentRequestResult.Approved.class,
                    store.approve(current, MOD, caseId, approvalTime)
            );
            assertEquals(caseId, approved.caseId());
            assertEquals(PunishmentRequestStatus.APPROVED, store.find(pending.requestId()).orElseThrow().status());
        }

        assertEquals(approvalTime, caseIssuedAt(caseId));
        assertEquals(approvalTime.plus(Duration.ofDays(7)), sanctionExpiration(caseId));
        assertEquals(0, leaseCount(pending.requestId()));
    }

    @Test
    void denialAndExpirationRemainDurableWithoutCreatingCases() throws SQLException {
        PunishmentApprovalRequest denied = request(
                "denial",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        PunishmentApprovalRequest expiring = request(
                "expiration",
                thirtyDayBan(),
                NOW.plus(Duration.ofHours(1))
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(denied);
            store.submit(expiring);
            PunishmentApprovalLease lease = acquire(store, denied, MOD, NOW);
            PunishmentRequestResult.Denied result = assertInstanceOf(
                    PunishmentRequestResult.Denied.class,
                    store.deny(lease, MOD, "Evidence did not support the requested result", NOW.plusSeconds(5))
            );
            assertFalse(result.replayed());
            assertEquals(1, store.expire(NOW.plus(Duration.ofHours(2))));
            assertEquals(PunishmentRequestStatus.DENIED, store.find(denied.requestId()).orElseThrow().status());
            assertEquals(PunishmentRequestStatus.EXPIRED, store.find(expiring.requestId()).orElseThrow().status());
        }

        assertEquals(0, countCasesForRequest(denied.requestId()));
        assertEquals(0, countCasesForRequest(expiring.requestId()));
        assertEquals(0, leaseCount(denied.requestId()));
        assertEquals(0, leaseCount(expiring.requestId()));
    }

    @Test
    void independentExactPunishmentFulfillsOnlyMatchingPendingRequest() {
        UUID target = identifier("external-target");
        String reason = "test.external";
        PunishmentApprovalRequest matching = request(
                "external-match",
                target,
                reason,
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        PunishmentApprovalRequest differentDuration = request(
                "external-different",
                target,
                reason,
                thirtyDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId caseId = new CaseId("A000000000000002");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore requests = runtime.punishmentRequestStore();
            requests.submit(matching);
            requests.submit(differentDuration);
            runtime.moderationStore().createPunishment(plan(
                    caseId,
                    target,
                    reason,
                    sevenDayBan(),
                    NOW.plus(Duration.ofHours(3))
            ));

            PunishmentApprovalRequest fulfilled = requests.find(matching.requestId()).orElseThrow();
            assertEquals(PunishmentRequestStatus.FULFILLED_EXTERNALLY, fulfilled.status());
            assertEquals(caseId, fulfilled.resultingCaseId());
            assertEquals(PunishmentRequestStatus.PENDING,
                    requests.find(differentDuration.requestId()).orElseThrow().status());
        }
    }

    private static PunishmentApprovalLease acquire(
            PunishmentRequestStore store,
            PunishmentApprovalRequest request,
            Actor owner,
            Instant now
    ) {
        return store.acquire(request.requestId(), owner.id(), now, now.plus(Duration.ofMinutes(2)))
                .orElseThrow();
    }

    private static PunishmentApprovalRequest request(
            String key,
            List<SanctionSpec> sanctions,
            Instant expiresAt
    ) {
        return request(key, identifier("target-" + key), "test." + key, sanctions, expiresAt);
    }

    private static PunishmentApprovalRequest request(
            String key,
            UUID target,
            String reason,
            List<SanctionSpec> sanctions,
            Instant expiresAt
    ) {
        UUID requestId = identifier("request-" + key);
        PunishmentStep step = new PunishmentStep(0, "Configured", sanctions);
        PunishmentProposal proposal = new PunishmentProposal(
                target,
                HELPER,
                reason,
                "test",
                "Test punishment request",
                "Evidence-backed punishment request",
                "v1",
                CaseVisibility.PUBLIC,
                StaffRank.MOD,
                new EscalationDecision(0, 0, 0, List.of(), step),
                sanctions
        );
        return PunishmentApprovalRequest.pending(
                requestId,
                new IdempotencyKey("punishment-request-integration:" + key),
                proposal,
                NOW,
                expiresAt
        );
    }

    private static PunishmentPlan plan(
            CaseId caseId,
            UUID target,
            String reason,
            List<SanctionSpec> sanctions,
            Instant issuedAt
    ) {
        PunishmentStep step = new PunishmentStep(0, "Configured", sanctions);
        return new PunishmentPlan(
                caseId,
                new IdempotencyKey("direct-punishment:" + caseId.value()),
                target,
                MOD,
                reason,
                "test",
                "Test direct punishment",
                "Independent direct punishment",
                "v1",
                CaseVisibility.PUBLIC,
                issuedAt,
                new EscalationDecision(0, 0, 0, List.of(), step),
                sanctions
        );
    }

    private static List<SanctionSpec> sevenDayBan() {
        return List.of(new SanctionSpec(
                SanctionType.NETWORK_BAN,
                SanctionLength.temporary(Duration.ofDays(7))
        ));
    }

    private static List<SanctionSpec> thirtyDayBan() {
        return List.of(new SanctionSpec(
                SanctionType.NETWORK_BAN,
                SanctionLength.temporary(Duration.ofDays(30))
        ));
    }

    private static Actor actor(String key, StaffRank rank) {
        return new Actor(identifier(key), rank.name(), rank);
    }

    private static UUID identifier(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static DatabaseConfig databaseConfig() {
        return new DatabaseConfig(
                DATABASE.getJdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword(),
                4,
                5_000
        );
    }

    private static int countCases(CaseId caseId) throws SQLException {
        return count("SELECT COUNT(*) FROM cases WHERE case_id = ?", caseId.value());
    }

    private static int countCasesForRequest(UUID requestId) throws SQLException {
        return count(
                "SELECT COUNT(*) FROM cases WHERE idempotency_key = ?",
                "punishment-request:" + requestId + ":approved"
        );
    }

    private static int leaseCount(UUID requestId) throws SQLException {
        return count(
                "SELECT COUNT(*) FROM operation_leases WHERE resource_key = ?",
                "punishment-request:" + requestId
        );
    }

    private static int count(String sql, String value) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static Instant caseIssuedAt(CaseId caseId) throws SQLException {
        return timestamp("SELECT issued_at FROM cases WHERE case_id = ?", caseId.value());
    }

    private static Instant sanctionExpiration(CaseId caseId) throws SQLException {
        return timestamp("SELECT expiration_at FROM sanctions WHERE case_id = ?", caseId.value());
    }

    private static Instant timestamp(String sql, String value) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                Timestamp timestamp = result.getTimestamp(1);
                return timestamp.toInstant();
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                DATABASE.getJdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword()
        );
    }

    @SuppressWarnings("unused")
    private static byte[] uuid(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
