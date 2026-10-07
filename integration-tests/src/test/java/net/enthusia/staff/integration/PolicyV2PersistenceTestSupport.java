package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.DecayPolicy;
import net.enthusia.staff.domain.policyv2.HistoryAssessment;
import net.enthusia.staff.domain.policyv2.HistoryPolicy;
import net.enthusia.staff.domain.policyv2.HistoryWindow;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicyResolution;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.ResolutionRule;
import net.enthusia.staff.domain.policyv2.RuleCondition;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.persistence.MariaDb;
import org.testcontainers.containers.MariaDBContainer;

final class PolicyV2PersistenceTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-06T22:30:00Z");
    private static final String SPAM = "chat.spam";
    private static final String HARASSMENT = "chat.harassment";

    private PolicyV2PersistenceTestSupport() {
    }

    static PolicyV2Store.CreateCase createRequest(Fixture fixture, String operationKey) {
        PolicySnapshot snapshot = policy();
        IncidentFinding finding = finding(SPAM);
        ResolutionRule rule = snapshot.offenses().get(0).rules().get(0);
        PolicyResolution resolution = new PolicyResolution(
                snapshot.version(),
                finding.offenseId(),
                rule.id(),
                rule.action(),
                rule.remedies(),
                HistoryAssessment.empty()
        );
        return new PolicyV2Store.CreateCase(
                fixture.caseId(),
                snapshot,
                finding,
                NOW.minusSeconds(30),
                resolution,
                List.of(new BehavioralHistoryEntry(
                        "prior-audit-only",
                        NOW.minus(Duration.ofDays(5)),
                        SPAM,
                        SPAM,
                        BehavioralHistoryEntry.FindingState.CONFIRMED
                )),
                List.of(mute(Duration.ofHours(1))),
                fixture.actorId(),
                operationKey,
                NOW
        );
    }

    static PolicySnapshot policy() {
        List<String> offenseIds = List.of(SPAM, HARASSMENT);
        return new PolicySnapshot("policy-v2-persistence-test", offenseIds.stream()
                .map(id -> offense(id, offenseIds))
                .toList());
    }

    static OffensePolicy offense(String id, List<String> knownOffenses) {
        Map<String, Double> relationships = Map.of(
                knownOffenses.get(0), 1.0,
                knownOffenses.get(1), 0.5
        );
        RemedySpec remedy = new RemedySpec(
                "remove-message",
                RemedySpec.Type.REMOVE_CONTENT,
                "Remove the offending message"
        );
        PolicyAction action = new PolicyAction.Exact(List.of(mute(Duration.ofHours(1))));
        ResolutionRule rule = new ResolutionRule(
                "default",
                new RuleCondition(
                        Map.of("scope", Set.of(new IncidentAttributeValue.EnumValue("global"))),
                        HistoryWindow.atLeast(0.0)
                ),
                action,
                List.of(remedy)
        );
        return new OffensePolicy(
                id,
                id,
                "chat",
                List.of(IncidentAttributeDefinition.enumValue(
                        "scope",
                        true,
                        Set.of("global", "private")
                )),
                new HistoryPolicy(relationships, DecayPolicy.nonDecaying()),
                List.of(rule)
        );
    }

    static IncidentFinding finding(String offenseId) {
        return new IncidentFinding(
                offenseId,
                Map.of("scope", new IncidentAttributeValue.EnumValue("global"))
        );
    }

    static SanctionSpec mute(Duration duration) {
        return new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(duration));
    }

    static SanctionSpec warning() {
        return new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
    }

    static Fixture seed(MariaDBContainer<?> database, int sequence) throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        insertPlayer(database, targetId, "P2Target" + sequence, NOW.minusSeconds(120));
        insertPlayer(database, actorId, "P2Actor" + sequence, NOW.minusSeconds(120));
        String caseId = caseId(sequence);
        insertCase(database, caseId, targetId, actorId, NOW.minusSeconds(30));
        return new Fixture(caseId, targetId, actorId);
    }

    static String caseId(int sequence) {
        return "P2%014d".formatted(sequence);
    }

    static HikariDataSource open(MariaDBContainer<?> database) {
        HikariDataSource dataSource = MariaDb.open(databaseConfig(database));
        MariaDb.migrate(dataSource);
        return dataSource;
    }

    static void createAuditFailureTrigger(MariaDBContainer<?> database) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(database);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER policy_v2_test_fail_audit
                    BEFORE INSERT ON policy_v2_audit_events
                    FOR EACH ROW
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced Policy v2 audit failure'
                    """);
        }
    }

    static void dropAuditFailureTrigger(MariaDBContainer<?> database) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(database);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS policy_v2_test_fail_audit");
        }
    }

    static int countFindingRevisions(MariaDBContainer<?> database, String caseId) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(database);
             var statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM policy_v2_finding_revisions
                     WHERE case_id = ?
                     """)) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    static int countFindingAuditEvents(MariaDBContainer<?> database, String caseId) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(database);
             var statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM policy_v2_audit_events
                     WHERE case_id = ? AND event_type LIKE 'FINDING_%'
                     """)) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    static int countOperation(MariaDBContainer<?> database, String operationKey) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(database);
             var statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM policy_v2_operations
                     WHERE operation_key = ?
                     """)) {
            statement.setString(1, operationKey);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    record Fixture(String caseId, UUID targetId, UUID actorId) {
    }
}
