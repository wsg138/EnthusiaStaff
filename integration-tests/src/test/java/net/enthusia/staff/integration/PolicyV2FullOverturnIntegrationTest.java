package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.actor;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.createRequest;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.finding;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.open;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.seed;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.warning;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.CaseRevision;
import net.enthusia.staff.domain.policyv2.appeal.PolicyV2FullOverturnOrchestrator;
import net.enthusia.staff.domain.policyv2.appeal.PolicyV2FullOverturnOrchestrator.Checkpoint;
import net.enthusia.staff.domain.policyv2.appeal.PolicyV2FullOverturnStore;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyService;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.integration.PolicyV2FullOverturnRuntimeTestSupport.RecordingProviders;
import net.enthusia.staff.integration.PolicyV2FullOverturnRuntimeTestSupport.Runtime;
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
class PolicyV2FullOverturnIntegrationTest {
    private static final Instant OVERTURNED_AT = Instant.parse("2026-10-07T07:00:00Z");
    private static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_policy_v2_full_overturn")
            .withUsername("staff_test")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void everyRequiredFailureCheckpointRecoversAfterRestartWithoutDuplicateEffects() throws Exception {
        int sequence = 410;
        for (Checkpoint checkpoint : Checkpoint.values()) {
            assertFailureCheckpointRecovery(checkpoint, ++sequence);
        }
    }

    private static void assertFailureCheckpointRecovery(
            Checkpoint checkpoint,
            int sequence
    ) throws Exception {
        Fixture fixture = prepare(sequence);
        UUID operationId = UUID.randomUUID();
        RecordingProviders providers = new RecordingProviders();

        failOnceAfterDurableBegin(fixture, operationId, providers, checkpoint);
        recoverAfterRestart(fixture, operationId, providers);
        assertConverged(fixture, operationId, providers);
        assertProviderRetryShape(checkpoint, providers);
    }

    private static void assertProviderRetryShape(
            Checkpoint checkpoint,
            RecordingProviders providers
    ) {
        int sanctionAttempts = checkpoint == Checkpoint.AFTER_SANCTION_TERMINATION ? 2 : 1;
        int remedyAttempts = checkpoint == Checkpoint.AFTER_REMEDY_CLEANUP ? 2 : 1;
        assertEquals(sanctionAttempts, providers.sanctionAttempts.get());
        assertEquals(remedyAttempts, providers.remedyAttempts.get());
    }

