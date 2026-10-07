package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.caseId;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.countFindingAuditEvents;
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
import net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.Fixture;
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
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.persistence.JdbcPolicyV2Store;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PolicyV2PersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-06T22:30:00Z");
    private static final String SPAM = "chat.spam";
    private static final String HARASSMENT = "chat.harassment";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_policy_v2")
            .withUsername("staff_test")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void roundTripRestartAndIdempotencyPreserveImmutableInputs() throws Exception {
        Fixture fixture = seed(DATABASE, 1);
        PolicyV2Store.CreateCase request = createRequest(fixture, "create:roundtrip");

        PolicyV2Store.CaseRecord created;
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            created = store.createCase(request);
            PolicyV2Store.CaseRecord replay = store.createCase(request);

            assertEquals(created.resolutionId(), replay.resolutionId());
            assertEquals(request.finding(), created.originalFinding());
            assertEquals(Optional.of(request.finding()), created.effectiveFinding());
            assertEquals(request.resolution(), created.resolution());
            assertEquals(request.historyInputs(), created.historyInputs());
            assertEquals(request.policySnapshot(), store.loadSnapshot(created.policySnapshotId()));
            assertEquals(0L, created.findingRevision());
            assertEquals(0L, created.sanctionRevision());

            PolicyV2Store.CreateCase collision = new PolicyV2Store.CreateCase(
                    request.caseId(),
                    request.policySnapshot(),
                    request.finding(),
                    request.incidentAt(),
                    request.resolution(),
                    request.historyInputs(),
                    List.of(warning()),
                    request.actorId(),
                    request.operationKey(),
                    request.recordedAt()
            );
            assertThrows(PolicyV2Store.Conflict.class, () -> store.createCase(collision));
        }

        try (HikariDataSource restarted = open(DATABASE)) {
            PolicyV2Store.CaseRecord recovered = new JdbcPolicyV2Store(restarted)
                    .findCase(fixture.caseId())
                    .orElseThrow();
            assertEquals(created.resolutionId(), recovered.resolutionId());
            assertEquals(created.originalFinding(), recovered.originalFinding());
            assertEquals(created.currentSanctions(), recovered.currentSanctions());
        }
    }

    @Test
    void duplicateCaseWithDifferentOperationKeyReturnsConflict() throws Exception {
        Fixture fixture = seed(DATABASE, 10);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            store.createCase(createRequest(fixture, "create:duplicate-case:first"));

            PolicyV2Store.CreateCase duplicate =
                    createRequest(fixture, "create:duplicate-case:second");
            assertThrows(PolicyV2Store.Conflict.class, () -> store.createCase(duplicate));
        }
    }

    @Test
    void leniencyRevisesSanctionsWithoutChangingBehavioralHistory() throws Exception {
        Fixture fixture = seed(DATABASE, 2);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            PolicyV2Store.CaseRecord created = store.createCase(createRequest(fixture, "create:leniency"));
            List<BehavioralHistoryEntry> before = store.completeHistory(fixture.targetId(), NOW.plusSeconds(60));

            PolicyV2Store.SanctionRevisionRequest request = new PolicyV2Store.SanctionRevisionRequest(
                    fixture.caseId(),
                    0,
                    PolicyV2Store.SanctionChangeKind.LENIENCY,
                    new CaseRevision.SanctionRevision("Accepted appeal leniency", List.of(mute(Duration.ofMinutes(10)))),
                    fixture.actorId(),
                    Optional.of("appeal:leniency:2"),
                    "sanction:leniency:2",
                    NOW.plusSeconds(1)
            );
            PolicyV2Store.SanctionRevisionRecord revised = store.reviseSanctions(request);
            PolicyV2Store.SanctionRevisionRecord replay = store.reviseSanctions(request);
            List<BehavioralHistoryEntry> after = store.completeHistory(fixture.targetId(), NOW.plusSeconds(60));
            PolicyV2Store.CaseRecord caseAfter = store.findCase(fixture.caseId()).orElseThrow();

            assertEquals(1L, revised.revision());
            assertEquals(revised, replay);
            assertEquals(List.of(revised, created.currentSanctions()), store.sanctionRevisions(fixture.caseId(), 10));
            assertEquals(before, after);
            assertEquals(0L, caseAfter.findingRevision());
            assertEquals(1L, caseAfter.sanctionRevision());
            assertEquals(created.historyEntry(), caseAfter.historyEntry());

            PolicyV2Store.SanctionRevisionRequest collision = new PolicyV2Store.SanctionRevisionRequest(
                    fixture.caseId(),
                    0,
                    PolicyV2Store.SanctionChangeKind.LENIENCY,
                    new CaseRevision.SanctionRevision("Different request", List.of()),
                    fixture.actorId(),
                    Optional.of("appeal:leniency:2"),
                    request.operationKey(),
                    request.occurredAt()
            );
            assertThrows(PolicyV2Store.Conflict.class, () -> store.reviseSanctions(collision));
        }
    }

    @Test
    void reclassificationChangesFutureHistoryAndOverturnStopsContribution() throws Exception {
        Fixture fixture = seed(DATABASE, 3);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            store.createCase(createRequest(fixture, "create:finding-revision"));

            IncidentFinding replacement = finding(HARASSMENT);
            PolicyV2Store.CaseRecord reclassified = store.reviseFinding(
                    new PolicyV2Store.FindingRevisionRequest(
                            fixture.caseId(),
                            0,
                            new CaseRevision.FindingReclassification(SPAM, HARASSMENT, "Factual correction"),
                            Optional.of(replacement),
                            fixture.actorId(),
                            Optional.of("appeal:facts:3"),
                            "finding:reclassify:3",
                            NOW.plusSeconds(1)
                    )
            );
            BehavioralHistoryEntry reclassifiedHistory = onlyHistory(store, fixture.targetId());

            assertEquals(BehavioralHistoryEntry.FindingState.RECLASSIFIED, reclassified.findingState());
            assertEquals(HARASSMENT, reclassifiedHistory.contributingOffenseId().orElseThrow());
            assertEquals(SPAM, reclassifiedHistory.originalOffenseId());

            PolicyV2Store.CaseRecord overturned = store.reviseFinding(
                    new PolicyV2Store.FindingRevisionRequest(
                            fixture.caseId(),
                            1,
                            new CaseRevision.FindingOverturn(HARASSMENT, "Appeal disproved the violation"),
                            Optional.empty(),
                            fixture.actorId(),
                            Optional.of("appeal:facts:3"),
                            "finding:overturn:3",
                            NOW.plusSeconds(2)
                    )
            );
            BehavioralHistoryEntry overturnedHistory = onlyHistory(store, fixture.targetId());

            assertEquals(BehavioralHistoryEntry.FindingState.OVERTURNED, overturned.findingState());
            assertTrue(overturned.effectiveFinding().isEmpty());
            assertTrue(overturnedHistory.contributingOffenseId().isEmpty());
            List<PolicyV2Store.FindingRevisionRecord> revisions = store.findingRevisions(fixture.caseId(), 10);
            assertEquals(2, revisions.size());
            assertEquals(PolicyV2Store.FindingChangeKind.OVERTURN, revisions.get(0).changeKind());
            assertEquals(PolicyV2Store.FindingChangeKind.RECLASSIFICATION, revisions.get(1).changeKind());
            assertEquals(2, countFindingRevisions(DATABASE, fixture.caseId()));
            assertEquals(2, countFindingAuditEvents(DATABASE, fixture.caseId()));
        }
    }

    @Test
    void reclassificationBackToOriginalRestoresConfirmedEffectiveHistory() throws Exception {
        Fixture fixture = seed(DATABASE, 9);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            store.createCase(createRequest(fixture, "create:reclassify-back"));
            store.reviseFinding(new PolicyV2Store.FindingRevisionRequest(
                    fixture.caseId(), 0,
                    new CaseRevision.FindingReclassification(SPAM, HARASSMENT, "First correction"),
                    Optional.of(finding(HARASSMENT)), fixture.actorId(), Optional.empty(),
                    "finding:reclassify-out:9", NOW.plusSeconds(1)
            ));
            PolicyV2Store.CaseRecord restored = store.reviseFinding(
                    new PolicyV2Store.FindingRevisionRequest(
                            fixture.caseId(), 1,
                            new CaseRevision.FindingReclassification(HARASSMENT, SPAM, "Correction restored"),
                            Optional.of(finding(SPAM)), fixture.actorId(), Optional.empty(),
                            "finding:reclassify-back:9", NOW.plusSeconds(2)
                    )
            );

            assertEquals(BehavioralHistoryEntry.FindingState.CONFIRMED, restored.findingState());
            assertEquals(SPAM, restored.historyEntry().contributingOffenseId().orElseThrow());
            assertEquals(2, store.findingRevisions(fixture.caseId(), 10).size());
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
                Set<String> outcomes = Set.of(left.get(), right.get());

                assertEquals(Set.of("APPLIED", "CONFLICT"), outcomes);
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
                        fixture.caseId(),
                        0,
                        new CaseRevision.FindingReclassification(SPAM, HARASSMENT, "Should roll back"),
                        Optional.of(finding(HARASSMENT)),
                        fixture.actorId(),
                        Optional.empty(),
                        "finding:rollback:5",
                        NOW.plusSeconds(1)
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
                    fixture.caseId(),
                    0,
                    PolicyV2Store.SanctionChangeKind.OTHER,
                    new CaseRevision.SanctionRevision("Concurrent " + suffix, List.of(warning())),
                    fixture.actorId(),
                    Optional.empty(),
                    "sanction:race:4:" + suffix,
                    NOW.plusSeconds(1)
            ));
            return "APPLIED";
        } catch (PolicyV2Store.Conflict conflict) {
            return "CONFLICT";
        }
    }

    private static BehavioralHistoryEntry onlyHistory(PolicyV2Store store, UUID targetId) {
        List<BehavioralHistoryEntry> history = store.completeHistory(targetId, NOW.plusSeconds(60));
        assertEquals(1, history.size());
        return history.get(0);
    }

}
