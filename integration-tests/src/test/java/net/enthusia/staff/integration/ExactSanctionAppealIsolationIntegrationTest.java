package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertSanction;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;
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
class ExactSanctionAppealIsolationIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-05T20:00:00Z");
    private static final String APPLIED = "APPLIED";
    private static final String ACTIVE = "ACTIVE";
    private static final String REJECTED = "REJECTED";
    private static final String STALE_SANCTION_STATE = "STALE_SANCTION_STATE";
    private static final String ACCOUNT_ID = uuid(800).toString();
    private static final String USERNAME = "appeal_isolation_user";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final Actor REVIEWER = new Actor(uuid(900), "Appeal Reviewer", StaffRank.MOD);
    private static final SanctionActionLimits LIMITS = new SanctionActionLimits(10, 1_000, true);
    private static final PunishmentCodeProtector CODE_PROTECTOR = new PunishmentCodeProtector(
            1,
            new SecretKeySpec(
                    UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            )
    );

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_appeal_isolation_test")
            .withUsername(USERNAME)
            .withPassword(PASSWORD);

    @BeforeAll
    static void migrateSchema() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            assertNotNull(runtime.sanctionMutationStore());
            assertNotNull(runtime.websiteModerationStore(CODE_PROTECTOR));
        }
    }

    @BeforeEach
    void clearFixtures() throws SQLException {
        try (Connection database = connection(DATABASE);
             java.sql.Statement statement = database.createStatement()) {
            for (String table : List.of(
                    "network_outbox_deliveries",
                    "network_outbox",
                    "discord_outbox",
                    "audit_events",
                    "sanction_events",
                    "website_appeal_requests",
                    "punishment_codes",
                    "sanction_links",
                    "sanctions",
                    "punishment_steps",
                    "cases",
                    "player_names",
                    "players"
            )) {
                statement.executeUpdate("DELETE FROM " + table); // nosemgrep
            }
            statement.executeUpdate("""
                    UPDATE operational_state
                    SET mode='ACTIVE', revision=revision+1,
                        reason='appeal isolation integration', updated_at=CURRENT_TIMESTAMP(6)
                    WHERE singleton_id=1
                    """);
        }
    }

    @Test
    void combinedCaseMutatesOnlyAppealedSanctionAndReplaysAfterRestart() throws Exception {
        Fixture fixture = seedCombinedCase(1);
        UUID appealId = uuid(501);
        String key = "appeal-isolation-restart";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = claimedWebsiteStore(runtime, fixture);
            assertPrepared(website, appealId, fixture, key, false);
            website.completeAppealAcceptance(appealId, APPLIED, pending(0L), NOW);
            ExactSanctionChangeResult.Applied applied = apply(runtime, request(runtime, fixture, appealId, key));
            assertFalse(applied.replayed());
            website.completeAppealAcceptance(appealId, APPLIED, APPLIED, NOW.plusSeconds(1));
        }

        assertIsolatedResult(fixture, appealId);

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = runtime.websiteModerationStore(CODE_PROTECTOR);
            AppealAcceptancePreparation.Ready ready = assertPrepared(website, appealId, fixture, key, true);
            assertTrue(ready.pendingRevision().isEmpty());
            website.completeAppealAcceptance(appealId, APPLIED, pending(1L), NOW.plusSeconds(2));
            ExactSanctionChangeResult.Applied replay = apply(runtime, request(runtime, fixture, appealId, key));
            assertTrue(replay.replayed());
            website.completeAppealAcceptance(appealId, APPLIED, APPLIED, NOW.plusSeconds(3));
        }

        assertIsolatedResult(fixture, appealId);
        assertEquals(1L, countSanctionEvents(fixture.appealedSanctionId()));
        assertEquals(1L, countOverturnAudits(fixture.caseId()));
    }

    @Test
    void pendingRevisionSurvivesRestartAndRejectsInterveningMutation() throws Exception {
        Fixture fixture = seedCombinedCase(2);
        UUID appealId = uuid(502);
        String key = "appeal-isolation-stale";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = claimedWebsiteStore(runtime, fixture);
            assertPrepared(website, appealId, fixture, key, false);
            long revision = exactRevision(runtime, fixture);
            assertEquals(0L, revision);
            website.completeAppealAcceptance(appealId, APPLIED, pending(revision), NOW);
        }

        incrementRevision(fixture.appealedSanctionId());

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = runtime.websiteModerationStore(CODE_PROTECTOR);
            AppealAcceptancePreparation.Ready ready = assertPrepared(website, appealId, fixture, key, true);
            long acceptedRevision = ready.pendingRevision().orElseThrow();
            assertEquals(0L, acceptedRevision);
            ExactSanctionChangeResult.Rejected rejected = assertInstanceOf(
                    ExactSanctionChangeResult.Rejected.class,
                    service(runtime).applyExact(
                            request(fixture, appealId, key, acceptedRevision),
                            OperationalMode.ACTIVE,
                            LIMITS
                    )
            );
            assertEquals(STALE_SANCTION_STATE, rejected.code());
            website.completeAppealAcceptance(appealId, REJECTED, rejected.code(), NOW.plusSeconds(1));
        }

        assertUnchanged(fixture);
        assertAppealState(appealId, REJECTED, STALE_SANCTION_STATE);
        assertEquals(0L, countSanctionEvents(fixture.appealedSanctionId()));
        assertEquals(0L, countOverturnAudits(fixture.caseId()));
    }

    @Test
    void transactionRollbackLeavesPendingAppealRecoverableAfterRestart() throws Exception {
        Fixture fixture = seedCombinedCase(3);
        UUID appealId = uuid(503);
        String key = "appeal-isolation-rollback";

        preparePendingAndForceRollback(fixture, appealId, key);

        assertUnchanged(fixture);
        assertAppealState(appealId, APPLIED, pending(0L));
        assertEquals(0L, countSanctionEvents(fixture.appealedSanctionId()));
        assertEquals(0L, countOverturnAudits(fixture.caseId()));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = runtime.websiteModerationStore(CODE_PROTECTOR);
            AppealAcceptancePreparation.Ready ready = assertPrepared(website, appealId, fixture, key, true);
            ExactSanctionChangeResult.Applied applied = apply(
                    runtime,
                    request(fixture, appealId, key, ready.pendingRevision().orElseThrow())
            );
            assertFalse(applied.replayed());
            website.completeAppealAcceptance(appealId, APPLIED, APPLIED, NOW.plusSeconds(2));
        }

        assertIsolatedResult(fixture, appealId);
    }

    @Test
    void concurrentRetriesProduceOneMutationAndOneReplay() throws Exception {
        Fixture fixture = seedCombinedCase(4);
        UUID appealId = uuid(504);
        String key = "appeal-isolation-concurrent";

        try (MariaDbRuntime setup = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = claimedWebsiteStore(setup, fixture);
            assertPrepared(website, appealId, fixture, key, false);
            website.completeAppealAcceptance(appealId, APPLIED, pending(0L), NOW);
        }

        try (MariaDbRuntime firstRuntime = MariaDb.initialize(databaseConfig(DATABASE));
             MariaDbRuntime secondRuntime = MariaDb.initialize(databaseConfig(DATABASE));
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            ExactSanctionChangeRequest request = request(firstRuntime, fixture, appealId, key);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<ExactSanctionChangeResult> first = executor.submit(
                    () -> applyWhenReleased(service(firstRuntime), request, ready, start));
            Future<ExactSanctionChangeResult> second = executor.submit(
                    () -> applyWhenReleased(service(secondRuntime), request, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<ExactSanctionChangeResult.Applied> results = List.of(
                    assertInstanceOf(ExactSanctionChangeResult.Applied.class, first.get(20, TimeUnit.SECONDS)),
                    assertInstanceOf(ExactSanctionChangeResult.Applied.class, second.get(20, TimeUnit.SECONDS))
            );
            assertEquals(1L, results.stream().filter(ExactSanctionChangeResult.Applied::replayed).count());
            firstRuntime.websiteModerationStore(CODE_PROTECTOR)
                    .completeAppealAcceptance(appealId, APPLIED, APPLIED, NOW.plusSeconds(1));
        }

        assertIsolatedResult(fixture, appealId);
        assertEquals(1L, countSanctionEvents(fixture.appealedSanctionId()));
        assertEquals(1L, countOverturnAudits(fixture.caseId()));
    }

    private static void preparePendingAndForceRollback(
            Fixture fixture,
            UUID appealId,
            String key
    ) throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = claimedWebsiteStore(runtime, fixture);
            assertPrepared(website, appealId, fixture, key, false);
            website.completeAppealAcceptance(appealId, APPLIED, pending(0L), NOW);
            ExactSanctionChangeRequest request = request(runtime, fixture, appealId, key);
            execute("""
                    CREATE TRIGGER fail_appeal_isolation_audit
                    BEFORE INSERT ON audit_events
                    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced appeal audit failure'
                    """);
            try {
                assertThrows(
                        ModerationPersistenceException.class,
                        () -> service(runtime).applyExact(request, OperationalMode.ACTIVE, LIMITS)
                );
            } finally {
                execute("DROP TRIGGER IF EXISTS fail_appeal_isolation_audit");
            }
        }
    }

    private static ExactSanctionChangeResult.Applied apply(
            MariaDbRuntime runtime,
            ExactSanctionChangeRequest request
    ) {
        return assertInstanceOf(
                ExactSanctionChangeResult.Applied.class,
                service(runtime).applyExact(request, OperationalMode.ACTIVE, LIMITS)
        );
    }

    private static ExactSanctionChangeResult applyWhenReleased(
            SanctionChangeService service,
            ExactSanctionChangeRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent appeal mutation was not released");
        }
        return service.applyExact(request, OperationalMode.ACTIVE, LIMITS);
    }

    private static WebsiteModerationStore claimedWebsiteStore(
            MariaDbRuntime runtime,
            Fixture fixture
    ) {
        WebsiteModerationStore website = runtime.websiteModerationStore(CODE_PROTECTOR);
        website.ensureEligibleCodes(NOW, 10);
        String code = website.codeForSanction(fixture.appealedSanctionId(), NOW)
                .orElseThrow()
                .code();
        website.claimCode(code, ACCOUNT_ID, "AppealTarget", NOW);
        return website;
    }

    private static AppealAcceptancePreparation.Ready assertPrepared(
            WebsiteModerationStore website,
            UUID appealId,
            Fixture fixture,
            String key,
            boolean replayed
    ) {
        AppealAcceptancePreparation.Ready ready = assertInstanceOf(
                AppealAcceptancePreparation.Ready.class,
                website.prepareAppealAcceptance(
                        appealId,
                        fixture.appealedSanctionId(),
                        fixture.caseId(),
                        ACCOUNT_ID,
                        key,
                        NOW
                )
        );
        assertEquals(replayed, ready.replayed());
        return ready;
    }

    private static SanctionChangeService service(MariaDbRuntime runtime) {
        return new SanctionChangeService(
                new DefaultAuthorizationPolicy(),
                runtime.sanctionMutationStore()
        );
    }

    private static long exactRevision(MariaDbRuntime runtime, Fixture fixture) {
        return runtime.sanctionMutationStore()
                .exactRevision(fixture.appealedSanctionId())
                .orElseThrow();
    }

    private static ExactSanctionChangeRequest request(
            MariaDbRuntime runtime,
            Fixture fixture,
            UUID appealId,
            String key
    ) {
        return request(fixture, appealId, key, exactRevision(runtime, fixture));
    }

    private static ExactSanctionChangeRequest request(
            Fixture fixture,
            UUID appealId,
            String key,
            long revision
    ) {
        return new ExactSanctionChangeRequest(
                new IdempotencyKey("website-appeal:" + key),
                fixture.appealedSanctionId(),
                revision,
                REVIEWER,
                SanctionChangeAction.FULL_OVERTURN,
                Optional.empty(),
                "Accepted appeal overturns only the exact punishment",
                Optional.of(appealId),
                Optional.empty(),
                "VELOCITY_WEBSITE",
                true
        );
    }

    private static Fixture seedCombinedCase(int sequence) throws SQLException {
        CaseId caseId = new CaseId("%016d".formatted(7_000L + sequence));
        UUID targetId = uuid(sequence);
        UUID appealed = uuid(100L + sequence);
        UUID sibling = uuid(200L + sequence);
        Instant issuedAt = NOW.minusSeconds(3_600);
        insertPlayer(DATABASE, targetId, "AppealTarget", issuedAt);
        insertCase(DATABASE, caseId.value(), targetId, uuid(950L + sequence), "PUBLIC", issuedAt);
        insertSanction(
                DATABASE,
                appealed,
                caseId.value(),
                targetId,
                "BAN",
                ACTIVE,
                issuedAt,
                NOW.plusSeconds(7_200)
        );
        insertSanction(
                DATABASE,
                sibling,
                caseId.value(),
                targetId,
                "MUTE",
                ACTIVE,
                issuedAt,
                NOW.plusSeconds(7_200)
        );
        return new Fixture(caseId, appealed, sibling);
    }

    private static void assertIsolatedResult(Fixture fixture, UUID appealId) throws SQLException {
        assertEquals("OVERTURNED", sanctionStatus(fixture.appealedSanctionId()));
        assertEquals(ACTIVE, sanctionStatus(fixture.siblingSanctionId()));
        assertEquals("OPEN", stringValue(
                "SELECT state FROM cases WHERE case_id=?",
                fixture.caseId().value()
        ));
        assertAppealState(appealId, APPLIED, APPLIED);
        assertEquals(1L, longValue(
                "SELECT COUNT(*) FROM sanction_events WHERE linked_appeal_id=? AND sanction_id=?",
                appealId,
                fixture.appealedSanctionId()
        ));
        assertEquals(0L, countSanctionEvents(fixture.siblingSanctionId()));
    }

    private static void assertUnchanged(Fixture fixture) throws SQLException {
        assertEquals(ACTIVE, sanctionStatus(fixture.appealedSanctionId()));
        assertEquals(ACTIVE, sanctionStatus(fixture.siblingSanctionId()));
    }

    private static void assertAppealState(UUID appealId, String state, String outcome) throws SQLException {
        assertEquals(state, stringValue(
                "SELECT state FROM website_appeal_requests WHERE appeal_id=?",
                appealId
        ));
        assertEquals(outcome, stringValue(
                "SELECT outcome_code FROM website_appeal_requests WHERE appeal_id=?",
                appealId
        ));
    }

    private static String sanctionStatus(UUID sanctionId) throws SQLException {
        return stringValue("SELECT status FROM sanctions WHERE sanction_id=?", sanctionId);
    }

    private static long countSanctionEvents(UUID sanctionId) throws SQLException {
        return longValue("SELECT COUNT(*) FROM sanction_events WHERE sanction_id=?", sanctionId);
    }

    private static long countOverturnAudits(CaseId caseId) throws SQLException {
        return longValue(
                "SELECT COUNT(*) FROM audit_events WHERE case_id=? AND event_type='SANCTION_OVERTURNED'",
                caseId.value()
        );
    }

    private static void incrementRevision(UUID sanctionId) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement(
                     "UPDATE sanctions SET revision=revision+1 WHERE sanction_id=?")) {
            statement.setBytes(1, uuidBytes(sanctionId));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static long longValue(String sql, Object... parameters) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement(sql)) { // nosemgrep
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static String stringValue(String sql, Object... parameters) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement(sql)) { // nosemgrep
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            Object parameter = parameters[index];
            if (parameter instanceof UUID identifier) {
                statement.setBytes(index + 1, uuidBytes(identifier));
            } else {
                statement.setObject(index + 1, parameter);
            }
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement(sql)) { // nosemgrep
            statement.execute();
        }
    }

    private static String pending(long revision) {
        return "MUTATION_PENDING_R" + revision;
    }

    private static UUID uuid(long suffix) {
        return new UUID(0L, suffix);
    }

    private record Fixture(
            CaseId caseId,
            UUID appealedSanctionId,
            UUID siblingSanctionId
    ) {
    }
}
