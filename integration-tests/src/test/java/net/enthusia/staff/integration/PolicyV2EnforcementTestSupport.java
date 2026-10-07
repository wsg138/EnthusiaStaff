package net.enthusia.staff.integration;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.DecayPolicy;
import net.enthusia.staff.domain.policyv2.HistoryAssessment;
import net.enthusia.staff.domain.policyv2.HistoryPolicy;
import net.enthusia.staff.domain.policyv2.HistoryWindow;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicyResolution;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.ResolutionRule;
import net.enthusia.staff.domain.policyv2.RuleCondition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator.Observation;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator.VpnState;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyService;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.Fixture;
import net.enthusia.staff.persistence.JdbcPolicyV2EnforcementStore;
import net.enthusia.staff.persistence.JdbcPolicyV2Store;
import org.testcontainers.containers.MariaDBContainer;

final class PolicyV2EnforcementTestSupport {
    static final String CONTENT_OFFENSE = "content.inappropriate";
    static final String PROFILE_OFFENSE = "profile.inappropriate-username";

    private PolicyV2EnforcementTestSupport() {
    }

    static Runtime runtime(javax.sql.DataSource dataSource, UUID systemActorId) {
        PolicyV2Store canonical = new JdbcPolicyV2Store(dataSource);
        PolicyV2EnforcementStore enforcement = new JdbcPolicyV2EnforcementStore(dataSource);
        PolicyV2RemedyService remedies = new PolicyV2RemedyService(
                canonical,
                enforcement,
                new DefaultAuthorizationPolicy(),
                systemActorId
        );
        return new Runtime(canonical, enforcement, remedies);
    }

    static PolicyV2RemedyService.RegisterCommand register(
            Fixture fixture,
            RemedySpec remedy,
            Scope scope,
            Condition condition,
            String operationKey,
            Instant now
    ) {
        return new PolicyV2RemedyService.RegisterCommand(
                fixture.caseId(),
                remedy.id(),
                fixture.targetId(),
                scope,
                condition,
                operationKey,
                now
        );
    }

    static Observation observation(UUID subjectId, String username) {
        return new Observation(subjectId, username, VpnState.CLEAR, Map.of());
    }

    static Actor actor(UUID actorId, StaffRank rank) {
        return new Actor(actorId, rank.name(), rank);
    }

    static RemedySpec profileRemedy() {
        return new RemedySpec(
                "correct-username",
                RemedySpec.Type.CORRECT_PROFILE,
                "Correct the prohibited username"
        );
    }

    static RemedySpec contentRemedy() {
        return new RemedySpec(
                "remove-content",
                RemedySpec.Type.REMOVE_CONTENT,
                "Remove prohibited content"
        );
    }

    static void createPolicyCase(
            PolicyV2Store store,
            Fixture fixture,
            String offenseId,
            RemedySpec remedy,
            String operationKey,
            Instant now
    ) {
        SanctionSpec warning = new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
        PolicyAction action = new PolicyAction.Exact(List.of(warning));
        ResolutionRule rule = resolutionRule(remedy, action);
        PolicySnapshot snapshot = policySnapshot(fixture, offenseId, rule);
        PolicyResolution resolution = new PolicyResolution(
                snapshot.version(),
                offenseId,
                rule.id(),
                action,
                rule.remedies(),
                HistoryAssessment.empty()
        );
        store.createCase(new PolicyV2Store.CreateCase(
                fixture.caseId(),
                snapshot,
                new IncidentFinding(offenseId, Map.of()),
                now.minusSeconds(30),
                resolution,
                List.of(),
                List.of(warning),
                fixture.actorId(),
                operationKey,
                now
        ));
    }

    static PolicyV2Store.RemedyRecord remedy(
            PolicyV2Store store,
            String caseId,
            String remedyId
    ) {
        return store.findCase(caseId)
                .orElseThrow()
                .remedies()
                .stream()
                .filter(record -> record.remedy().id().equals(remedyId))
                .findFirst()
                .orElseThrow();
    }

    static void createLifecycleAuditFailureTrigger(MariaDBContainer<?> database) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(database);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER policy_v2_enforcement_test_fail_audit
                    BEFORE INSERT ON policy_v2_audit_events
                    FOR EACH ROW
                    BEGIN
                        IF NEW.event_type LIKE 'REMEDY_ENFORCEMENT_%' THEN
                            SIGNAL SQLSTATE '45000'
                                SET MESSAGE_TEXT = 'forced Policy v2 enforcement audit failure';
                        END IF;
                    END
                    """);
        }
    }

    static void dropLifecycleAuditFailureTrigger(MariaDBContainer<?> database) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(database);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS policy_v2_enforcement_test_fail_audit");
        }
    }

    private static ResolutionRule resolutionRule(RemedySpec remedy, PolicyAction action) {
        return new ResolutionRule(
                "default",
                new RuleCondition(Map.of(), HistoryWindow.atLeast(0.0)),
                action,
                List.of(remedy)
        );
    }

    private static PolicySnapshot policySnapshot(
            Fixture fixture,
            String offenseId,
            ResolutionRule rule
    ) {
        return new PolicySnapshot(
                "w3b-test-" + fixture.caseId(),
                List.of(new OffensePolicy(
                        offenseId,
                        offenseId,
                        "compliance",
                        List.of(),
                        new HistoryPolicy(Map.of(offenseId, 1.0), DecayPolicy.nonDecaying()),
                        List.of(rule)
                ))
        );
    }

    record Runtime(
            PolicyV2Store canonical,
            PolicyV2EnforcementStore enforcement,
            PolicyV2RemedyService remedies
    ) {
    }
}
