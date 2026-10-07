package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.createRequest;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.finding;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.mute;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.open;
import static net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.seed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.CaseRevision;
import net.enthusia.staff.domain.policyv2.HistoryAssessment;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicyResolution;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.ResolutionRule;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.Fixture;
import net.enthusia.staff.persistence.JdbcPolicyV2Store;
import net.enthusia.staff.persistence.MariaDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PolicyV2W4PersistenceAdversarialIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-06T22:30:00Z");
    private static final String SPAM = "chat.spam";

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
    void twoDayLeniencyReductionLeavesFindingAndHistoryUntouched() throws Exception {
        Fixture fixture = seed(DATABASE, 12);
        try (HikariDataSource dataSource = open(DATABASE)) {
            PolicyV2Store store = new JdbcPolicyV2Store(dataSource);
            PolicyV2Store.CaseRecord created =
                    store.createCase(fiveDayCreateRequest(fixture, "create:w4-two-day-leniency"));
            List<BehavioralHistoryEntry> before =
                    store.history(fixture.targetId(), NOW.plusSeconds(60), 20);

            PolicyV2Store.SanctionRevisionRecord revised = store.reviseSanctions(
                    leniencyRequest(fixture)
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
            Set<String> references = history.stream()
                    .map(PolicyV2Store.AppealEvent::appealReference)
                    .collect(java.util.stream.Collectors.toSet());
            long appealAuditEvents = store.auditHistory(fixture.caseId(), 20).stream()
                    .filter(event -> event.eventType().startsWith("APPEAL_"))
                    .count();

            assertEquals(4, history.size());
            assertEquals(Set.of("appeal:w4:first", "appeal:w4:second"), references);
            assertEquals(4L, appealAuditEvents);
        }
    }

    private static PolicyV2Store.SanctionRevisionRequest leniencyRequest(Fixture fixture) {
        return new PolicyV2Store.SanctionRevisionRequest(
                fixture.caseId(),
                0,
                PolicyV2Store.SanctionChangeKind.LENIENCY,
                new CaseRevision.SanctionRevision(
                        "Two-day leniency reduction",
                        List.of(mute(Duration.ofDays(3)))
                ),
                fixture.actorId(),
                Optional.of("appeal:w4-leniency"),
                "sanction:w4-two-day-leniency",
                NOW.plusSeconds(1)
        );
    }

    private static PolicyV2Store.CreateCase fiveDayCreateRequest(Fixture fixture, String operationKey) {
        PolicySnapshot base = PolicyV2PersistenceTestSupport.policy();
        OffensePolicy spam = base.offense(SPAM).orElseThrow();
        ResolutionRule originalRule = spam.rules().getFirst();
        PolicyAction action = new PolicyAction.Exact(List.of(mute(Duration.ofDays(5))));
        ResolutionRule fiveDayRule = new ResolutionRule(
                originalRule.id(), originalRule.condition(), action, originalRule.remedies()
        );
        OffensePolicy fiveDaySpam = new OffensePolicy(
                spam.id(), spam.displayName(), spam.navigationGroupId(), spam.attributes(),
                spam.historyPolicy(), List.of(fiveDayRule)
        );
        PolicySnapshot snapshot = new PolicySnapshot(
                "policy-v2-w4-five-day",
                base.offenses().stream()
                        .map(offense -> offense.id().equals(SPAM) ? fiveDaySpam : offense)
                        .toList()
        );
        IncidentFinding incident = finding(SPAM);
        PolicyResolution resolution = new PolicyResolution(
                snapshot.version(), SPAM, fiveDayRule.id(), action,
                fiveDayRule.remedies(), HistoryAssessment.empty()
        );
        return new PolicyV2Store.CreateCase(
                fixture.caseId(), snapshot, incident, NOW.minusSeconds(30),
                resolution, List.of(), List.of(mute(Duration.ofDays(5))),
                fixture.actorId(), operationKey, NOW
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
                fixture.caseId(),
                appealReference,
                eventType,
                Optional.of(fixture.actorId()),
                "W4 appeal event " + sequence,
                "appeal:w4:event:" + sequence,
                NOW.plusSeconds(sequence)
        ));
    }
}
