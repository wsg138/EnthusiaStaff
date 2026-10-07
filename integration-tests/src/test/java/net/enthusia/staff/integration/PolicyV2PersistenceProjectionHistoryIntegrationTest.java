package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.caseId;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.createRequest;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.mute;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.open;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.seed;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.warning;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.CaseRevision;
import net.enthusia.staff.domain.policyv2.DecayPolicy;
import net.enthusia.staff.domain.policyv2.HistoryPolicy;
import net.enthusia.staff.domain.policyv2.HistoryWindow;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicyResolution;
import net.enthusia.staff.domain.policyv2.PolicyResolver;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.ResolutionRule;
import net.enthusia.staff.domain.policyv2.RuleCondition;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.Fixture;
import net.enthusia.staff.persistence.JdbcPolicyV2Store;
import net.enthusia.staff.persistence.MariaDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PolicyV2PersistenceProjectionHistoryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-06T22:30:00Z");
    private static final String SPAM = "chat.spam";
    private static final String SEVERE = "exploit.severe";
    private static final String UNRELATED = "chat.unrelated";
    private static final int NEWER_HISTORY_CASES = 201;

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_policy_v2_projection_history")
            .withUsername("staff_test")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void remediesAppealsShadowAndPublicProjectionUseIndependentBoundaries() throws Exception {
        Fixture fixture = seed(DATABASE, 6);
        PolicyV2Store.CreateCase create = createRequest(fixture, "create:surfaces");
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            PolicyV2Store.CaseRecord created = store.createCase(create);
            assertRemedyAndAppealBoundaries(store, fixture);
            assertPublicProjectionBoundary(store, fixture, create);
            assertShadowBoundary(store, fixture, create, created);
        }
    }

    private static void assertRemedyAndAppealBoundaries(PolicyV2Store store, Fixture fixture) {
        PolicyV2Store.RemedyRecord remedy = store.updateRemedy(new PolicyV2Store.RemedyUpdateRequest(
                fixture.caseId(), "remove-message", 0, PolicyV2Store.RemedyStatus.SATISFIED,
                fixture.actorId(), "Message removed", "remedy:6", NOW.plusSeconds(1)
        ));
        assertEquals(PolicyV2Store.RemedyStatus.SATISFIED, remedy.status());

        PolicyV2Store.AppealEvent event = store.appendAppealEvent(new PolicyV2Store.AppealEventRequest(
                fixture.caseId(), "website:appeal:6", PolicyV2Store.AppealEventType.SUBMITTED,
                Optional.of(fixture.actorId()), "Appeal linked", "appeal:6", NOW.plusSeconds(2)
        ));
        assertEquals("website:appeal:6", event.appealReference());
        assertEquals(List.of(event), store.appealHistory(fixture.caseId(), 10));
        assertTrue(store.auditHistory(fixture.caseId(), 20).stream()
                .anyMatch(audit -> audit.eventType().equals("APPEAL_SUBMITTED")));
    }

    private static void assertPublicProjectionBoundary(
            PolicyV2Store store,
            Fixture fixture,
            PolicyV2Store.CreateCase create
    ) {
        PolicyV2PublicProjection projection = new PolicyV2PublicProjection(
                fixture.caseId(), "Chat spam", "Repeated disruptive chat",
                PolicyV2PublicProjection.Status.ACTIVE, create.incidentAt(),
                List.of(new PolicyV2PublicProjection.PublicSanction(
                        SanctionType.MUTE,
                        PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE,
                        Optional.of(NOW.plus(Duration.ofHours(1)))
                )),
                0
        );
        assertEquals(projection, store.publishProjection(new PolicyV2Store.PublishProjectionRequest(
                projection, -1, "public:6", NOW.plusSeconds(3)
        )));
        assertEquals(projection, store.publicProjection(fixture.caseId()).orElseThrow());
        assertPublicProjectionFields();
    }

    private static void assertShadowBoundary(
            PolicyV2Store store,
            Fixture fixture,
            PolicyV2Store.CreateCase create,
            PolicyV2Store.CaseRecord created
    ) {
        PolicyV2Store.ShadowEvaluationRequest request = new PolicyV2Store.ShadowEvaluationRequest(
                fixture.targetId(), Optional.of(fixture.caseId()), create.policySnapshot(),
                create.finding(), create.resolution(), create.historyInputs(),
                "shadow:6", NOW.plusSeconds(4)
        );
        PolicyV2Store.ShadowEvaluation shadow = store.recordShadowEvaluation(request);
        PolicyV2Store.ShadowEvaluation replay = store.recordShadowEvaluation(request);
        assertEquals(shadow.evaluationId(), replay.evaluationId());
        assertEquals(shadow, store.shadowEvaluation(shadow.evaluationId()).orElseThrow());
        assertEquals(created.policySnapshotId(), shadow.policySnapshotId());
    }

    private static void assertPublicProjectionFields() {
        Set<String> publicFields = java.util.Arrays.stream(
                        PolicyV2PublicProjection.class.getRecordComponents()
                )
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(
                Set.of(
                        "caseId", "currentPlayerName", "incidentPlayerName", "category",
                        "publicOffense", "publicReason", "relatedHistorySummary", "status",
                        "appealStatus", "issuedAt", "expiresAt", "sanctions", "remedies",
                        "timeline", "policyVersion", "revision"
                ),
                publicFields
        );
    }

    @Test
    void retainedW2PublicProjectionDecodesThroughCurrentStore() throws Exception {
        Fixture fixture = seed(DATABASE, 11);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            store.createCase(createRequest(fixture, "create:legacy-public-projection"));

            String legacyJson = """
                    {"caseId":"%s","publicOffense":"Legacy offense",
                    "publicReason":"Legacy public reason","status":"RESOLVED",
                    "incidentAt":"%s","sanctions":[
                    {"type":"NETWORK_IDENTITY_BAN","status":"COMPLETED","endsAt":null},
                    {"type":"CONTENT_REMOVAL","status":"COMPLETED","endsAt":null}],
                    "revision":0}
                    """.formatted(fixture.caseId(), NOW);
            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement("""
                         INSERT INTO policy_v2_public_projections(
                             case_id, projection_json, revision, updated_at
                         ) VALUES (?, ?, 0, ?)
                         """)) {
                statement.setString(1, fixture.caseId());
                statement.setString(2, legacyJson);
                statement.setTimestamp(3, Timestamp.from(NOW));
                statement.executeUpdate();
            }

            PolicyV2PublicProjection projection =
                    store.publicProjection(fixture.caseId()).orElseThrow();
            assertEquals(PolicyV2PublicProjection.Status.EXPIRED, projection.status());
            assertEquals(NOW, projection.issuedAt());
            assertEquals("Legacy offense", projection.publicOffense());
            assertEquals(1, projection.sanctions().size());
            assertEquals(
                    PolicyV2PublicProjection.PublicSanctionType.BAN,
                    projection.sanctions().getFirst().type()
            );
        }
    }

    @Test
    void legacyV1CaseWithoutV2MetadataRemainsUnknown() throws Exception {
        Fixture fixture = seed(DATABASE, 7);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            assertTrue(store.findCase(fixture.caseId()).isEmpty());
            assertTrue(store.completeHistory(fixture.targetId(), NOW.plusSeconds(60)).isEmpty());
        }
    }

    @Test
    void completeHistoryKeepsOldNonDecayingSevereFindingBeyondTwoHundredNewerCases() throws Exception {
        Fixture subject = seed(DATABASE, 11);
        PolicySnapshot snapshot = completeHistoryPolicy();
        List<BehavioralHistoryEntry> directHistory = new ArrayList<>();
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            directHistory.add(createHistoryCase(
                    store, subject, snapshot, 1000, SEVERE, NOW.minus(Duration.ofDays(1000))
            ));
            for (int index = 0; index < NEWER_HISTORY_CASES; index++) {
                directHistory.add(createHistoryCase(
                        store, subject, snapshot, 1001 + index, UNRELATED,
                        NOW.minus(Duration.ofDays(200)).plusSeconds(index)
                ));
            }
            assertCompleteHistoryResolverResult(store, subject, snapshot, directHistory);
        }
    }

    @Test
    void privilegedRevisionRequestsRejectMissingActorIdentityBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> new PolicyV2Store.FindingRevisionRequest(
                caseId(99),
                0,
                new CaseRevision.FindingOverturn(SPAM, "Invalid actor"),
                Optional.empty(),
                null,
                Optional.empty(),
                "finding:unauthorized",
                NOW
        ));
    }

    private static void assertCompleteHistoryResolverResult(
            PolicyV2Store store,
            Fixture subject,
            PolicySnapshot snapshot,
            List<BehavioralHistoryEntry> directHistory
    ) {
        List<BehavioralHistoryEntry> complete = store.completeHistory(subject.targetId(), NOW);
        List<BehavioralHistoryEntry> bounded = store.history(subject.targetId(), NOW, 200);
        PolicyResolver resolver = new PolicyResolver();
        IncidentFinding current = new IncidentFinding(SPAM, Map.of());
        PolicyResolution expected = resolver.resolve(snapshot, current, NOW, directHistory);
        PolicyResolution actual = resolver.resolve(snapshot, current, NOW, complete);
        PolicyResolution truncated = resolver.resolve(snapshot, current, NOW, bounded);

        assertEquals(202, complete.size());
        assertEquals(202L, complete.stream().map(BehavioralHistoryEntry::caseId).distinct().count());
        assertEquals(directHistory, complete);
        assertEquals(expected, actual);
        assertEquals("severe-history", actual.matchedRuleId());
        assertEquals("first", truncated.matchedRuleId());
        assertTrue(bounded.stream().noneMatch(entry -> entry.caseId().equals(directHistory.getFirst().caseId())));
    }

    private static BehavioralHistoryEntry createHistoryCase(
            PolicyV2Store store,
            Fixture subject,
            PolicySnapshot snapshot,
            int sequence,
            String offenseId,
            Instant incidentAt
    ) throws Exception {
        String id = caseId(sequence);
        MariaDbIntegrationSupport.insertCase(DATABASE, id, subject.targetId(), subject.actorId(), incidentAt);
        IncidentFinding incident = new IncidentFinding(offenseId, Map.of());
        PolicyResolution resolution = new PolicyResolver().resolve(snapshot, incident, incidentAt, List.of());
        PolicyV2Store.CaseRecord created = store.createCase(new PolicyV2Store.CreateCase(
                id, snapshot, incident, incidentAt, resolution, List.of(), List.of(warning()),
                subject.actorId(), "create:complete-history:" + sequence, NOW
        ));
        return created.historyEntry();
    }

    private static PolicySnapshot completeHistoryPolicy() {
        OffensePolicy current = new OffensePolicy(
                SPAM, "Spam", "chat", List.of(),
                new HistoryPolicy(Map.of(SEVERE, 1.0), DecayPolicy.nonDecaying()),
                List.of(
                        resolutionRule("first", new HistoryWindow(0.0, 1.0), warning()),
                        resolutionRule("severe-history", HistoryWindow.atLeast(1.0), mute(Duration.ofDays(1)))
                )
        );
        return new PolicySnapshot(
                "policy-v2-complete-history-test",
                List.of(current, historyOnlyOffense(SEVERE), historyOnlyOffense(UNRELATED))
        );
    }

    private static OffensePolicy historyOnlyOffense(String offenseId) {
        return new OffensePolicy(
                offenseId, offenseId, "history-test", List.of(),
                new HistoryPolicy(Map.of(offenseId, 1.0), DecayPolicy.nonDecaying()),
                List.of(resolutionRule("base", HistoryWindow.atLeast(0.0), warning()))
        );
    }

    private static ResolutionRule resolutionRule(
            String id,
            HistoryWindow history,
            net.enthusia.staff.domain.sanction.SanctionSpec sanction
    ) {
        return new ResolutionRule(
                id,
                new RuleCondition(Map.of(), history),
                new PolicyAction.Exact(List.of(sanction)),
                List.of()
        );
    }


}
