package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentProposal;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestService;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.EscalationEngine;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRequestIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-30T14:00:00Z");
    private static final String DATABASE_PASSWORD = UUID.randomUUID().toString();
    private static final Actor HELPER = actor("request-helper", StaffRank.HELPER);
    private static final Actor DEVELOPER = actor("request-developer", StaffRank.DEVELOPER);
    private static final Actor MOD = actor("request-mod", StaffRank.MOD);
    private static final Actor ADMIN = actor("request-admin", StaffRank.ADMIN);

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_punishment_requests_test")
            .withUsername("enthusia_test")
            .withPassword(DATABASE_PASSWORD);

    @Test
    void pendingRequestSurvivesRestartAndDuplicateSubmissionReplays() throws SQLException {
        PunishmentApprovalRequest initial = request(
                "restart",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestResult.Submitted submitted = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    runtime.punishmentRequestStore().submit(initial)
            );
            assertFalse(submitted.replayed());
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            assertEquals(initial, store.find(initial.requestId()).orElseThrow());
            PunishmentRequestResult.Submitted replay = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    store.submit(initial)
            );
            assertTrue(replay.replayed());
            assertEquals(initial.requestId(), replay.request().requestId());
        }

        assertEquals(1, eventCount(initial.requestId(), "SUBMITTED"));
    }

    @Test
    void conflictingSubmissionsAreRejectedWithoutReplacingFrozenProposal() {
        UUID target = identifier("conflict-target");
        PunishmentApprovalRequest initial = request(
                "conflict-initial",
                "punishment-request-integration:conflict-shared",
                target,
                "test.conflict",
                HELPER,
                StaffRank.MOD,
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        PunishmentApprovalRequest idempotencyConflict = request(
                "conflict-idempotency",
                initial.submissionKey().value(),
                target,
                "test.conflict.changed",
                HELPER,
                StaffRank.MOD,
                thirtyDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        PunishmentApprovalRequest duplicatePending = request(
                "conflict-duplicate",
                "punishment-request-integration:conflict-other",
                target,
                "test.conflict",
                HELPER,
                StaffRank.MOD,
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            assertInstanceOf(PunishmentRequestResult.Submitted.class, store.submit(initial));

            PunishmentRequestResult.Rejected keyConflict = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.submit(idempotencyConflict)
            );
            assertEquals("IDEMPOTENCY_CONFLICT", keyConflict.code());

            PunishmentRequestResult.Rejected duplicate = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.submit(duplicatePending)
            );
            assertEquals("DUPLICATE_PENDING", duplicate.code());
            assertEquals(initial, store.find(initial.requestId()).orElseThrow());
            assertFalse(store.find(idempotencyConflict.requestId()).isPresent());
            assertFalse(store.find(duplicatePending.requestId()).isPresent());
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
            PunishmentApprovalLease first = acquire(store, pending, MOD, approvalTime.minusSeconds(2));
            PunishmentApprovalLease current = acquire(store, pending, MOD, approvalTime.minusSeconds(1));
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
            assertFalse(approved.replayed());
            assertEquals(caseId, approved.caseId());

            PunishmentRequestResult.Approved replay = assertInstanceOf(
                    PunishmentRequestResult.Approved.class,
                    store.approve(current, MOD, new CaseId("A000000000000099"), approvalTime.plusSeconds(1))
            );
            assertTrue(replay.replayed());
            assertEquals(caseId, replay.caseId());
            assertEquals(PunishmentRequestStatus.APPROVED, store.find(pending.requestId()).orElseThrow().status());
        }

        assertEquals(approvalTime, caseIssuedAt(caseId));
        assertEquals(approvalTime.plus(Duration.ofDays(7)), sanctionExpiration(caseId));
        assertEquals(0, leaseCount(pending.requestId()));
        assertEquals(1, eventCount(pending.requestId(), "APPROVED"));
    }

    @Test
    void lostLeaseFailsClosedWithoutCreatingCase() throws SQLException {
        PunishmentApprovalRequest pending = request(
                "lease-loss",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId caseId = new CaseId("A000000000000003");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(pending);
            PunishmentApprovalLease lease = acquire(store, pending, MOD, NOW);
            deleteLease(pending.requestId());

            PunishmentRequestResult.Rejected rejected = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.approve(lease, MOD, caseId, NOW.plusSeconds(10))
            );
            assertEquals("STALE_LEASE", rejected.code());
            assertEquals(PunishmentRequestStatus.PENDING, store.find(pending.requestId()).orElseThrow().status());
        }

        assertEquals(0, countCases(caseId));
        assertEquals(0, eventCount(pending.requestId(), "APPROVED"));
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

            PunishmentRequestResult.Denied replay = assertInstanceOf(
                    PunishmentRequestResult.Denied.class,
                    store.deny(lease, MOD, "Repeated denial", NOW.plusSeconds(6))
            );
            assertTrue(replay.replayed());
            assertEquals(1, store.expire(NOW.plus(Duration.ofHours(2))));
            assertEquals(PunishmentRequestStatus.DENIED, store.find(denied.requestId()).orElseThrow().status());
            assertEquals(PunishmentRequestStatus.EXPIRED, store.find(expiring.requestId()).orElseThrow().status());
        }

        assertEquals(0, countCasesForRequest(denied.requestId()));
        assertEquals(0, countCasesForRequest(expiring.requestId()));
        assertEquals(0, leaseCount(denied.requestId()));
        assertEquals(0, leaseCount(expiring.requestId()));
        assertEquals(1, eventCount(denied.requestId(), "DENIED"));
        assertEquals(1, eventCount(expiring.requestId(), "EXPIRED"));
    }

    @Test
    void independentExactPunishmentFulfillsOnlyMatchingPendingRequest() throws SQLException {
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
            assertEquals(
                    PunishmentRequestStatus.PENDING,
                    requests.find(differentDuration.requestId()).orElseThrow().status()
            );
        }

        assertEquals(1, eventCount(matching.requestId(), "FULFILLED_EXTERNALLY"));
        assertEquals(0, eventCount(differentDuration.requestId(), "FULFILLED_EXTERNALLY"));
    }

    @Test
    void helperDeveloperAndSelfApprovalRestrictionsUseDurableStore() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ServiceFixture permanent = serviceFixture(
                    runtime,
                    "test.authority.permanent",
                    StaffRank.MOD,
                    SanctionLength.permanent()
            );
            PunishmentRequestResult.Submitted helperSubmission = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    permanent.requests().submit(
                            serviceRequest("helper-permanent", HELPER, "test.authority.permanent"),
                            OperationalMode.ACTIVE
                    )
            );
            assertEquals(PunishmentRequestStatus.PENDING, helperSubmission.request().status());

            PunishmentRequestResult.Rejected helperDecision = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    permanent.requests().acquire(helperSubmission.request().requestId(), HELPER)
            );
            assertEquals("FORBIDDEN", helperDecision.code());

            PunishmentRequestResult.Rejected developerDecision = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    permanent.requests().acquire(helperSubmission.request().requestId(), DEVELOPER)
            );
            assertEquals("FORBIDDEN", developerDecision.code());

            PunishmentRequestResult.Submitted developerSubmission = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    permanent.requests().submit(
                            serviceRequest("developer-proposal", DEVELOPER, "test.authority.permanent"),
                            OperationalMode.ACTIVE
                    )
            );
            Actor promotedRequester = new Actor(DEVELOPER.id(), "Promoted requester", StaffRank.MOD);
            PunishmentRequestResult.Rejected selfApproval = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    permanent.requests().acquire(developerSubmission.request().requestId(), promotedRequester)
            );
            assertEquals("SELF_APPROVAL_FORBIDDEN", selfApproval.code());

            ServiceFixture temporary = serviceFixture(
                    runtime,
                    "test.authority.temporary",
                    StaffRank.MOD,
                    SanctionLength.temporary(Duration.ofHours(6))
            );
            PunishmentRequestResult.Rejected directHelper = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    temporary.requests().submit(
                            serviceRequest("helper-temporary", HELPER, "test.authority.temporary"),
                            OperationalMode.ACTIVE
                    )
            );
            assertEquals("APPROVAL_NOT_REQUIRED", directHelper.code());
        }
    }

    @Test
    void reasonRankMinimumIsEnforcedBeforeLeaseAcquisition() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ServiceFixture fixture = serviceFixture(
                    runtime,
                    "test.rank.admin",
                    StaffRank.ADMIN,
                    SanctionLength.permanent()
            );
            PunishmentRequestResult.Submitted submitted = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    fixture.requests().submit(
                            serviceRequest("admin-reason", DEVELOPER, "test.rank.admin"),
                            OperationalMode.ACTIVE
                    )
            );

            PunishmentRequestResult.Rejected modRejected = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    fixture.requests().acquire(submitted.request().requestId(), MOD)
            );
            assertEquals("APPROVER_RANK_REQUIRED", modRejected.code());
            assertInstanceOf(
                    PunishmentRequestResult.Leased.class,
                    fixture.requests().acquire(submitted.request().requestId(), ADMIN)
            );
        }
    }

    @Test
    void reviewedProposalRemainsFrozenAfterPolicyReplacement() throws SQLException {
        String reason = "test.frozen";
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ServiceFixture fixture = serviceFixture(
                    runtime,
                    reason,
                    StaffRank.MOD,
                    SanctionLength.temporary(Duration.ofDays(7))
            );
            PunishmentRequestResult.Submitted submitted = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    fixture.requests().submit(
                            serviceRequest("frozen", DEVELOPER, reason),
                            OperationalMode.ACTIVE
                    )
            );
            fixture.policies().replace(
                    "v2",
                    List.of(policy(reason, StaffRank.MOD, SanctionLength.temporary(Duration.ofDays(30))))
            );

            PunishmentApprovalLease lease = assertInstanceOf(
                    PunishmentRequestResult.Leased.class,
                    fixture.requests().acquire(submitted.request().requestId(), MOD)
            ).lease();
            PunishmentRequestResult.Approved approved = assertInstanceOf(
                    PunishmentRequestResult.Approved.class,
                    fixture.requests().approve(lease, MOD)
            );

            assertEquals("v1", approved.request().proposal().configurationVersion());
            assertEquals(NOW.plus(Duration.ofDays(7)), sanctionExpiration(approved.caseId()));
        }
    }

    @Test
    void approvalAndRequestResolutionRollbackTogetherAndRetrySafely() throws SQLException {
        PunishmentApprovalRequest pending = request(
                "atomic-rollback",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId caseId = new CaseId("A000000000000004");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(pending);
            PunishmentApprovalLease lease = acquire(store, pending, MOD, NOW);
            installApprovalFailureTrigger();
            try {
                assertThrows(
                        ModerationPersistenceException.class,
                        () -> store.approve(lease, MOD, caseId, NOW.plusSeconds(10))
                );
            } finally {
                dropApprovalFailureTrigger();
            }

            assertEquals(0, countCases(caseId));
            assertEquals(PunishmentRequestStatus.PENDING, store.find(pending.requestId()).orElseThrow().status());
            assertEquals(0, eventCount(pending.requestId(), "APPROVED"));
            assertEquals(1, leaseCount(pending.requestId()));

            PunishmentRequestResult.Approved recovered = assertInstanceOf(
                    PunishmentRequestResult.Approved.class,
                    store.approve(lease, MOD, caseId, NOW.plusSeconds(20))
            );
            assertFalse(recovered.replayed());
        }

        assertEquals(1, countCases(caseId));
        assertEquals(1, eventCount(pending.requestId(), "APPROVED"));
    }

    private static ServiceFixture serviceFixture(
            MariaDbRuntime runtime,
            String reasonId,
            StaffRank requiredRank,
            SanctionLength length
    ) {
        AtomicReasonPolicyRepository policies = new AtomicReasonPolicyRepository(
                "v1",
                List.of(policy(reasonId, requiredRank, length))
        );
        DefaultAuthorizationPolicy authorization = new DefaultAuthorizationPolicy();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PunishmentService punishments = new PunishmentService(
                clock,
                new SecureIdentifiers(new SecureRandom()),
                authorization,
                policies,
                runtime.moderationStore(),
                new EscalationEngine()
        );
        PunishmentRequestService requests = new PunishmentRequestService(
                clock,
                Duration.ofDays(7),
                Duration.ofMinutes(2),
                new SecureIdentifiers(new SecureRandom()),
                authorization,
                punishments,
                runtime.punishmentRequestStore()
        );
        return new ServiceFixture(requests, policies);
    }

    private static ReasonPolicy policy(String reasonId, StaffRank requiredRank, SanctionLength length) {
        PunishmentStep step = new PunishmentStep(
                0,
                "Configured",
                List.of(new SanctionSpec(SanctionType.NETWORK_BAN, length))
        );
        return new ReasonPolicy(
                reasonId,
                "test",
                "Test reason",
                10,
                true,
                List.of(step),
                List.of(),
                true,
                true,
                false,
                requiredRank,
                false,
                AltInheritanceMode.ACTIVE_SANCTIONS
        );
    }

    private static CreatePunishmentRequest serviceRequest(String key, Actor actor, String reasonId) {
        return new CreatePunishmentRequest(
                new IdempotencyKey("punishment-request-service-integration:" + key),
                identifier("service-target-" + key),
                actor,
                reasonId,
                "Evidence-backed punishment request",
                CaseVisibility.PUBLIC,
                List.of()
        );
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
        return request(
                key,
                "punishment-request-integration:" + key,
                target,
                reason,
                HELPER,
                StaffRank.MOD,
                sanctions,
                expiresAt
        );
    }

    private static PunishmentApprovalRequest request(
            String key,
            String submissionKey,
            UUID target,
            String reason,
            Actor requester,
            StaffRank requiredRank,
            List<SanctionSpec> sanctions,
            Instant expiresAt
    ) {
        UUID requestId = identifier("request-" + key);
        PunishmentStep step = new PunishmentStep(0, "Configured", sanctions);
        PunishmentProposal proposal = new PunishmentProposal(
                target,
                requester,
                reason,
                "test",
                "Test punishment request",
                "Evidence-backed punishment request",
                "v1",
                CaseVisibility.PUBLIC,
                requiredRank,
                new EscalationDecision(0, 0, 0, List.of(), step),
                sanctions
        );
        return PunishmentApprovalRequest.pending(
                requestId,
                new IdempotencyKey(submissionKey),
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
        return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
        return countByString("SELECT COUNT(*) FROM cases WHERE case_id = ?", caseId.value());
    }

    private static int countCasesForRequest(UUID requestId) throws SQLException {
        return countByString(
                "SELECT COUNT(*) FROM cases WHERE idempotency_key = ?",
                "punishment-request:" + requestId + ":approved"
        );
    }

    private static int leaseCount(UUID requestId) throws SQLException {
        return countByString(
                "SELECT COUNT(*) FROM operation_leases WHERE resource_key = ?",
                "punishment-request:" + requestId
        );
    }

    private static int eventCount(UUID requestId, String eventType) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM punishment_request_events WHERE request_id = ? AND event_type = ?")) {
            statement.setBytes(1, uuidBytes(requestId));
            statement.setString(2, eventType);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static int countByString(String sql, String value) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
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

    private static void deleteLease(UUID requestId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM operation_leases WHERE resource_key = ?")) {
            statement.setString(1, "punishment-request:" + requestId);
            statement.executeUpdate();
        }
    }

    private static void installApprovalFailureTrigger() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS test_fail_punishment_request_approval");
            statement.execute("""
                    CREATE TRIGGER test_fail_punishment_request_approval
                    BEFORE UPDATE ON punishment_requests
                    FOR EACH ROW
                    BEGIN
                        IF NEW.status = 'APPROVED' THEN
                            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced punishment request rollback';
                        END IF;
                    END
                    """);
        }
    }

    private static void dropApprovalFailureTrigger() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS test_fail_punishment_request_approval");
        }
    }

    private static byte[] uuidBytes(UUID value) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                DATABASE.getJdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword()
        );
    }

    private record ServiceFixture(
            PunishmentRequestService requests,
            AtomicReasonPolicyRepository policies
    ) {
    }
}
