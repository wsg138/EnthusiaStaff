package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
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
    private static final String ACCOUNT_ID = uuid(800).toString();
    private static final String USERNAME = "appeal_isolation_user";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final Actor REVIEWER = new Actor(uuid(900), "Appeal Reviewer", StaffRank.MOD);
    private static final SanctionActionLimits LIMITS = new SanctionActionLimits(10, 500, true);
    private static final PunishmentCodeProtector CODE_PROTECTOR = new PunishmentCodeProtector(
            1,
            new SecretKeySpec(
                    "appeal-isolation-integration-key-32".getBytes(StandardCharsets.UTF_8),
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
            try (PreparedStatement mode = database.prepareStatement("""
                    UPDATE operational_state
                    SET mode='ACTIVE', revision=revision+1,
                        reason='appeal isolation integration', updated_at=CURRENT_TIMESTAMP(6)
                    WHERE singleton_id=1
                    """)) {
                assertEquals(1, mode.executeUpdate());
            }
        }
    }

    @Test
    void combinedCaseOverturnsOnlyAppealedSanctionAndReplaysAcrossRestart() throws Exception {
        Fixture fixture = seedCombinedCase(1);
        UUID appealId = uuid(501);
        String key = "appeal-isolation-restart";
        ExactSanctionChangeRequest request;

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = claimedWebsiteStore(runtime, fixture);
            assertPrepared(website, appealId, fixture, key, false);
            website.completeAppealAcceptance(appealId, "APPLIED", "MUTATION_PENDING", NOW);
            request = request(runtime, fixture, appealId, key);
            ExactSanctionChangeResult.Applied applied = assertInstanceOf(
                    ExactSanctionChangeResult.Applied.class,
                    service(runtime).applyExact(request, OperationalMode.ACTIVE, LIMITS)
            );
            assertFalse(applied.replayed());
            website.completeAppealAcceptance(appealId, "APPLIED", "APPLIED", NOW.plusSeconds(1));
        }

        assertIsolation(fixture, appealId);

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = runtime.websiteModerationStore(CODE_PROTECTOR);
            assertPrepared(website, appealId, fixture, key, true);
            website.completeAppealAcceptance(appealId, "APPLIED", "MUTATION_PENDING", NOW.plusSeconds(2));
            ExactSanctionChangeResult.Applied replay = assertInstanceOf(
                    ExactSanctionChangeResult.Applied.class,
                    service(runtime).applyExact(
                            request(runtime, fixture, appealId, key),
                            OperationalMode.ACTIVE,
                            LIMITS
                    )
            );
            assertTrue(replay.replayed());
            website.completeAppealAcceptance(appealId, "APPLIED", "APPLIED", NOW.plusSeconds(3));
        }

        assertIsolation(fixture, appealId);
        assertEquals(1, count("sanction_events"));
        assertEquals(1, count("audit_events"));
    }

    @Test
    void staleAppealDecisionIsRejectedWithoutMutatingEitherSanction() throws Exception {
        Fixture fixture = seedCombinedCase(2);
        UUID appealId = uuid(502);
        String key = "appeal-isolation-stale";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = claimedWebsiteStore(runtime, fixture);
            assertPrepared(website, appealId, fixture, key, false);
            long revision = runtime.sanctionMutationStore()
                    .exactRevision(fixture.appealedSanctionId())
                    .orElseThrow();
            website.completeAppealAcceptance(appealId, "APPLIED", "MUTATION_PENDING", NOW);
            incrementRevision(fixture.appealedSanctionId());
            ExactSanctionChangeResult.Rejected rejected = assertInstanceOf(
                    ExactSanctionChangeResult.Rejected.class,
                    service(runtime).applyExact(
                            request(fixture, appealId, key, revision),
                            OperationalMode.ACTIVE,
                            LIMITS
                    )
            );
            assertEquals("STALE_SANCTION_STATE", rejected.code());
            website.completeAppealAcceptance(appealId, "REJECTED", rejected.code(), NOW.plusSeconds(1));
        }

        assertEquals("ACTIVE", sanctionStatus(fixture.appealedSanctionId()));
        assertEquals("ACTIVE", sanctionStatus(fixture.siblingSanctionId()));
        assertAppealState(appealId, "REJECTED", "STALE_SANCTION_STATE");
        assertEquals(0, count("sanction_events"));
        assertEquals(0, count("audit_events"));
    }

    @Test
    void mutationRollbackLeavesRecoverablePendingAppeal() throws Exception {
        Fixture fixture = seedCombinedCase(3);
        UUID appealId = uuid(503);
        String key = "appeal-isolation-rollback";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = claimedWebsiteStore(runtime, fixture);
            assertPrepared(website, appealId, fixture, key, false);
            website.completeAppealAcceptance(appealId, "APPLIED", "MUTATION_PENDING", NOW);
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

        assertEquals("ACTIVE", sanctionStatus(fixture.appealedSanctionId()));
        assertEquals("ACTIVE", sanctionStatus(fixture.siblingSanctionId()));
        assertAppealState(appealId, "APPLIED", "MUTATION_PENDING");
        assertEquals(0, count("sanction_events"));
        assertEquals(0, count("audit_events"));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = runtime.websiteModerationStore(CODE_PROTECTOR);
            assertPrepared(website, appealId, fixture, key, true);
            ExactSanctionChangeResult.Applied applied = assertInstanceOf(
                    ExactSanctionChangeResult.Applied.class,
                    service(runtime).applyExact(
                            request(runtime, fixture, appealId, key),
                            OperationalMode.ACTIVE,
                            LIMITS
                    )
            );
            assertFalse(applied.replayed());
            website.completeAppealAcceptance(appealId, "APPLIED", "APPLIED", NOW.plusSeconds(2));
        }

        assertIsolation(fixture, appealId);
    }

    @Test
    void concurrentIdenticalAppealRetriesCreateOneMutationAndOneReplay() throws Exception {
        Fixture fixture = seedCombinedCase(4);
        UUID appealId = uuid(504);
        String key = "appeal-isolation-concurrent";

        try (MariaDbRuntime setup = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore website = claimedWebsiteStore(setup, fixture);
            assertPrepared(website, appealId, fixture, key, false);
            website.completeAppealAcceptance(appealId, "APPLIED", "MUTATION_PENDING", NOW);
        }

        try (MariaDbRuntime firstRuntime = MariaDb.initialize(databaseConfig(DATABASE));
             MariaDbRuntime secondRuntime = MariaDb.initialize(databaseConfig(DATABASE));
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            ExactSanctionChangeRequest request = request(firstRuntime, fixture, appealId, key);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<ExactSanctionChangeResult> first = executor.submit(() -> applyWhenReleased(
                    service(firstRuntime), request, ready, start
            ));
            Future<ExactSanctionChangeResult> second = executor.submit(() -> applyWhenReleased(
                    service(secondRuntime), request, ready, start
            ));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<ExactSanctionChangeResult> results = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
            assertEquals(2, results.stream().filter(ExactSanctionChangeResult.Applied.class::isInstance).count());
            assertEquals(1, results.stream()
                    .map(ExactSanctionChangeResult.Applied.class::cast)
                    .filter(ExactSanctionChangeResult.Applied::replayed)
                    .count());
            firstRuntime.websiteModerationStore(CODE_PROTECTOR)
                    .completeAppealAcceptance(appealId, "APPLIED", "APPLIED", NOW.plusSeconds(1));
        }

        assertIsolation(fixture, appealId);
        assertEquals(1, count("sanction_events"));
        assertEquals(1, count("audit_events"));
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

    private static void assertPrepared(
            WebsiteModerationStore website,
            UUID appealId,
            Fixture fixture,
            String key,
            boolean replayed
    ) {
        net.enthusia.staff.domain.website.AppealAcceptancePreparation.Ready ready = assertInstanceOf(
                net.enthusia.staff.domain.website.AppealAcceptancePreparation.Ready.class,
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
    }

    private static SanctionChangeService service(MariaDbRuntime runtime) {
        return new SanctionChangeService(
                new DefaultAuthorizationPolicy(),
                runtime.sanctionMutationStore()
        );
    }

    private static ExactSanctionChangeRequest request(
            MariaDbRuntime runtime,
            Fixture fixture,
            UUID appealId,
            String key
    ) {
        long revision = runtime.sanctionMutationStore()
                .exactRevision(fixture.appealedSanctionId())
                .orElseThrow();
        return request(fixture, appealId, key, revision);
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
        UUID targetId = uuid(sequence);
        UUID appealed = uuid(100 + sequence);
        UUID sibling = uuid(200 + sequence);
        CaseId caseId = new CaseId("APPEAL" + String.format(java.util.Locale.ROOT, "%010d", sequence));
        Instant issuedAt = NOW.minusSeconds(3_600);
        try (Connection database = connection(DATABASE)) {
            insertPlayer(database, targetId, "AppealTarget");
            insertPlayer(database, REVIEWER.id(), REVIEWER.displayName());
            try (PreparedStatement statement = database.prepareStatement("""
                    INSERT INTO cases(
                        case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                        public_reason, exact_reason_id, sanction_family, internal_explanation,
                        configuration_version, visibility, state, issued_at, revision)
                    VALUES (?, ?, ?, ?, 'Original Issuer', 'ADMIN',
                        'Combined appeal regression', 'appeal.isolation', 'COMBINED',
                        'Exact sanction appeal isolation', 'integration', 'PUBLIC', 'OPEN', ?, 0)
                    """)) {
                statement.setString(1, caseId.value());
                statement.setString(2, "appeal-case-" + sequence);
                statement.setBytes(3, uuidBytes(targetId));
                statement.setBytes(4, uuidBytes(uuid(950 + sequence)));
                statement.setTimestamp(5, Timestamp.from(issuedAt));
                assertEquals(1, statement.executeUpdate());
            }
            try (PreparedStatement statement = database.prepareStatement("""
                    INSERT INTO punishment_steps(
                        case_id, raw_ordinal, effective_ordinal, recency_bonus,
                        step_label, contribution_json, escalation_contributes)
                    VALUES (?, 1, 1, 0, 'Combined step', JSON_OBJECT(), TRUE)
                    """)) {
                statement.setString(1, caseId.value());
                assertEquals(1, statement.executeUpdate());
            }
            insertSanction(database, appealed, caseId, targetId, "BAN", issuedAt);
            insertSanction(database, sibling, caseId, targetId, "MUTE", issuedAt);
        }
        return new Fixture(caseId, appealed, sibling);
    }

    private static void insertPlayer(Connection database, UUID playerId, String username) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("""
                INSERT INTO players(
                    player_id, current_username, lowercase_username, platform,
                    first_seen_at, last_seen_at)
                VALUES (?, ?, ?, 'JAVA', ?, ?)
                """)) {
            statement.setBytes(1, uuidBytes(playerId));
            statement.setString(2, username);
            statement.setString(3, username.toLowerCase(java.util.Locale.ROOT));
            statement.setTimestamp(4, Timestamp.from(NOW));
            statement.setTimestamp(5, Timestamp.from(NOW));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSanction(
            Connection database,
            UUID sanctionId,
            CaseId caseId,
            UUID targetId,
            String type,
            Instant issuedAt
    ) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("""
                INSERT INTO sanctions(
                    sanction_id, case_id, target_id, sanction_type, status,
                    issued_at, activated_at, expiration_at, ended_at, revision)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, NULL, 0)
                """)) {
            statement.setBytes(1, uuidBytes(sanctionId));
            statement.setString(2, caseId.value());
            statement.setBytes(3, uuidBytes(targetId));
            statement.setString(4, type);
            statement.setTimestamp(5, Timestamp.from(issuedAt));
            statement.setTimestamp(6, Timestamp.from(issuedAt));
            statement.setTimestamp(7, Timestamp.from(NOW.plusSeconds(7_200)));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertIsolation(Fixture fixture, UUID appealId) throws SQLException {
        assertEquals("OVERTURNED", sanctionStatus(fixture.appealedSanctionId()));
        assertEquals("ACTIVE", sanctionStatus(fixture.siblingSanctionId()));
        assertEquals("OPEN", stringValue(
                "SELECT state FROM cases WHERE case_id=?",
                fixture.caseId().value()
        ));
        assertTrue(booleanValue(
                "SELECT escalation_contributes FROM punishment_steps WHERE case_id=?",
                fixture.caseId().value()
        ));
        assertAppealState(appealId, "APPLIED", "APPLIED");
        assertEquals(1, longValue(
                "SELECT COUNT(*) FROM sanction_events WHERE linked_appeal_id=? AND sanction_id=?",
                appealId,
                fixture.appealedSanctionId()
        ));
        assertEquals(0, longValue(
                "SELECT COUNT(*) FROM sanction_events WHERE sanction_id=?",
                fixture.siblingSanctionId()
        ));
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

    private static void incrementRevision(UUID sanctionId) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement("""
                     UPDATE sanctions SET revision=revision+1 WHERE sanction_id=?
                     """)) {
            statement.setBytes(1, uuidBytes(sanctionId));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static int count(String table) throws SQLException {
        return Math.toIntExact(longValue("SELECT COUNT(*) FROM " + table)); // nosemgrep
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

    private static boolean booleanValue(String sql, Object... parameters) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement(sql)) { // nosemgrep
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
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
