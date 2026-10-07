package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.CONTENT_OFFENSE;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.PROFILE_OFFENSE;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.actor;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.contentRemedy;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.createLifecycleAuditFailureTrigger;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.createPolicyCase;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.dropLifecycleAuditFailureTrigger;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.observation;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.profileRemedy;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.register;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.remedy;
import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.runtime;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.caseId;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.open;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.seed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.PolicyV2RemedyBindingSpec;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessCoordinator;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.ConditionType;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.Runtime;
import net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.Fixture;
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

        createEnforcedProfileRemedy(fixture, remedy, admin);
        assertCorrectionClearsAfterRestart(fixture, remedy);
        try (HikariDataSource dataSource = open(DATABASE)) {
            assertTrue(runtime(dataSource, SYSTEM_ACTOR).enforcement()
                    .activeFor(fixture.targetId())
                    .isEmpty());
        }
    }

    @Test
    void enforcementFailureRollsBackAndRetryReusesProviderOperation() throws Exception {
        Fixture fixture = seed(DATABASE, 302);
        RemedySpec remedy = contentRemedy();
        Actor admin = actor(fixture.actorId(), StaffRank.ADMIN);
        List<UUID> providerOperations = new ArrayList<>();

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, SYSTEM_ACTOR);
            createPolicyCase(runtime.canonical(), fixture, CONTENT_OFFENSE, remedy, "case:content:302", NOW);
            registerAndReplay(runtime, fixture, remedy, admin);
            assertUnauthorizedProviderNotCalled(runtime, fixture, remedy);
            assertAuditRollback(runtime, fixture, remedy, admin, providerOperations);
            PolicyV2RemedyEnforcement enforced =
                    enforceAfterRollback(runtime, fixture, remedy, admin, providerOperations);
            assertSuccessfulReplay(runtime, fixture, remedy, admin, enforced, providerOperations);
            assertStaleTransitionRejected(runtime, fixture, remedy);
        }
    }

    @Test
    void terminalProjectionRecoversWhenCanonicalUpdateCommittedFirst() throws Exception {
        Fixture fixture = seed(DATABASE, 303);
        RemedySpec remedy = contentRemedy();
        Actor admin = actor(fixture.actorId(), StaffRank.ADMIN);

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, SYSTEM_ACTOR);
            createPolicyCase(runtime.canonical(), fixture, CONTENT_OFFENSE, remedy, "case:content:303", NOW);
            PolicyV2RemedyEnforcement enforced = registerAndEnforce(runtime, fixture, remedy, admin, "303");
            assertTerminalProjectionRecovery(runtime, fixture, remedy, admin, enforced);
        }
    }

    @Test
    void recurrenceStaysComplianceOnlyAndWaiverRequiresAuthorization() throws Exception {
        Fixture first = seed(DATABASE, 304);
        String secondCaseId = caseId(305);
        insertCase(DATABASE, secondCaseId, first.targetId(), first.actorId(), NOW.plusSeconds(20));
        Fixture second = new Fixture(secondCaseId, first.targetId(), first.actorId());

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, SYSTEM_ACTOR);
            RemedySpec remedy = profileRemedy();
            Actor admin = actor(first.actorId(), StaffRank.ADMIN);
            satisfyFirstProfileCase(runtime, first, remedy, admin);
            assertRecurrenceWaiverAndHistory(runtime, second, remedy, admin);
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

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, SYSTEM_ACTOR);
            createPolicyCase(runtime.canonical(), fixture, "market.stall-compliance", remedy, "case:308", NOW);
            assertThrows(SecurityException.class, () -> runtime.remedies().register(
                    actor(fixture.actorId(), StaffRank.MOD),
                    register(fixture, remedy, Scope.MARKET_ACCESS, Condition.manual(), "register:mod:308", NOW)
            ));
            PolicyV2RemedyEnforcement registered = runtime.remedies().register(
                    actor(fixture.actorId(), StaffRank.ADMIN),
                    register(fixture, remedy, Scope.MARKET_ACCESS, Condition.manual(), "register:admin:308", NOW)
            );
            assertEquals(Lifecycle.REQUIRED, registered.lifecycle());
        }
    }

    @Test
    void configuredRegistrationUsesPersistedBindingAndRejectsCallerConditionOverride() throws Exception {
        Fixture fixture = seed(DATABASE, 309);
        RemedySpec vpn = new RemedySpec(
                "vpn-access",
                RemedySpec.Type.ACCESS_RESTRICTION,
                "Disable the unapproved VPN",
                Optional.of(new PolicyV2RemedyBindingSpec(
                        Scope.NETWORK_ACCESS,
                        ConditionType.VPN_APPROVAL,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                ))
        );
        Actor admin = actor(fixture.actorId(), StaffRank.ADMIN);

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, SYSTEM_ACTOR);
            createPolicyCase(runtime.canonical(), fixture,
                    "access.vpn-compliance", vpn, "case:309", NOW);

            assertThrows(IllegalArgumentException.class, () -> runtime.remedies().register(
                    admin,
                    register(fixture, vpn, Scope.NETWORK_ACCESS, Condition.manual(),
                            "register:wrong:309", NOW)
            ));
            assertTrue(runtime.enforcement().find(fixture.caseId(), vpn.id()).isEmpty());

            PolicyV2RemedyEnforcement configured = runtime.remedies().registerConfigured(
                    admin,
                    new net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyService
                            .ConfiguredRegisterCommand(
                            fixture.caseId(), vpn.id(), fixture.targetId(),
                            "register:bound:309", NOW
                    )
            );
            assertEquals(Scope.NETWORK_ACCESS, configured.scope());
            assertEquals(Condition.vpnApproval(), configured.condition());

            PolicyV2RemedyEnforcement replay = runtime.remedies().registerConfigured(
                    admin,
                    new net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyService
                            .ConfiguredRegisterCommand(
                            fixture.caseId(), vpn.id(), fixture.targetId(),
                            "register:bound:309", NOW
                    )
            );
            assertEquals(configured, replay);
        }
    }

    @Test
    void registrationOperationCollisionRejectsDifferentCase() throws Exception {
        Fixture first = seed(DATABASE, 306);
        Fixture second = seed(DATABASE, 307);
        RemedySpec remedy = contentRemedy();

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, SYSTEM_ACTOR);
            createPolicyCase(runtime.canonical(), first, CONTENT_OFFENSE, remedy, "case:306", NOW);
            createPolicyCase(runtime.canonical(), second, CONTENT_OFFENSE, remedy, "case:307", NOW);
            runtime.remedies().register(
                    actor(first.actorId(), StaffRank.ADMIN),
                    register(first, remedy, Scope.CONTENT, Condition.manual(), "shared-register", NOW)
            );
            assertThrows(PolicyV2Store.Conflict.class, () -> runtime.remedies().register(
                    actor(second.actorId(), StaffRank.ADMIN),
                    register(second, remedy, Scope.CONTENT, Condition.manual(), "shared-register", NOW)
            ));
            assertTrue(runtime.enforcement().find(second.caseId(), remedy.id()).isEmpty());
        }
    }

    private static void createEnforcedProfileRemedy(
            Fixture fixture,
            RemedySpec remedy,
            Actor admin
    ) throws Exception {
        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, SYSTEM_ACTOR);
            createPolicyCase(runtime.canonical(), fixture, PROFILE_OFFENSE, remedy, "case:profile:301", NOW);
            PolicyV2RemedyEnforcement required = runtime.remedies().register(
                    admin,
                    register(fixture, remedy, Scope.NETWORK_ACCESS, Condition.username("BadName"), "register:301", NOW)
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
    }

    private static void assertCorrectionClearsAfterRestart(
            Fixture fixture,
            RemedySpec remedy
    ) throws Exception {
        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, SYSTEM_ACTOR);
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
            assertLifecycleAudit(runtime, fixture.caseId());

            int auditCount = runtime.canonical().auditHistory(fixture.caseId(), 100).size();
            assertTrue(coordinator.evaluateAndRepair(
                    observation(fixture.targetId(), "GoodName"),
                    NOW.plusSeconds(10)
            ).allowed());
            assertEquals(auditCount, runtime.canonical().auditHistory(fixture.caseId(), 100).size());
        }
    }

    private static void assertLifecycleAudit(Runtime runtime, String caseId) {
        Set<String> events = runtime.canonical().auditHistory(caseId, 100).stream()
                .map(PolicyV2Store.AuditEvent::eventType)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(events.contains("REMEDY_ENFORCEMENT_REQUIRED"));
        assertTrue(events.contains("REMEDY_ENFORCEMENT_ENFORCED"));
        assertTrue(events.contains("REMEDY_ENFORCEMENT_SATISFIED"));
        assertTrue(events.contains("REMEDY_UPDATED"));
    }

    private static void registerAndReplay(
            Runtime runtime,
            Fixture fixture,
            RemedySpec remedy,
            Actor admin
    ) {
        PolicyV2RemedyEnforcement required = runtime.remedies().register(
                admin,
                register(fixture, remedy, Scope.CONTENT, Condition.manual(), "register:302", NOW)
        );
        PolicyV2RemedyEnforcement replay = runtime.remedies().register(
                admin,
                register(fixture, remedy, Scope.CONTENT, Condition.manual(), "register:302", NOW)
        );
        assertEquals(required, replay);
    }

    private static void assertUnauthorizedProviderNotCalled(
            Runtime runtime,
            Fixture fixture,
            RemedySpec remedy
    ) {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(SecurityException.class, () -> runtime.remedies().enforce(
                actor(fixture.actorId(), StaffRank.HELPER),
                fixture.caseId(),
                remedy.id(),
                0L,
                (ignored, operationId) -> calls.incrementAndGet(),
                "unauthorized:302",
                NOW.plusSeconds(1)
        ));
        assertEquals(0, calls.get());
    }

    private static void assertAuditRollback(
            Runtime runtime,
            Fixture fixture,
            RemedySpec remedy,
            Actor admin,
            List<UUID> providerOperations
    ) throws Exception {
        createLifecycleAuditFailureTrigger(DATABASE);
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
            dropLifecycleAuditFailureTrigger(DATABASE);
        }
        assertEquals(Lifecycle.REQUIRED, runtime.enforcement()
                .find(fixture.caseId(), remedy.id()).orElseThrow().lifecycle());
    }

    private static PolicyV2RemedyEnforcement enforceAfterRollback(
            Runtime runtime,
            Fixture fixture,
            RemedySpec remedy,
            Actor admin,
            List<UUID> providerOperations
    ) {
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
        return enforced;
    }

    private static void assertSuccessfulReplay(
            Runtime runtime,
            Fixture fixture,
            RemedySpec remedy,
            Actor admin,
            PolicyV2RemedyEnforcement enforced,
            List<UUID> providerOperations
    ) {
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
    }

    private static void assertStaleTransitionRejected(
            Runtime runtime,
            Fixture fixture,
            RemedySpec remedy
    ) {
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

    private static PolicyV2RemedyEnforcement registerAndEnforce(
            Runtime runtime,
            Fixture fixture,
            RemedySpec remedy,
            Actor admin,
            String suffix
    ) {
        PolicyV2RemedyEnforcement required = runtime.remedies().register(
                admin,
                register(fixture, remedy, Scope.CONTENT, Condition.manual(), "register:" + suffix, NOW)
        );
        return runtime.remedies().enforce(
                admin,
                fixture.caseId(),
                remedy.id(),
                required.revision(),
                (ignored, operationId) -> { },
                "enforce:" + suffix,
                NOW.plusSeconds(1)
        );
    }

    private static void assertTerminalProjectionRecovery(
            Runtime runtime,
            Fixture fixture,
            RemedySpec remedy,
            Actor admin,
            PolicyV2RemedyEnforcement enforced
    ) throws Exception {
        createLifecycleAuditFailureTrigger(DATABASE);
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
            dropLifecycleAuditFailureTrigger(DATABASE);
        }
        assertEquals(
                PolicyV2Store.RemedyStatus.SATISFIED,
                remedy(runtime.canonical(), fixture.caseId(), remedy.id()).status()
        );
        assertEquals(
                Lifecycle.ENFORCED,
                runtime.enforcement().find(fixture.caseId(), remedy.id()).orElseThrow().lifecycle()
        );
        assertEquals(Lifecycle.SATISFIED, runtime.remedies().satisfy(
                admin,
                fixture.caseId(),
                remedy.id(),
                enforced.revision(),
                "Content removal verified",
                "satisfy:303",
                NOW.plusSeconds(2)
        ).lifecycle());
    }

    private static void satisfyFirstProfileCase(
            Runtime runtime,
            Fixture fixture,
            RemedySpec remedy,
            Actor admin
    ) {
        createPolicyCase(runtime.canonical(), fixture, PROFILE_OFFENSE, remedy, "case:profile:304", NOW);
        PolicyV2RemedyEnforcement required = runtime.remedies().register(
                admin,
                register(fixture, remedy, Scope.NETWORK_ACCESS, Condition.username("BadOne"), "register:304", NOW)
        );
        runtime.remedies().satisfy(
                admin,
                fixture.caseId(),
                remedy.id(),
                required.revision(),
                "Username corrected",
                "satisfy:304",
                NOW.plusSeconds(1)
        );
    }

    private static void assertRecurrenceWaiverAndHistory(
            Runtime runtime,
            Fixture fixture,
            RemedySpec remedy,
            Actor admin
    ) {
        createPolicyCase(runtime.canonical(), fixture, PROFILE_OFFENSE, remedy, "case:profile:305", NOW);
        PolicyV2RemedyEnforcement recurrence = runtime.remedies().register(
                admin,
                register(fixture, remedy, Scope.NETWORK_ACCESS, Condition.username("BadTwo"), "register:305", NOW)
        );
        assertEquals(fixture.caseId(), runtime.enforcement().activeFor(fixture.targetId()).getFirst().caseId());
        assertThrows(SecurityException.class, () -> runtime.remedies().waive(
                actor(fixture.actorId(), StaffRank.MOD),
                fixture.caseId(),
                remedy.id(),
                recurrence.revision(),
                "Moderator cannot waive",
                "waive:mod:305",
                NOW.plusSeconds(2)
        ));
        assertEquals(Lifecycle.WAIVED, runtime.remedies().waive(
                admin,
                fixture.caseId(),
                remedy.id(),
                recurrence.revision(),
                "Authorized compliance waiver",
                "waive:admin:305",
                NOW.plusSeconds(3)
        ).lifecycle());

        List<String> offenses = runtime.canonical().history(
                fixture.targetId(),
                NOW.plusSeconds(60),
                20
        ).stream().map(entry -> entry.contributingOffenseId().orElse(entry.originalOffenseId())).toList();
        assertEquals(List.of(PROFILE_OFFENSE, PROFILE_OFFENSE), offenses);
        assertFalse(offenses.contains("access.vpn-evasion"));
        assertFalse(offenses.contains("evasion.mute"));
    }
}
