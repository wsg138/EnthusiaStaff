package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionStatus;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SanctionLifecycleIntegrationTest {
    private static final String USERNAME = "sanction_lifecycle_user";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final Actor MODERATOR = new Actor(uuid(900), "Moderator", StaffRank.MOD);
    private static final SanctionActionLimits DEFAULT_LIMITS = SanctionActionLimits.defaults();

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_sanction_lifecycle_test")
            .withUsername(USERNAME)
            .withPassword(PASSWORD);

    @BeforeAll
    static void migrateSchema() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            assertNotNull(runtime.sanctionMutationStore());
        }
    }

    @BeforeEach
    void clearFixtures() throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection()) {
            for (String table : List.of(
                    "network_outbox_deliveries",
                    "network_outbox",
                    "discord_outbox",
                    "audit_events",
                    "sanction_events",
                    "website_appeal_requests",
                    "punishment_request_events",
                    "punishment_requests",
                    "punishment_overturn_requests",
                    "staff_notes",
                    "sanction_links",
                    "sanctions",
                    "punishment_steps",
                    "cases",
                    "player_names",
                    "players"
            )) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table)) { // nosemgrep
                    statement.executeUpdate();
                }
            }
            setMode(connection, "ACTIVE");
        }
    }

    @Test
    void reductionPreservesOriginalRecordAndReplaysAcrossRestart() throws Exception {
        Instant issued = Instant.now().minusSeconds(3_600);
        Instant originalExpiration = Instant.now().plusSeconds(7_200);
        Instant reducedExpiration = Instant.now().plusSeconds(3_600);
        Fixture fixture = seed(1, "HELPER", SanctionStatus.ACTIVE, issued, Optional.of(originalExpiration));
        ExactSanctionChangeRequest request = request(
                fixture,
                0,
                SanctionChangeAction.REDUCE_DURATION,
                Optional.of(reducedExpiration),
                "Reduce after staff review",
                "reduce-restart",
                Optional.empty()
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ExactSanctionChangeResult.Applied applied = assertInstanceOf(
                    ExactSanctionChangeResult.Applied.class,
                    runtime.sanctionMutationStore().applyExact(request, DEFAULT_LIMITS)
            );
            assertFalse(applied.replayed());
            assertEquals(SanctionStatus.ACTIVE, applied.previousStatus());
            assertEquals(SanctionStatus.ACTIVE, applied.resultingStatus());
            assertEquals(originalExpiration, applied.previousExpiration().orElseThrow());
            assertEquals(reducedExpiration, applied.resultingExpiration().orElseThrow());
        }

        assertEquals(issued, instantValue(
                "SELECT issued_at FROM sanctions WHERE sanction_id=?",
                fixture.sanctionId()
        ));
        assertEquals(reducedExpiration, instantValue(
                "SELECT expiration_at FROM sanctions WHERE sanction_id=?",
                fixture.sanctionId()
        ));
        assertEquals(1, count("sanction_events"));
        assertEquals(1, count("audit_events"));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ExactSanctionChangeResult.Applied replay = assertInstanceOf(
                    ExactSanctionChangeResult.Applied.class,
                    runtime.sanctionMutationStore().applyExact(request, DEFAULT_LIMITS)
            );
            assertTrue(replay.replayed());
        }
        assertEquals(1, count("sanction_events"));
        assertEquals(1, count("audit_events"));
    }

    @Test
    void reductionRejectsExtensionsAndIdenticalExpirationsWithoutAuditNoise() throws Exception {
        Instant issued = Instant.now().minusSeconds(3_600);
        Instant expiration = Instant.now().plusSeconds(3_600);
        Fixture fixture = seed(2, "HELPER", SanctionStatus.ACTIVE, issued, Optional.of(expiration));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ExactSanctionChangeResult.Rejected extension = assertInstanceOf(
                    ExactSanctionChangeResult.Rejected.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            fixture,
                            0,
                            SanctionChangeAction.REDUCE_DURATION,
                            Optional.of(expiration.plusSeconds(60)),
                            "This must not extend",
                            "reduce-extension",
                            Optional.empty()
                    ), DEFAULT_LIMITS)
            );
            assertEquals("NOT_A_REDUCTION", extension.code());

            ExactSanctionChangeResult.NoChange identical = assertInstanceOf(
                    ExactSanctionChangeResult.NoChange.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            fixture,
                            0,
                            SanctionChangeAction.REDUCE_DURATION,
                            Optional.of(expiration),
                            "Identical retry with another key",
                            "reduce-identical",
                            Optional.empty()
                    ), DEFAULT_LIMITS)
            );
            assertEquals("NO_CHANGE", identical.code());
        }
        assertEquals(0, count("sanction_events"));
        assertEquals(0, count("audit_events"));
    }

    @Test
    void permanentToFiniteReductionFollowsConfiguredPolicy() throws Exception {
        Fixture fixture = seed(
                3,
                "HELPER",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.empty()
        );
        Instant replacement = Instant.now().plusSeconds(3_600);
        ExactSanctionChangeRequest request = request(
                fixture,
                0,
                SanctionChangeAction.REDUCE_DURATION,
                Optional.of(replacement),
                "Convert permanent sanction to finite",
                "reduce-permanent",
                Optional.empty()
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ExactSanctionChangeResult.Rejected denied = assertInstanceOf(
                    ExactSanctionChangeResult.Rejected.class,
                    runtime.sanctionMutationStore().applyExact(
                            request,
                            new SanctionActionLimits(3, 500, false)
                    )
            );
            assertEquals("PERMANENT_REDUCTION_DENIED", denied.code());

            ExactSanctionChangeResult.Applied applied = assertInstanceOf(
                    ExactSanctionChangeResult.Applied.class,
                    runtime.sanctionMutationStore().applyExact(request, DEFAULT_LIMITS)
            );
            assertEquals(replacement, applied.resultingExpiration().orElseThrow());
        }
    }

    @Test
    void endEarlyRecordsActualEndAndReturnsNoOpWhenAlreadyInactive() throws Exception {
        Fixture fixture = seed(
                4,
                "HELPER",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.of(Instant.now().plusSeconds(3_600))
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ExactSanctionChangeResult.Applied applied = assertInstanceOf(
                    ExactSanctionChangeResult.Applied.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            fixture,
                            0,
                            SanctionChangeAction.END_EARLY,
                            Optional.empty(),
                            "End immediately after review",
                            "end-first",
                            Optional.empty()
                    ), DEFAULT_LIMITS)
            );
            assertEquals(SanctionStatus.ENDED_EARLY, applied.resultingStatus());
            assertNotNull(instantValue("SELECT ended_at FROM sanctions WHERE sanction_id=?", fixture.sanctionId()));

            ExactSanctionChangeResult.NoChange noChange = assertInstanceOf(
                    ExactSanctionChangeResult.NoChange.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            fixture,
                            1,
                            SanctionChangeAction.END_EARLY,
                            Optional.empty(),
                            "Second early-end attempt",
                            "end-second",
                            Optional.empty()
                    ), DEFAULT_LIMITS)
            );
            assertEquals("ALREADY_INACTIVE", noChange.code());
        }
        assertEquals(1, count("sanction_events"));
    }

    @Test
    void revokeAndOverturnRemainDistinctTerminalStates() throws Exception {
        Fixture revoked = seed(
                5,
                "HELPER",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.of(Instant.now().plusSeconds(3_600))
        );
        Fixture overturned = seed(
                6,
                "HELPER",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.of(Instant.now().plusSeconds(3_600))
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            assertEquals(SanctionStatus.REVOKED, assertInstanceOf(
                    ExactSanctionChangeResult.Applied.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            revoked,
                            0,
                            SanctionChangeAction.REVOKE,
                            Optional.empty(),
                            "Administratively withdraw sanction",
                            "revoke-first",
                            Optional.empty()
                    ), DEFAULT_LIMITS)
            ).resultingStatus());
            assertEquals("ALREADY_REVOKED", assertInstanceOf(
                    ExactSanctionChangeResult.NoChange.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            revoked,
                            1,
                            SanctionChangeAction.REVOKE,
                            Optional.empty(),
                            "Duplicate revocation attempt",
                            "revoke-second",
                            Optional.empty()
                    ), DEFAULT_LIMITS)
            ).code());

            assertEquals(SanctionStatus.OVERTURNED, assertInstanceOf(
                    ExactSanctionChangeResult.Applied.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            overturned,
                            0,
                            SanctionChangeAction.FULL_OVERTURN,
                            Optional.empty(),
                            "Reverse the punishment decision",
                            "overturn-first",
                            Optional.empty()
                    ), DEFAULT_LIMITS)
            ).resultingStatus());
            assertEquals("ALREADY_OVERTURNED", assertInstanceOf(
                    ExactSanctionChangeResult.NoChange.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            overturned,
                            1,
                            SanctionChangeAction.FULL_OVERTURN,
                            Optional.empty(),
                            "Duplicate overturn attempt",
                            "overturn-second",
                            Optional.empty()
                    ), DEFAULT_LIMITS)
            ).code());
        }
        assertEquals("REVOKED", stringValue("SELECT status FROM sanctions WHERE sanction_id=?", revoked.sanctionId()));
        assertEquals("OVERTURNED", stringValue(
                "SELECT status FROM sanctions WHERE sanction_id=?",
                overturned.sanctionId()
        ));
        assertEquals("FULLY_OVERTURNED", stringValue(
                "SELECT state FROM cases WHERE case_id=?",
                overturned.caseId().value()
        ));
        assertFalse(booleanValue(
                "SELECT escalation_contributes FROM punishment_steps WHERE case_id=?",
                overturned.caseId().value()
        ));
    }

    @Test
    void appealLinkedOverturnValidatesBindingAndPreventsReuse() throws Exception {
        Fixture fixture = seed(
                7,
                "HELPER",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.of(Instant.now().plusSeconds(3_600))
        );
        Fixture other = seed(
                8,
                "HELPER",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.of(Instant.now().plusSeconds(3_600))
        );
        UUID appealId = insertAppeal(other, "APPLIED");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ExactSanctionChangeResult.Rejected mismatch = assertInstanceOf(
                    ExactSanctionChangeResult.Rejected.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            fixture,
                            0,
                            SanctionChangeAction.FULL_OVERTURN,
                            Optional.empty(),
                            "Appeal belongs to a different punishment",
                            "appeal-mismatch",
                            Optional.of(appealId)
                    ), DEFAULT_LIMITS)
            );
            assertEquals("APPEAL_TARGET_MISMATCH", mismatch.code());

            ExactSanctionChangeResult.Applied applied = assertInstanceOf(
                    ExactSanctionChangeResult.Applied.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            other,
                            0,
                            SanctionChangeAction.FULL_OVERTURN,
                            Optional.empty(),
                            "Accepted appeal reverses punishment",
                            "appeal-valid",
                            Optional.of(appealId)
                    ), DEFAULT_LIMITS)
            );
            assertEquals(appealId, applied.linkedAppealId().orElseThrow());

            ExactSanctionChangeResult.NoChange reused = assertInstanceOf(
                    ExactSanctionChangeResult.NoChange.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            other,
                            1,
                            SanctionChangeAction.FULL_OVERTURN,
                            Optional.empty(),
                            "Do not link the appeal twice",
                            "appeal-reused",
                            Optional.of(appealId)
                    ), DEFAULT_LIMITS)
            );
            assertEquals("APPEAL_ALREADY_LINKED", reused.code());
        }
        assertEquals(1, longValue(
                "SELECT COUNT(*) FROM sanction_events WHERE linked_appeal_id=?",
                appealId
        ));
    }

    @Test
    void hierarchyAndDurableOperationalModeAreRecheckedInsideTheStoreBoundary() throws Exception {
        Fixture adminIssued = seed(
                9,
                "ADMIN",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.of(Instant.now().plusSeconds(3_600))
        );
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ExactSanctionChangeResult.Rejected hierarchy = assertInstanceOf(
                    ExactSanctionChangeResult.Rejected.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            adminIssued,
                            0,
                            SanctionChangeAction.REVOKE,
                            Optional.empty(),
                            "Moderator cannot alter admin sanction",
                            "hierarchy-denied",
                            Optional.empty()
                    ), DEFAULT_LIMITS)
            );
            assertEquals("HIERARCHY_DENIED", hierarchy.code());
        }

        setMode("SHADOW_MIGRATION");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ExactSanctionChangeResult.Rejected blocked = assertInstanceOf(
                    ExactSanctionChangeResult.Rejected.class,
                    runtime.sanctionMutationStore().applyExact(request(
                            adminIssued,
                            0,
                            SanctionChangeAction.REVOKE,
                            Optional.empty(),
                            "Authority fence must reject",
                            "mode-denied",
                            Optional.empty()
                    ), DEFAULT_LIMITS)
            );
            assertEquals("MODE_BLOCKED", blocked.code());
        }
        assertEquals(0, count("sanction_events"));
    }

    @Test
    void auditFailureRollsBackSanctionEventAndOutboxes() throws Exception {
        Fixture fixture = seed(
                10,
                "HELPER",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.of(Instant.now().plusSeconds(3_600))
        );
        execute("""
                CREATE TRIGGER fail_exact_sanction_audit
                BEFORE INSERT ON audit_events
                FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced audit failure'
                """);
        try {
            try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
                assertThrows(ModerationPersistenceException.class, () ->
                        runtime.sanctionMutationStore().applyExact(request(
                                fixture,
                                0,
                                SanctionChangeAction.REVOKE,
                                Optional.empty(),
                                "This transaction must roll back",
                                "audit-rollback",
                                Optional.empty()
                        ), DEFAULT_LIMITS)
                );
            }
        } finally {
            execute("DROP TRIGGER IF EXISTS fail_exact_sanction_audit");
        }
        assertEquals("ACTIVE", stringValue("SELECT status FROM sanctions WHERE sanction_id=?", fixture.sanctionId()));
        assertEquals(0, count("sanction_events"));
        assertEquals(0, count("audit_events"));
        assertEquals(0, count("network_outbox"));
        assertEquals(0, count("discord_outbox"));
    }

    @Test
    void concurrentReductionsCommitOneObservedRevision() throws Exception {
        Fixture fixture = seed(
                11,
                "HELPER",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.of(Instant.now().plusSeconds(7_200))
        );
        List<ExactSanctionChangeResult> results = race(
                request(
                        fixture,
                        0,
                        SanctionChangeAction.REDUCE_DURATION,
                        Optional.of(Instant.now().plusSeconds(3_600)),
                        "First concurrent reduction",
                        "concurrent-reduce-a",
                        Optional.empty()
                ),
                request(
                        fixture,
                        0,
                        SanctionChangeAction.REDUCE_DURATION,
                        Optional.of(Instant.now().plusSeconds(1_800)),
                        "Second concurrent reduction",
                        "concurrent-reduce-b",
                        Optional.empty()
                ),
                false
        );
        assertOneAppliedOneStale(results);
        assertEquals(1, count("sanction_events"));
    }

    @Test
    void reductionRacingWithEarlyEndCommitsOnlyOneObservedRevision() throws Exception {
        Fixture fixture = seed(
                12,
                "HELPER",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.of(Instant.now().plusSeconds(7_200))
        );
        List<ExactSanctionChangeResult> results = race(
                request(
                        fixture,
                        0,
                        SanctionChangeAction.REDUCE_DURATION,
                        Optional.of(Instant.now().plusSeconds(3_600)),
                        "Concurrent reduction",
                        "concurrent-reduce-end-a",
                        Optional.empty()
                ),
                request(
                        fixture,
                        0,
                        SanctionChangeAction.END_EARLY,
                        Optional.empty(),
                        "Concurrent early end",
                        "concurrent-reduce-end-b",
                        Optional.empty()
                ),
                false
        );
        assertOneAppliedOneStale(results);
        assertEquals(1, count("sanction_events"));
    }

    @Test
    void revokeRacingWithOverturnAcrossRuntimesCannotCreateContradictoryTerminalStates()
            throws Exception {
        Fixture fixture = seed(
                13,
                "HELPER",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.of(Instant.now().plusSeconds(7_200))
        );
        List<ExactSanctionChangeResult> results = race(
                request(
                        fixture,
                        0,
                        SanctionChangeAction.REVOKE,
                        Optional.empty(),
                        "Concurrent revocation",
                        "concurrent-terminal-a",
                        Optional.empty()
                ),
                request(
                        fixture,
                        0,
                        SanctionChangeAction.FULL_OVERTURN,
                        Optional.empty(),
                        "Concurrent overturn",
                        "concurrent-terminal-b",
                        Optional.empty()
                ),
                true
        );
        assertOneAppliedOneStale(results);
        String status = stringValue("SELECT status FROM sanctions WHERE sanction_id=?", fixture.sanctionId());
        assertTrue(status.equals("REVOKED") || status.equals("OVERTURNED"));
        assertEquals(1, count("sanction_events"));
        assertEquals(1, count("audit_events"));
    }

    @Test
    void identicalConcurrentRetryCreatesOneEventAndOneReplay() throws Exception {
        Fixture fixture = seed(
                14,
                "HELPER",
                SanctionStatus.ACTIVE,
                Instant.now().minusSeconds(3_600),
                Optional.of(Instant.now().plusSeconds(7_200))
        );
        ExactSanctionChangeRequest identical = request(
                fixture,
                0,
                SanctionChangeAction.REVOKE,
                Optional.empty(),
                "Identical concurrent retry",
                "concurrent-identical",
                Optional.empty()
        );
        List<ExactSanctionChangeResult> results = race(identical, identical, true);
        assertEquals(2, results.stream().filter(ExactSanctionChangeResult.Applied.class::isInstance).count());
        assertEquals(1, results.stream()
                .map(ExactSanctionChangeResult.Applied.class::cast)
                .filter(ExactSanctionChangeResult.Applied::replayed)
                .count());
        assertEquals(1, count("sanction_events"));
        assertEquals(1, count("audit_events"));
    }

    private static List<ExactSanctionChangeResult> race(
            ExactSanctionChangeRequest first,
            ExactSanctionChangeRequest second,
            boolean separateRuntimes
    ) throws Exception {
        try (MariaDbRuntime runtimeOne = MariaDb.initialize(databaseConfig());
             MariaDbRuntime runtimeTwo = separateRuntimes ? MariaDb.initialize(databaseConfig()) : null;
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            SanctionMutationStore firstStore = runtimeOne.sanctionMutationStore();
            SanctionMutationStore secondStore = runtimeTwo == null
                    ? runtimeOne.sanctionMutationStore()
                    : runtimeTwo.sanctionMutationStore();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<ExactSanctionChangeResult> firstResult = executor.submit(
                    () -> applyWhenReleased(firstStore, first, ready, start)
            );
            Future<ExactSanctionChangeResult> secondResult = executor.submit(
                    () -> applyWhenReleased(secondStore, second, ready, start)
            );
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(
                    firstResult.get(20, TimeUnit.SECONDS),
                    secondResult.get(20, TimeUnit.SECONDS)
            );
        }
    }

    private static ExactSanctionChangeResult applyWhenReleased(
            SanctionMutationStore store,
            ExactSanctionChangeRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return store.applyExact(request, DEFAULT_LIMITS);
    }

    private static void assertOneAppliedOneStale(List<ExactSanctionChangeResult> results) {
        assertEquals(1, results.stream().filter(ExactSanctionChangeResult.Applied.class::isInstance).count());
        assertEquals(1, results.stream()
                .filter(ExactSanctionChangeResult.Rejected.class::isInstance)
                .map(ExactSanctionChangeResult.Rejected.class::cast)
                .filter(result -> result.code().equals("STALE_SANCTION_STATE"))
                .count());
    }

    private static Fixture seed(
            int sequence,
            String issuerRank,
            SanctionStatus status,
            Instant issuedAt,
            Optional<Instant> expiration
    ) throws SQLException {
        UUID subjectId = uuid(sequence);
        UUID sanctionId = uuid(100 + sequence);
        CaseId caseId = caseId(sequence);
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection()) {
            insertPlayer(connection, subjectId, "Player" + sequence, sequence % 2 == 0 ? "BEDROCK" : "JAVA");
            insertPlayer(connection, MODERATOR.id(), MODERATOR.displayName(), "JAVA");
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO cases(
                        case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                        public_reason, exact_reason_id, sanction_family, internal_explanation,
                        configuration_version, visibility, state, issued_at, revision)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'integration.reason', 'BAN',
                        'Internal integration detail', 'integration', 'PRIVATE', 'OPEN', ?, 0)
                    """)) {
                statement.setString(1, caseId.value());
                statement.setString(2, "case-seed-" + sequence);
                statement.setBytes(3, uuidBytes(subjectId));
                statement.setBytes(4, uuidBytes(MODERATOR.id()));
                statement.setString(5, MODERATOR.displayName());
                statement.setString(6, issuerRank);
                statement.setString(7, "Public reason " + sequence);
                statement.setTimestamp(8, Timestamp.from(issuedAt));
                assertEquals(1, statement.executeUpdate());
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO punishment_steps(
                        case_id, raw_ordinal, effective_ordinal, recency_bonus,
                        step_label, contribution_json, escalation_contributes)
                    VALUES (?, 1, 1, 0, 'Step 1', JSON_OBJECT(), TRUE)
                    """)) {
                statement.setString(1, caseId.value());
                assertEquals(1, statement.executeUpdate());
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sanctions(
                        sanction_id, case_id, target_id, sanction_type, status,
                        issued_at, activated_at, expiration_at, ended_at, revision)
                    VALUES (?, ?, ?, 'BAN', ?, ?, ?, ?, NULL, 0)
                    """)) {
                statement.setBytes(1, uuidBytes(sanctionId));
                statement.setString(2, caseId.value());
                statement.setBytes(3, uuidBytes(subjectId));
                statement.setString(4, status.name());
                statement.setTimestamp(5, Timestamp.from(issuedAt));
                if (status == SanctionStatus.ACTIVE) {
                    statement.setTimestamp(6, Timestamp.from(issuedAt));
                } else {
                    statement.setNull(6, java.sql.Types.TIMESTAMP);
                }
                if (expiration.isPresent()) {
                    statement.setTimestamp(7, Timestamp.from(expiration.orElseThrow()));
                } else {
                    statement.setNull(7, java.sql.Types.TIMESTAMP);
                }
                assertEquals(1, statement.executeUpdate());
            }
        }
        return new Fixture(subjectId, sanctionId, caseId);
    }

    private static UUID insertAppeal(Fixture fixture, String state) throws SQLException {
        UUID appealId = UUID.randomUUID();
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO website_appeal_requests(
                         appeal_id, punishment_id, case_id, player_account_token,
                         idempotency_key, state, outcome_code, created_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, 'ACCEPTED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                     """)) {
            statement.setBytes(1, uuidBytes(appealId));
            statement.setBytes(2, uuidBytes(fixture.sanctionId()));
            statement.setString(3, fixture.caseId().value());
            statement.setBytes(4, new byte[32]);
            statement.setString(5, "appeal-" + appealId);
            statement.setString(6, state);
            assertEquals(1, statement.executeUpdate());
        }
        return appealId;
    }

    private static ExactSanctionChangeRequest request(
            Fixture fixture,
            long expectedRevision,
            SanctionChangeAction action,
            Optional<Instant> expiration,
            String reason,
            String key,
            Optional<UUID> appealId
    ) {
        return new ExactSanctionChangeRequest(
                new IdempotencyKey(key),
                fixture.sanctionId(),
                expectedRevision,
                MODERATOR,
                action,
                expiration,
                reason,
                appealId,
                Optional.empty(),
                "SMP",
                false
        );
    }

    private static void insertPlayer(
            Connection connection,
            UUID playerId,
            String username,
            String platform
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO players(
                    player_id, current_username, lowercase_username, platform,
                    first_seen_at, last_seen_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """)) {
            statement.setBytes(1, uuidBytes(playerId));
            statement.setString(2, username);
            statement.setString(3, username.toLowerCase(java.util.Locale.ROOT));
            statement.setString(4, platform);
            statement.executeUpdate();
        }
    }

    private static void setMode(String mode) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection()) {
            setMode(connection, mode);
        }
    }

    private static void setMode(Connection connection, String mode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE operational_state
                SET mode=?, revision=revision+1, reason='integration test', updated_at=CURRENT_TIMESTAMP(6)
                WHERE singleton_id=1
                """)) {
            statement.setString(1, mode);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static int count(String table) throws SQLException {
        return Math.toIntExact(longValue("SELECT COUNT(*) FROM " + table)); // nosemgrep
    }

    private static long longValue(String sql, Object... parameters) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) { // nosemgrep
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static String stringValue(String sql, Object... parameters) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) { // nosemgrep
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static Instant instantValue(String sql, Object... parameters) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) { // nosemgrep
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                Timestamp value = result.getTimestamp(1);
                return value == null ? null : value.toInstant();
            }
        }
    }

    private static boolean booleanValue(String sql, Object... parameters) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) { // nosemgrep
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            Object value = parameters[index];
            if (value instanceof UUID identifier) {
                statement.setBytes(index + 1, uuidBytes(identifier));
            } else {
                statement.setObject(index + 1, value);
            }
        }
    }

    private static void execute(String sql) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) { // nosemgrep
            statement.execute();
        }
    }

    private static net.enthusia.staff.persistence.DatabaseConfig databaseConfig() {
        return MariaDbIntegrationSupport.databaseConfig(DATABASE);
    }

    private static UUID uuid(int value) {
        return new UUID(0L, value);
    }

    private static byte[] uuidBytes(UUID value) {
        return MariaDbIntegrationSupport.uuidBytes(value);
    }

    private static CaseId caseId(int sequence) {
        return new CaseId("CASE" + String.format(java.util.Locale.ROOT, "%012d", sequence));
    }

    private record Fixture(UUID subjectId, UUID sanctionId, CaseId caseId) {
    }
}
