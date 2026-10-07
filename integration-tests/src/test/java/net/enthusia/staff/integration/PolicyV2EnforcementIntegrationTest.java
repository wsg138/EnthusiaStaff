package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.caseId;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.open;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.seed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessCoordinator;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator.Observation;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator.VpnState;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyService;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.Fixture;
import net.enthusia.staff.persistence.JdbcPolicyV2EnforcementStore;
import net.enthusia.staff.persistence.JdbcPolicyV2Store;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PolicyV2EnforcementIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-07T02:00:00Z");
    private static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String PROFILE_OFFENSE = "profile.inappropriate-username";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_policy_v2_enforcement")
            .withUsername("staff_test")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void lifecycleJoinCorrectionRestartAndAuditRemainDurable() throws Exception {
        Fixture fixture = seed(DATABASE, 301);
        RemedySpec remedy = profileRemedy();
        Actor admin = actor(fixture.actorId(), StaffRank.ADMIN);

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource);
            createPolicyCase(runtime.canonical(), fixture, PROFILE_OFFENSE, remedy, "case:profile:301");

            PolicyV2RemedyEnforcement required = runtime.remedies().register(
                    admin,
                    register(fixture, remedy, Scope.NETWORK_ACCESS, Condition.username("BadName"), "register:301")
            );
            PolicyV2RemedyEnforcement enforced = runtime.remedies().enforce(
                    admin,
                    fixture.caseId(),
                    remedy.id(),
                    required.revision(),
                    (ignored, operationId) -> { },
                    "enforce:301",
                    NOW.plusSeconds(1)
            );
            assertEquals(Lifecycle.ENFORCED, enforced.lifecycle());
        }

        try (HikariDataSource restarted = open(DATABASE)) {
            Runtime runtime = runtime(restarted);
            PolicyV2RemedyEnforcement recovered = runtime.enforcement()
                    .find(fixture.caseId(), remedy.id())
                    .orElseThrow();
            assertEquals(Lifecycle.ENFORCED, recovered.lifecycle());

            PolicyV2AccessEvaluator evaluator = new PolicyV2AccessEvaluator(runtime.enforcement());
            assertFalse(evaluator.evaluate(observation(fixture.targetId(), "BadName")).allowed());
            assertFalse(evaluator.evaluate(observation(fixture.targetId(), "GoodName")).allowed());

            PolicyV2AccessCoordinator coordinator = new PolicyV2AccessCoordinator(evaluator, runtime.remedies());
            assertTrue(coordinator.evaluateAndRepair(
                    observation(fixture.targetId(), "GoodName"),
                    NOW.plusSeconds(2)
            ).allowed());

            assertEquals(
                    PolicyV2Store.RemedyStatus.SATISFIED,
                    remedy(runtime.canonical(), fixture.caseId(), remedy.id()).status()
            );
            Set<String> events = runtime.canonical().auditHistory(fixture.caseId(), 100).stream()
                    .map(PolicyV2Store.AuditEvent::eventType)
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(events.contains("REMEDY_ENFORCEMENT_REQUIRED"));
            assertTrue(events.contains("REMEDY_ENFORCEMENT_ENFORCED"));
            assertTrue(events.contains("REMEDY_ENFORCEMENT_SATISFIED"));
            assertTrue(events.contains("REMEDY_UPDATED"));

            int baselineAuditCount = runtime.canonical().auditHistory(fixture.caseId(), 100).size();
            for (int index = 0; index < 4; index++) {
                assertTrue(coordinator.evaluateAndRepair(
                        observation(fixture.targetId(), "GoodName"),
                        NOW.plusSeconds(10 + index)
                ).allowed());
            }
            assertEquals(baselineAuditCount, runtime.canonical().auditHistory(fixture.caseId(), 100).size());
        }

        try (HikariDataSource restartedAgain = open(DATABASE)) {
            Runtime runtime = runtime(restartedAgain);
            assertTrue(runtime.enforcement().activeFor(fixture.targetId()).isEmpty());
        }
    }

    @Test
    void enforcementFailureRollsBackAndRetryReusesProviderOperation() throws Exception {
        Fixture fixture = seed(DATABASE, 302);
        RemedySpec remedy = contentRemedy();
        Actor admin = actor(fixture.actorId(), StaffRank.ADMIN);
        Actor helper = actor(fixture.actorId(), StaffRank.HELPER);
        List<UUID> providerOperations = new ArrayList<>();

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource);
            createPolicyCase(runtime.canonical(), fixture, "content.inappropriate", remedy, "case:content:302");
            PolicyV2RemedyEnforcement required = runtime.remedies().register(
                    admin,
                    register(fixture, remedy, Scope.CONTENT, Condition.manual(), "register:302")
            );
            PolicyV2RemedyEnforcement replay = runtime.remedies().register(
                    admin,
                    register(fixture, remedy, Scope.CONTENT, Condition.manual(), "register:302")
            );
            assertEquals(required, replay);

            AtomicInteger unauthorizedCalls = new AtomicInteger();
            assertThrows(SecurityException.class, () -> runtime.remedies().enforce(
                    helper,
                    fixture.caseId(),
                    remedy.id(),
                    0L,
                    (ignored, operationId) -> unauthorizedCalls.incrementAndGet(),
                    "unauthorized:302",
                    NOW.plusSeconds(1)
            ));
            assertEquals(0, unauthorizedCalls.get());

            createLifecycleAuditFailureTrigger();
            try {
                assertThrows(ModerationPersistenceException.class, () -> runtime.remedies().enforce(
                        admin,
                        fixture.caseId(),
                        remedy.id(),
                        0L,
                        (ignored, operationId) -> providerOperations.add(operationId),
                        "enforce:302",
                        NOW.plusSeconds(2)
                ));
            } finally {
                dropLifecycleAuditFailureTrigger();
            }
            assertEquals(Lifecycle.REQUIRED, runtime.enforcement()
                    .find(fixture.caseId(), remedy.id()).orElseThrow().lifecycle());

            PolicyV2RemedyEnforcement enforced = runtime.remedies().enforce(
                    admin,
                    fixture.caseId(),
                    remedy.id(),
                    0L,
                    (ignored, operationId) -> providerOperations.add(operationId),
                    "enforce:302",
                    NOW.plusSeconds(2)
            );
            assertEquals(Lifecycle.ENFORCED, enforced.lifecycle());
            assertEquals(2, providerOperations.size());
            assertEquals(providerOperations.get(0), providerOperations.get(1));

            PolicyV2RemedyEnforcement replayed = runtime.remedies().enforce(
                    admin,
                    fixture.caseId(),
                    remedy.id(),
                    0L,
                    (ignored, operationId) -> providerOperations.add(operationId),
                    "enforce:302",
                    NOW.plusSeconds(2)
            );
            assertEquals(enforced, replayed);
            assertEquals(2, providerOperations.size());

            assertThrows(PolicyV2Store.Conflict.class, () -> runtime.remedies().enforce(
                    admin,
                    fixture.caseId(),
                    remedy.id(),
                    0L,
                    (ignored, operationId) -> providerOperations.add(operationId),
                    "enforce:different:302",
                    NOW.plusSeconds(2)
            ));
            assertEquals(2, providerOperations.size());

            assertThrows(PolicyV2Store.Conflict.class, () -> runtime.enforcement().transition(
                    new PolicyV2EnforcementStore.TransitionRequest(
                            fixture.caseId(),
                            remedy.id(),
                            0L,
                            Lifecycle.SATISFIED,
                            fixture.actorId(),
                            "stale transition",
                            "stale:302",
                            NOW.plusSeconds(3)
                    )
            ));
        }
    }

    @Test
    void terminalProjectionRecoversWhenCanonicalUpdateCommittedFirst() throws Exception {
        Fixture fixture = seed(DATABASE, 303);
        RemedySpec remedy = contentRemedy();
        Actor admin = actor(fixture.actorId(), StaffRank.ADMIN);

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource);
            createPolicyCase(runtime.canonical(), fixture, "content.inappropriate", remedy, "case:content:303");
            PolicyV2RemedyEnforcement required = runtime.remedies().register(
                    admin,
                    register(fixture, remedy, Scope.CONTENT, Condition.manual(), "register:303")
            );
            PolicyV2RemedyEnforcement enforced = runtime.remedies().enforce(
                    admin,
                    fixture.caseId(),
                    remedy.id(),
                    required.revision(),
                    (ignored, operationId) -> { },
                    "enforce:303",
                    NOW.plusSeconds(1)
            );

            createLifecycleAuditFailureTrigger();
            try {
                assertThrows(ModerationPersistenceException.class, () -> runtime.remedies().satisfy(
                        admin,
                        fixture.caseId(),
                        remedy.id(),
                        enforced.revision(),
                        "Content removal verified",
                        "satisfy:303",
                        NOW.plusSeconds(2)
                ));
            } finally {
                dropLifecycleAuditFailureTrigger();
            }

            assertEquals(
                    PolicyV2Store.RemedyStatus.SATISFIED,
                    remedy(runtime.canonical(), fixture.caseId(), remedy.id()).status()
            );
            assertEquals(
                    Lifecycle.ENFORCED,
                    runtime.enforcement().find(fixture.caseId(), remedy.id()).orElseThrow().lifecycle()
            );

            PolicyV2RemedyEnforcement recovered = runtime.remedies().satisfy(
                    admin,
                    fixture.caseId(),
                    remedy.id(),
                    enforced.revision(),
                    "Content removal verified",
                    "satisfy:303",
                    NOW.plusSeconds(2)
            );
            assertEquals(Lifecycle.SATISFIED, recovered.lifecycle());
        }
    }

    @Test
    void recurrenceStaysComplianceOnlyAndWaiverRequiresAuthorization() throws Exception {
        Fixture first = seed(DATABASE, 304);
        String secondCaseId = caseId(305);
        insertCase(DATABASE, secondCaseId, first.targetId(), first.actorId(), NOW.plusSeconds(20));
        Fixture second = new Fixture(secondCaseId, first.targetId(), first.actorId());
        RemedySpec remedy = profileRemedy();
        Actor admin = actor(first.actorId(), StaffRank.ADMIN);
        Actor moderator = actor(first.actorId(), StaffRank.MOD);

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource);
            createPolicyCase(runtime.canonical(), first, PROFILE_OFFENSE, remedy, "case:profile:304");
            PolicyV2RemedyEnforcement firstRequired = runtime.remedies().register(
                    admin,
                    register(first, remedy, Scope.NETWORK_ACCESS, Condition.username("BadOne"), "register:304")
            );
            runtime.remedies().satisfy(
                    admin,
                    first.caseId(),
                    remedy.id(),
                    firstRequired.revision(),
                    "Username corrected",
                    "satisfy:304",
                    NOW.plusSeconds(1)
            );

            createPolicyCase(runtime.canonical(), second, PROFILE_OFFENSE, remedy, "case:profile:305");
            PolicyV2RemedyEnforcement recurrence = runtime.remedies().register(
                    admin,
                    register(second, remedy, Scope.NETWORK_ACCESS, Condition.username("BadTwo"), "register:305")
            );
            assertEquals(second.caseId(), runtime.enforcement().activeFor(first.targetId()).getFirst().caseId());

            assertThrows(SecurityException.class, () -> runtime.remedies().waive(
                    moderator,
                    second.caseId(),
                    remedy.id(),
                    recurrence.revision(),
                    "Moderator cannot waive",
                    "waive:mod:305",
                    NOW.plusSeconds(2)
            ));

            PolicyV2RemedyEnforcement waived = runtime.remedies().waive(
                    admin,
                    second.caseId(),
                    remedy.id(),
                    recurrence.revision(),
                    "Authorized compliance waiver",
                    "waive:admin:305",
                    NOW.plusSeconds(3)
            );
            assertEquals(Lifecycle.WAIVED, waived.lifecycle());

            List<String> offenses = runtime.canonical().history(
                    first.targetId(),
                    NOW.plusSeconds(60),
                    20
            ).stream().map(entry -> entry.contributingOffenseId().orElse(entry.originalOffenseId())).toList();
            assertEquals(List.of(PROFILE_OFFENSE, PROFILE_OFFENSE), offenses);
            assertFalse(offenses.contains("access.vpn-evasion"));
            assertFalse(offenses.contains("evasion.mute"));
        }
    }

    @Test
    void marketRestrictionKeepsExistingAdminAuthorityBoundary() throws Exception {
        Fixture fixture = seed(DATABASE, 308);
        RemedySpec remedy = new RemedySpec(
                "market-access",
                RemedySpec.Type.ACCESS_RESTRICTION,
                "Restrict Market access"
        );
        Actor moderator = actor(fixture.actorId(), StaffRank.MOD);
        Actor admin = actor(fixture.actorId(), StaffRank.ADMIN);

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource);
            createPolicyCase(runtime.canonical(), fixture, "market.stall-compliance", remedy, "case:308");

            assertThrows(SecurityException.class, () -> runtime.remedies().register(
                    moderator,
                    register(fixture, remedy, Scope.MARKET_ACCESS, Condition.manual(), "register:mod:308")
            ));

            PolicyV2RemedyEnforcement registered = runtime.remedies().register(
                    admin,
                    register(fixture, remedy, Scope.MARKET_ACCESS, Condition.manual(), "register:admin:308")
            );
            assertEquals(Lifecycle.REQUIRED, registered.lifecycle());
        }
    }

    @Test
    void registrationOperationCollisionRejectsDifferentCase() throws Exception {
        Fixture first = seed(DATABASE, 306);
        Fixture second = seed(DATABASE, 307);
        RemedySpec remedy = contentRemedy();
        Actor admin = actor(first.actorId(), StaffRank.ADMIN);

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource);
            createPolicyCase(runtime.canonical(), first, "content.inappropriate", remedy, "case:306");
            createPolicyCase(runtime.canonical(), second, "content.inappropriate", remedy, "case:307");
            runtime.remedies().register(
                    admin,
                    register(first, remedy, Scope.CONTENT, Condition.manual(), "shared-register")
            );

            Actor secondAdmin = actor(second.actorId(), StaffRank.ADMIN);
            assertThrows(PolicyV2Store.Conflict.class, () -> runtime.remedies().register(
                    secondAdmin,
                    register(second, remedy, Scope.CONTENT, Condition.manual(), "shared-register")
            ));
            assertTrue(runtime.enforcement().find(second.caseId(), remedy.id()).isEmpty());
        }
    }

    private static Runtime runtime(HikariDataSource dataSource) {
        PolicyV2Store canonical = new JdbcPolicyV2Store(dataSource);
        PolicyV2EnforcementStore enforcement = new JdbcPolicyV2EnforcementStore(dataSource);
        PolicyV2RemedyService remedies = new PolicyV2RemedyService(
                canonical,
                enforcement,
                new DefaultAuthorizationPolicy(),
                SYSTEM_ACTOR
        );
        return new Runtime(canonical, enforcement, remedies);
    }

    private static PolicyV2RemedyService.RegisterCommand register(
            Fixture fixture,
            RemedySpec remedy,
            Scope scope,
            Condition condition,
            String operationKey
    ) {
        return new PolicyV2RemedyService.RegisterCommand(
                fixture.caseId(),
                remedy.id(),
                fixture.targetId(),
                scope,
                condition,
                operationKey,
                NOW
        );
    }

    private static Observation observation(UUID subjectId, String username) {
        return new Observation(subjectId, username, VpnState.CLEAR, Map.of());
    }

    private static Actor actor(UUID actorId, StaffRank rank) {
        return new Actor(actorId, rank.name(), rank);
    }

    private static RemedySpec profileRemedy() {
        return new RemedySpec(
                "correct-username",
                RemedySpec.Type.CORRECT_PROFILE,
                "Correct the prohibited username"
        );
    }

    private static RemedySpec contentRemedy() {
        return new RemedySpec(
                "remove-content",
                RemedySpec.Type.REMOVE_CONTENT,
                "Remove prohibited content"
        );
    }

    private static void createPolicyCase(
            PolicyV2Store store,
            Fixture fixture,
            String offenseId,
            RemedySpec remedy,
            String operationKey
    ) {
        SanctionSpec warning = new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
        PolicyAction action = new PolicyAction.Exact(List.of(warning));
        ResolutionRule rule = new ResolutionRule(
                "default",
                new RuleCondition(Map.of(), HistoryWindow.atLeast(0.0)),
                action,
                List.of(remedy)
        );
        PolicySnapshot snapshot = new PolicySnapshot(
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
        IncidentFinding finding = new IncidentFinding(offenseId, Map.of());
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
                finding,
                NOW.minusSeconds(30),
                resolution,
                List.of(),
                List.of(warning),
                fixture.actorId(),
                operationKey,
                NOW
        ));
    }

    private static PolicyV2Store.RemedyRecord remedy(
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

    private static void createLifecycleAuditFailureTrigger() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
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

    private static void dropLifecycleAuditFailureTrigger() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS policy_v2_enforcement_test_fail_audit");
        }
    }

    private record Runtime(
            PolicyV2Store canonical,
            PolicyV2EnforcementStore enforcement,
            PolicyV2RemedyService remedies
    ) {
    }
}