    @Test
    void authorizationFailurePersistsNothingAndCallsNoProvider() throws Exception {
        Fixture fixture = prepare(430);
        UUID operationId = UUID.randomUUID();
        RecordingProviders providers = new RecordingProviders();

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, providers, checkpoint -> { });
            Actor mod = actor(fixture.actorId(), StaffRank.MOD);
            assertThrows(SecurityException.class, () -> runtime.orchestrator().execute(
                    command(operationId, fixture, mod, "appeal-auth")
            ));
            assertTrue(runtime.operations().find(operationId).isEmpty());
            assertTrue(providers.sanctionEffects.isEmpty());
            assertTrue(providers.remedyEffects.isEmpty());
        }
    }

    @Test
    void operationAndCaseCollisionsAreRejectedBeforeMutation() throws Exception {
        Fixture fixture = prepare(431);
        RecordingProviders providers = new RecordingProviders();
        UUID operationId = UUID.randomUUID();
        AtomicBoolean fail = new AtomicBoolean(true);

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, providers, checkpoint -> {
                if (checkpoint == Checkpoint.BEFORE_FINDING_OVERTURN && fail.getAndSet(false)) {
                    throw new InjectedFailure();
                }
            });
            Actor admin = actor(fixture.actorId(), StaffRank.ADMIN);
            assertThrows(InjectedFailure.class, () -> runtime.orchestrator().execute(
                    command(operationId, fixture, admin, "appeal-original")
            ));
            assertThrows(PolicyV2Store.Conflict.class, () -> runtime.orchestrator().execute(
                    command(operationId, fixture, admin, "appeal-different")
            ));
            assertThrows(PolicyV2Store.Conflict.class, () -> runtime.orchestrator().execute(
                    commandAt(
                            operationId,
                            fixture,
                            admin,
                            "appeal-original",
                            OVERTURNED_AT.plusSeconds(1)
                    )
            ));
            assertThrows(PolicyV2Store.Conflict.class, () -> runtime.orchestrator().execute(
                    command(UUID.randomUUID(), fixture, admin, "appeal-second-operation")
            ));
            assertEquals(BehavioralHistoryEntry.FindingState.CONFIRMED,
                    runtime.canonical().findCase(fixture.caseId()).orElseThrow().findingState());
        }
    }

    @Test
    void concurrentLeniencyTripsSanctionFenceBeforeProviderCall() throws Exception {
        Fixture fixture = prepare(432);
        UUID operationId = UUID.randomUUID();
        RecordingProviders providers = new RecordingProviders();
        failAt(fixture, operationId, providers, Checkpoint.BEFORE_SANCTION_TERMINATION);

        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store canonical = new JdbcPolicyV2Store(dataSource);
            canonical.reviseSanctions(new PolicyV2Store.SanctionRevisionRequest(
                    fixture.caseId(),
                    0L,
                    PolicyV2Store.SanctionChangeKind.LENIENCY,
                    new CaseRevision.SanctionRevision("Concurrent leniency", List.of(warning())),
                    fixture.actorId(),
                    java.util.Optional.empty(),
                    "concurrent-leniency:432",
                    OVERTURNED_AT.plusSeconds(1)
            ));
        }

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, providers, checkpoint -> { });
            assertThrows(PolicyV2Store.Conflict.class, () -> runtime.orchestrator().resume(operationId));
            assertTrue(providers.sanctionEffects.isEmpty());
        }
    }

    @Test
    void concurrentReclassificationStaysSeparateAndTripsFindingFence() throws Exception {
        Fixture fixture = prepare(433);
        UUID operationId = UUID.randomUUID();
        RecordingProviders providers = new RecordingProviders();
        failAt(fixture, operationId, providers, Checkpoint.BEFORE_FINDING_OVERTURN);

        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store canonical = new JdbcPolicyV2Store(dataSource);
            canonical.reviseFinding(new PolicyV2Store.FindingRevisionRequest(
                    fixture.caseId(),
                    0L,
                    new CaseRevision.FindingReclassification(
                            "chat.spam",
                            "chat.harassment",
                            "Concurrent factual correction"
                    ),
                    java.util.Optional.of(finding("chat.harassment")),
                    fixture.actorId(),
                    java.util.Optional.empty(),
                    "concurrent-reclassification:433",
                    OVERTURNED_AT.plusSeconds(1)
            ));
        }

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, providers, checkpoint -> { });
            assertThrows(PolicyV2Store.Conflict.class, () -> runtime.orchestrator().resume(operationId));
            assertEquals(BehavioralHistoryEntry.FindingState.RECLASSIFIED,
                    runtime.canonical().findCase(fixture.caseId()).orElseThrow().findingState());
            assertTrue(providers.sanctionEffects.isEmpty());
            assertTrue(providers.remedyEffects.isEmpty());
        }
    }

    @Test
    void terminalCanonicalRemedyWithActiveEnforcementIsStillCleaned() throws Exception {
        Fixture fixture = prepare(436);
        UUID operationId = UUID.randomUUID();
        RecordingProviders providers = new RecordingProviders();

        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store canonical = new JdbcPolicyV2Store(dataSource);
            PolicyV2Store.CaseRecord policyCase = canonical.findCase(fixture.caseId()).orElseThrow();
            String remedyId = policyCase.remedies().get(0).remedy().id();
            canonical.updateRemedy(new PolicyV2Store.RemedyUpdateRequest(
                    fixture.caseId(),
                    remedyId,
                    0L,
                    PolicyV2Store.RemedyStatus.SATISFIED,
                    fixture.actorId(),
                    "Simulate W3B canonical-first completion before restart",
                    "split-canonical:436",
                    OVERTURNED_AT.minusMillis(500)
            ));
        }

        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, providers, checkpoint -> { });
            PolicyV2FullOverturnStore.Operation completed = runtime.orchestrator().execute(
                    command(
                            operationId,
                            fixture,
                            actor(fixture.actorId(), StaffRank.ADMIN),
                            "appeal-split-remedy"
                    )
            );
            assertTrue(completed.completed());
            PolicyV2Store.CaseRecord policyCase =
                    runtime.canonical().findCase(fixture.caseId()).orElseThrow();
            assertEquals(
                    PolicyV2Store.RemedyStatus.SATISFIED,
                    policyCase.remedies().get(0).status()
            );
        }

        assertConverged(fixture, operationId, providers);
    }

    @Test
    void enforcedAssetCleanupKeepsFounderRestoreBoundary() throws Exception {
        Fixture fixture = seed(DATABASE, 434);
        RecordingProviders providers = new RecordingProviders();
        UUID operationId = UUID.randomUUID();

        try (HikariDataSource dataSource = open(DATABASE)) {
            Actor admin = PolicyV2FullOverturnRuntimeTestSupport.createEnforcedAssetRemedy(
                    dataSource, fixture, SYSTEM_ACTOR, OVERTURNED_AT);
            Runtime runtime = runtime(dataSource, providers, checkpoint -> { });
            assertThrows(SecurityException.class, () -> runtime.orchestrator().execute(
                    command(operationId, fixture, admin, "appeal-asset")
            ));
            assertTrue(runtime.operations().find(operationId).isEmpty());
            assertTrue(providers.remedyEffects.isEmpty());
        }
    }

    @Test
    void stageAuditFailureRollsBackCheckpointAndRestartRecovers() throws Exception {
        Fixture fixture = prepare(435);
        RecordingProviders providers = new RecordingProviders();
        UUID operationId = UUID.randomUUID();
        failAt(fixture, operationId, providers, Checkpoint.BEFORE_FINDING_OVERTURN);
        createFullOverturnAuditFailureTrigger();
        try {
            try (HikariDataSource dataSource = open(DATABASE)) {
                Runtime runtime = runtime(dataSource, providers, checkpoint -> { });
                assertThrows(
                        ModerationPersistenceException.class,
                        () -> runtime.orchestrator().resume(operationId)
                );
                assertEquals(
                        PolicyV2FullOverturnStore.Stage.STARTED,
                        runtime.operations().find(operationId).orElseThrow().stage()
                );
                assertEquals(
                        BehavioralHistoryEntry.FindingState.OVERTURNED,
                        runtime.canonical().findCase(fixture.caseId()).orElseThrow().findingState()
                );
            }
        } finally {
            dropFullOverturnAuditFailureTrigger();
        }

        recoverAfterRestart(fixture, operationId, providers);
        assertConverged(fixture, operationId, providers);
    }

    private static void createFullOverturnAuditFailureTrigger() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER policy_v2_full_overturn_fail_audit
                    BEFORE INSERT ON policy_v2_audit_events
                    FOR EACH ROW
                    BEGIN
                        IF NEW.event_type = 'FULL_OVERTURN_FINDING_OVERTURNED' THEN
                            SIGNAL SQLSTATE '45000'
                                SET MESSAGE_TEXT = 'forced full-overturn audit failure';
                        END IF;
                    END
                    """);
        }
    }

    private static void dropFullOverturnAuditFailureTrigger() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS policy_v2_full_overturn_fail_audit");
        }
    }

    private static Fixture prepare(int sequence) throws Exception {
        Fixture fixture = seed(DATABASE, sequence);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store canonical = new JdbcPolicyV2Store(dataSource);
            PolicyV2EnforcementStore enforcement = new JdbcPolicyV2EnforcementStore(dataSource);
            PolicyV2RemedyService service = new PolicyV2RemedyService(
                    canonical,
                    enforcement,
                    new DefaultAuthorizationPolicy(),
                    SYSTEM_ACTOR
            );
            PolicyV2Store.CaseRecord policyCase = canonical.createCase(
                    createRequest(fixture, "full-overturn-case:" + sequence)
            );
            String remedyId = policyCase.remedies().get(0).remedy().id();
            Actor admin = actor(fixture.actorId(), StaffRank.ADMIN);
            PolicyV2RemedyEnforcement required = service.register(
                    admin,
                    new PolicyV2RemedyService.RegisterCommand(
                            fixture.caseId(),
                            remedyId,
                            fixture.targetId(),
                            Scope.CONTENT,
                            Condition.manual(),
                            "full-overturn-register:" + sequence,
                            OVERTURNED_AT.minusSeconds(2)
                    )
            );
            service.enforce(
                    admin,
                    fixture.caseId(),
                    remedyId,
                    required.revision(),
                    (ignored, providerOperationId) -> { },
                    "full-overturn-enforce:" + sequence,
                    OVERTURNED_AT.minusSeconds(1)
            );
        }
        return fixture;
    }

    private static void failOnceAfterDurableBegin(
            Fixture fixture,
            UUID operationId,
            RecordingProviders providers,
            Checkpoint checkpoint
    ) {
        failAt(fixture, operationId, providers, checkpoint);
    }

    private static void failAt(
            Fixture fixture,
            UUID operationId,
            RecordingProviders providers,
            Checkpoint checkpoint
    ) {
        AtomicBoolean pending = new AtomicBoolean(true);
        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, providers, current -> {
                if (current == checkpoint && pending.getAndSet(false)) {
                    throw new InjectedFailure();
                }
            });
            assertThrows(InjectedFailure.class, () -> runtime.orchestrator().execute(
                    command(
                            operationId,
                            fixture,
                            actor(fixture.actorId(), StaffRank.ADMIN),
                            "appeal-" + checkpoint.name().toLowerCase(java.util.Locale.ROOT)
                    )
            ));
            assertTrue(runtime.operations().find(operationId).isPresent());
        }
    }

    private static void recoverAfterRestart(
            Fixture fixture,
            UUID operationId,
            RecordingProviders providers
    ) {
        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, providers, checkpoint -> { });
            PolicyV2FullOverturnStore.Operation completed = runtime.orchestrator().resume(operationId);
            assertTrue(completed.completed());
            assertEquals(fixture.caseId(), completed.caseId());
        }
    }

    private static void assertConverged(
            Fixture fixture,
            UUID operationId,
            RecordingProviders providers
    ) {
        try (HikariDataSource dataSource = open(DATABASE)) {
            Runtime runtime = runtime(dataSource, providers, checkpoint -> { });
            PolicyV2Store.CaseRecord policyCase = runtime.canonical()
                    .findCase(fixture.caseId()).orElseThrow();
            assertEquals(BehavioralHistoryEntry.FindingState.OVERTURNED, policyCase.findingState());
            assertTrue(policyCase.historyEntry().contributingOffenseId().isEmpty());
            assertTrue(policyCase.currentSanctions().sanctions().isEmpty());
            assertTrue(policyCase.remedies().stream()
                    .allMatch(remedy -> remedy.status() != PolicyV2Store.RemedyStatus.REQUIRED));

            String remedyId = policyCase.remedies().get(0).remedy().id();
            assertEquals(Lifecycle.WAIVED, runtime.enforcement()
                    .find(fixture.caseId(), remedyId).orElseThrow().lifecycle());
            assertAppealAndAudit(runtime.canonical(), fixture.caseId());
            assertTrue(runtime.operations().find(operationId).orElseThrow().completed());
        }
        assertEquals(1, providers.sanctionEffects.size());
        assertEquals(1, providers.remedyEffects.size());
    }

    private static void assertAppealAndAudit(PolicyV2Store canonical, String caseId) {
        List<PolicyV2Store.AppealEvent> appeals = canonical.appealHistory(caseId, 100);
        assertEquals(1, countAppeal(appeals, PolicyV2Store.AppealEventType.APPROVED));
        assertEquals(1, countAppeal(appeals, PolicyV2Store.AppealEventType.REVISION_APPLIED));

        List<PolicyV2Store.AuditEvent> audit = canonical.auditHistory(caseId, 100);
        for (String event : List.of(
                "FULL_OVERTURN_STARTED",
                "FULL_OVERTURN_FINDING_OVERTURNED",
                "FULL_OVERTURN_SANCTIONS_TERMINATED",
                "FULL_OVERTURN_REMEDIES_CLEANED",
                "FULL_OVERTURN_COMPLETED",
                "FINDING_OVERTURNED",
                "SANCTION_REVISED",
                "REMEDY_UPDATED",
                "REMEDY_ENFORCEMENT_WAIVED"
        )) {
            assertEquals(1L, audit.stream().filter(value -> value.eventType().equals(event)).count(), event);
        }
    }

    private static long countAppeal(
            List<PolicyV2Store.AppealEvent> appeals,
            PolicyV2Store.AppealEventType type
    ) {
        return appeals.stream().filter(event -> event.eventType() == type).count();
    }

    private static PolicyV2FullOverturnOrchestrator.Command command(
            UUID operationId,
            Fixture fixture,
            Actor actor,
            String appealReference
    ) {
        return new PolicyV2FullOverturnOrchestrator.Command(
                operationId,
                fixture.caseId(),
                appealReference,
                actor,
                "Appeal fully overturned the Policy v2 finding",
                OVERTURNED_AT
        );
    }

    private static PolicyV2FullOverturnOrchestrator.Command commandAt(
            UUID operationId,
            Fixture fixture,
            Actor actor,
            String appealReference,
            Instant occurredAt
    ) {
        return new PolicyV2FullOverturnOrchestrator.Command(
                operationId,
                fixture.caseId(),
                appealReference,
                actor,
                "Appeal fully overturned the Policy v2 finding",
                occurredAt
        );
    }

    private static Runtime runtime(
            HikariDataSource dataSource,
            RecordingProviders providers,
            PolicyV2FullOverturnOrchestrator.FailureProbe failureProbe
    ) {
        return PolicyV2FullOverturnRuntimeTestSupport.runtime(
                dataSource, providers, failureProbe, SYSTEM_ACTOR);
    }

    private static final class InjectedFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
