package net.enthusia.staff.integration;

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
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentProposal;
import net.enthusia.staff.domain.application.PunishmentRequestService;
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
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;

abstract class PunishmentRequestMariaDbSupport {
    protected static final Instant NOW = Instant.parse("2026-07-30T14:00:00Z");
    protected static final String EVENT_APPROVED = "APPROVED";
    protected static final String CODE_FORBIDDEN = "FORBIDDEN";
    protected static final Actor HELPER = actor("request-helper", StaffRank.HELPER);
    protected static final Actor DEVELOPER = actor("request-developer", StaffRank.DEVELOPER);
    protected static final Actor MOD = actor("request-mod", StaffRank.MOD);
    protected static final Actor ADMIN = actor("request-admin", StaffRank.ADMIN);

    private static final String DATABASE_PASSWORD = UUID.randomUUID().toString();

    @Container
    protected static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_punishment_requests_test")
            .withUsername("enthusia_test")
            .withPassword(DATABASE_PASSWORD);

    protected static ServiceFixture serviceFixture(
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

    protected static ReasonPolicy policy(
            String reasonId,
            StaffRank requiredRank,
            SanctionLength length
    ) {
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

    protected static CreatePunishmentRequest serviceRequest(
            String key,
            Actor actor,
            String reasonId
    ) {
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

    protected static PunishmentApprovalLease acquire(
            PunishmentRequestStore store,
            PunishmentApprovalRequest request,
            Actor owner,
            Instant now
    ) {
        return store.acquire(request.requestId(), owner.id(), now, now.plus(Duration.ofMinutes(2)))
                .orElseThrow();
    }

    protected static PunishmentApprovalRequest request(
            String key,
            List<SanctionSpec> sanctions,
            Instant expiresAt
    ) {
        return request(key, identifier("target-" + key), "test." + key, sanctions, expiresAt);
    }

    protected static PunishmentApprovalRequest request(
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
                DEVELOPER,
                StaffRank.MOD,
                sanctions,
                expiresAt
        );
    }

    protected static PunishmentApprovalRequest request(
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

    protected static PunishmentPlan plan(
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

    protected static List<SanctionSpec> sevenDayBan() {
        return List.of(new SanctionSpec(
                SanctionType.NETWORK_BAN,
                SanctionLength.temporary(Duration.ofDays(7))
        ));
    }

    protected static List<SanctionSpec> thirtyDayBan() {
        return List.of(new SanctionSpec(
                SanctionType.NETWORK_BAN,
                SanctionLength.temporary(Duration.ofDays(30))
        ));
    }

    protected static UUID identifier(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected static DatabaseConfig databaseConfig() {
        return new DatabaseConfig(
                DATABASE.getJdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword(),
                4,
                5_000
        );
    }

    protected static int countCases(CaseId caseId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM cases WHERE case_id = ?")) {
            statement.setString(1, caseId.value());
            return singleCount(statement);
        }
    }

    protected static int countCasesForRequest(UUID requestId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM cases WHERE idempotency_key = ?")) {
            statement.setString(1, "punishment-request:" + requestId + ":approved");
            return singleCount(statement);
        }
    }

    protected static int leaseCount(UUID requestId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM operation_leases WHERE resource_key = ?")) {
            statement.setString(1, "punishment-request:" + requestId);
            return singleCount(statement);
        }
    }

    protected static int eventCount(UUID requestId, String eventType) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM punishment_request_events WHERE request_id = ? AND event_type = ?")) {
            statement.setBytes(1, uuidBytes(requestId));
            statement.setString(2, eventType);
            return singleCount(statement);
        }
    }

    protected static Instant caseIssuedAt(CaseId caseId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT issued_at FROM cases WHERE case_id = ?")) {
            statement.setString(1, caseId.value());
            return singleTimestamp(statement);
        }
    }

    protected static Instant sanctionExpiration(CaseId caseId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT expiration_at FROM sanctions WHERE case_id = ?")) {
            statement.setString(1, caseId.value());
            return singleTimestamp(statement);
        }
    }

    protected static void deleteLease(UUID requestId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM operation_leases WHERE resource_key = ?")) {
            statement.setString(1, "punishment-request:" + requestId);
            statement.executeUpdate();
        }
    }

    protected static void installApprovalFailureTrigger() throws SQLException {
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

    protected static void dropApprovalFailureTrigger() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS test_fail_punishment_request_approval");
        }
    }

    protected record ServiceFixture(
            PunishmentRequestService requests,
            AtomicReasonPolicyRepository policies
    ) {
    }

    private static Actor actor(String key, StaffRank rank) {
        return new Actor(identifier(key), rank.name(), rank);
    }

    private static int singleCount(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static Instant singleTimestamp(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            Timestamp timestamp = result.getTimestamp(1);
            return timestamp.toInstant();
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
}
