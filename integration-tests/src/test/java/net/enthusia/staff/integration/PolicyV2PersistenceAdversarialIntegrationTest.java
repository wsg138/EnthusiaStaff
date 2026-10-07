package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.countFindingRevisions;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.countOperation;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.createAuditFailureTrigger;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.createRequest;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.dropAuditFailureTrigger;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.finding;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.mute;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.open;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.seed;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.warning;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.CaseRevision;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicyResolver;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.ResolutionRule;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.Fixture;
import net.enthusia.staff.persistence.JdbcPolicyV2Store;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PolicyV2PersistenceAdversarialIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-06T22:30:00Z");
    private static final String SPAM = "chat.spam";
    private static final String HARASSMENT = "chat.harassment";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_policy_v2_w4")
            .withUsername("staff_test")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void concurrentSanctionWritersAllowExactlyOneExpectedRevisionWinner() throws Exception {
        Fixture fixture = seed(DATABASE, 4);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            store.createCase(createRequest(fixture, "create:concurrency"));

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<String> left = executor.submit(() -> raceSanction(store, fixture, start, "left"));
                Future<String> right = executor.submit(() -> raceSanction(store, fixture, start, "right"));
                start.countDown();

                assertEquals(Set.of("APPLIED", "CONFLICT"), Set.of(left.get(), right.get()));
                assertEquals(1L, store.findCase(fixture.caseId()).orElseThrow().sanctionRevision());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void concurrentDuplicateSanctionOperationReplaysSingleRevision() throws Exception {
        Fixture fixture = seed(DATABASE, 8);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            store.createCase(createRequest(fixture, "create:duplicate-concurrency"));
            PolicyV2Store.SanctionRevisionRequest request = new PolicyV2Store.SanctionRevisionRequest(
                    fixture.caseId(), 0, PolicyV2Store.SanctionChangeKind.LENIENCY,
                    new CaseRevision.SanctionRevision("Duplicate delivery", List.of(warning())),
                    fixture.actorId(), Optional.empty(), "sanction:duplicate:8", NOW.plusSeconds(1)
            );
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<PolicyV2Store.SanctionRevisionRecord> left =
                        executor.submit(() -> concurrentSanction(store, request, start));
                Future<PolicyV2Store.SanctionRevisionRecord> right =
                        executor.submit(() -> concurrentSanction(store, request, start));
                start.countDown();

                assertEquals(left.get(), right.get());
                assertEquals(2, store.sanctionRevisions(fixture.caseId(), 10).size());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void auditFailureRollsBackFindingRevisionAndOperationJournal() throws Exception {
        Fixture fixture = seed(DATABASE, 5);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            store.createCase(createRequest(fixture, "create:rollback"));
            createAuditFailureTrigger(DATABASE);
            try {
                PolicyV2Store.FindingRevisionRequest request = new PolicyV2Store.FindingRevisionRequest(
                        fixture.caseId(), 0,
                        new CaseRevision.FindingReclassification(SPAM, HARASSMENT, "Should roll back"),
                        Optional.of(finding(HARASSMENT)), fixture.actorId(), Optional.empty(),
                        "finding:rollback:5", NOW.plusSeconds(1)
                );
                assertThrows(ModerationPersistenceException.class, () -> store.reviseFinding(request));
            } finally {
                dropAuditFailureTrigger(DATABASE);
            }

            PolicyV2Store.CaseRecord recovered = store.findCase(fixture.caseId()).orElseThrow();
            assertEquals(0L, recovered.findingRevision());
            assertEquals(SPAM, recovered.effectiveFinding().orElseThrow().offenseId());
            assertEquals(0, countFindingRevisions(DATABASE, fixture.caseId()));
            assertEquals(0, countOperation(DATABASE, "finding:rollback:5"));
        }
    }


    @Test
    void shadowEvaluationDoesNotCreateAuthoritativeCaseOrHistory() throws Exception {
        Fixture fixture = seed(DATABASE, 14);
        PolicyV2Store.CreateCase create = createRequest(fixture, "create:w4-shadow-fixture");
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            PolicyV2Store.ShadowEvaluation shadow = store.recordShadowEvaluation(
                    new PolicyV2Store.ShadowEvaluationRequest(
                            fixture.targetId(), Optional.of(fixture.caseId()), create.policySnapshot(),
                            create.finding(), create.resolution(), create.historyInputs(),
                            "shadow:w4:no-authority", NOW.plusSeconds(1)
                    )
            );

            assertEquals(shadow, store.shadowEvaluation(shadow.evaluationId()).orElseThrow());
            assertTrue(store.findCase(fixture.caseId()).isEmpty());
            assertTrue(store.history(fixture.targetId(), NOW.plusSeconds(60), 20).isEmpty());
        }
    }

    @Test
    void policyVersionCannotAliasDifferentImmutableContent() throws Exception {
        Fixture fixture = seed(DATABASE, 15);
        PolicySnapshot original = PolicyV2PersistenceTestSupport.policy();
        PolicySnapshot changed = sameVersionWithMute(original, Duration.ofHours(2));
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            PolicyV2Store.StoredSnapshot stored =
                    store.storeSnapshot(original, "snapshot:w4:original", NOW);

            assertThrows(
                    PolicyV2Store.Conflict.class,
                    () -> store.storeSnapshot(changed, "snapshot:w4:changed", NOW.plusSeconds(1))
            );
            assertEquals(original, store.loadSnapshot(stored.snapshotId()));
            assertTrue(store.findCase(fixture.caseId()).isEmpty());
        }
    }

    @Test
    void twoDayLeniencyReductionLeavesFindingAndHistoryUntouched() throws Exception {
        Fixture fixture = seed(DATABASE, 12);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            PolicyV2Store.CaseRecord created =
                    store.createCase(fiveDayCreateRequest(fixture, "create:w4-two-day-leniency"));
            List<BehavioralHistoryEntry> before =
                    store.history(fixture.targetId(), NOW.plusSeconds(60), 20);

            PolicyV2Store.SanctionRevisionRecord revised = store.reviseSanctions(
                    new PolicyV2Store.SanctionRevisionRequest(
                            fixture.caseId(), 0, PolicyV2Store.SanctionChangeKind.LENIENCY,
                            new CaseRevision.SanctionRevision(
                                    "Two-day leniency reduction",
                                    List.of(mute(Duration.ofDays(3)))
                            ),
                            fixture.actorId(), Optional.of("appeal:w4-leniency"),
                            "sanction:w4-two-day-leniency", NOW.plusSeconds(1)
                    )
            );
            PolicyV2Store.CaseRecord after = store.findCase(fixture.caseId()).orElseThrow();

            assertEquals(List.of(mute(Duration.ofDays(3))), revised.sanctions());
            assertEquals(before, store.history(fixture.targetId(), NOW.plusSeconds(60), 20));
            assertEquals(created.originalFinding(), after.originalFinding());
            assertEquals(created.effectiveFinding(), after.effectiveFinding());
            assertEquals(created.historyEntry(), after.historyEntry());
            assertEquals(0L, after.findingRevision());
            assertEquals(1L, after.sanctionRevision());
        }
    }

    @Test
    void multipleAppealsRemainSeparateAndAudited() throws Exception {
        Fixture fixture = seed(DATABASE, 13);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            store.createCase(createRequest(fixture, "create:w4-multiple-appeals"));

            appendAppeal(store, fixture, "appeal:w4:first", PolicyV2Store.AppealEventType.SUBMITTED, 1);
            appendAppeal(store, fixture, "appeal:w4:first", PolicyV2Store.AppealEventType.DENIED, 2);
            appendAppeal(store, fixture, "appeal:w4:second", PolicyV2Store.AppealEventType.SUBMITTED, 3);
            appendAppeal(store, fixture, "appeal:w4:second", PolicyV2Store.AppealEventType.APPROVED, 4);

            List<PolicyV2Store.AppealEvent> history = store.appealHistory(fixture.caseId(), 10);
            assertEquals(4, history.size());
            assertEquals(
                    Set.of("appeal:w4:first", "appeal:w4:second"),
                    history.stream().map(PolicyV2Store.AppealEvent::appealReference)
                            .collect(java.util.stream.Collectors.toSet())
            );
            assertEquals(
                    4L,
                    store.auditHistory(fixture.caseId(), 20).stream()
                            .filter(event -> event.eventType().startsWith("APPEAL_"))
                            .count()
            );
        }
    }


    private static PolicySnapshot sameVersionWithMute(PolicySnapshot base, Duration duration) {
        OffensePolicy spam = base.offense(SPAM).orElseThrow();
        ResolutionRule baseRule = spam.rules().getFirst();
        ResolutionRule changedRule = new ResolutionRule(
                baseRule.id(), baseRule.condition(),
                new PolicyAction.Exact(List.of(mute(duration))), baseRule.remedies()
        );
        OffensePolicy changedSpam = new OffensePolicy(
                spam.id(), spam.displayName(), spam.navigationGroupId(), spam.attributes(),
                spam.historyPolicy(), List.of(changedRule)
        );
        return new PolicySnapshot(
                base.version(),
                base.offenses().stream()
                        .map(offense -> offense.id().equals(SPAM) ? changedSpam : offense)
                        .toList()
        );
    }

    private static PolicyV2Store.CreateCase fiveDayCreateRequest(Fixture fixture, String operationKey) {
        PolicySnapshot base = PolicyV2PersistenceTestSupport.policy();
        OffensePolicy spam = base.offense(SPAM).orElseThrow();
        ResolutionRule baseRule = spam.rules().getFirst();
        ResolutionRule fiveDayRule = new ResolutionRule(
                baseRule.id(),
                baseRule.condition(),
                new PolicyAction.Exact(List.of(mute(Duration.ofDays(5)))),
                baseRule.remedies()
        );
        OffensePolicy fiveDaySpam = new OffensePolicy(
                spam.id(), spam.displayName(), spam.navigationGroupId(), spam.attributes(),
                spam.historyPolicy(), List.of(fiveDayRule)
        );
        PolicySnapshot snapshot = new PolicySnapshot(
                "policy-v2-w4-five-day",
                base.offenses().stream().map(offense -> offense.id().equals(SPAM) ? fiveDaySpam : offense).toList()
        );
        IncidentFinding incident = finding(SPAM);
        var resolution = new PolicyResolver().resolve(snapshot, incident, NOW.minusSeconds(30), List.of());
        return new PolicyV2Store.CreateCase(
                fixture.caseId(), snapshot, incident, NOW.minusSeconds(30), resolution, List.of(),
                List.of(mute(Duration.ofDays(5))), fixture.actorId(), operationKey, NOW
        );
    }

    private static void appendAppeal(
            PolicyV2Store store,
            Fixture fixture,
            String appealReference,
            PolicyV2Store.AppealEventType eventType,
            int sequence
    ) {
        store.appendAppealEvent(new PolicyV2Store.AppealEventRequest(
                fixture.caseId(), appealReference, eventType, Optional.of(fixture.actorId()),
                "W4 appeal event " + sequence, "appeal:w4:event:" + sequence, NOW.plusSeconds(sequence)
        ));
    }

    private static PolicyV2Store.SanctionRevisionRecord concurrentSanction(
            PolicyV2Store store,
            PolicyV2Store.SanctionRevisionRequest request,
            CountDownLatch start
    ) throws Exception {
        start.await();
        return store.reviseSanctions(request);
    }

    private static String raceSanction(
            PolicyV2Store store,
            Fixture fixture,
            CountDownLatch start,
            String suffix
    ) throws Exception {
        start.await();
        try {
            store.reviseSanctions(new PolicyV2Store.SanctionRevisionRequest(
                    fixture.caseId(), 0, PolicyV2Store.SanctionChangeKind.OTHER,
                    new CaseRevision.SanctionRevision("Concurrent " + suffix, List.of(warning())),
                    fixture.actorId(), Optional.empty(), "sanction:race:4:" + suffix, NOW.plusSeconds(1)
            ));
            return "APPLIED";
        } catch (PolicyV2Store.Conflict conflict) {
            return "CONFLICT";
        }
    }
}
