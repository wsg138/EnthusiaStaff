package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeExpectation;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CaseSanctionHierarchyIntegrationTest {
    private static final Actor MODERATOR = new Actor(uuid(900), "Moderator", StaffRank.MOD);
    private static final Actor ADMIN = new Actor(uuid(901), "Admin", StaffRank.ADMIN);
    private static final Actor FOUNDER = new Actor(uuid(902), "Founder", StaffRank.FOUNDER);

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_case_sanction_hierarchy_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

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
            for (String sql : List.of(
                    "DELETE FROM network_outbox_deliveries",
                    "DELETE FROM network_outbox",
                    "DELETE FROM discord_outbox",
                    "DELETE FROM audit_events",
                    "DELETE FROM sanction_events",
                    "DELETE FROM punishment_overturn_requests",
                    "DELETE FROM sanction_links",
                    "DELETE FROM punishment_steps",
                    "DELETE FROM sanctions",
                    "DELETE FROM cases",
                    "DELETE FROM player_names",
                    "DELETE FROM players"
            )) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE operational_state
                    SET mode = 'ACTIVE', revision = revision + 1,
                        reason = 'case sanction hierarchy integration', updated_at = CURRENT_TIMESTAMP(6)
                    WHERE singleton_id = 1
                    """)) {
                assertEquals(1, statement.executeUpdate());
            }
        }
    }

    @Test
    void rejectsHigherAndSystemIssuedCasesWithoutSideEffects() throws Exception {
        List<DeniedCase> deniedCases = List.of(
                new DeniedCase(seed(1, "ADMIN"), MODERATOR),
                new DeniedCase(seed(2, "FOUNDER"), MODERATOR),
                new DeniedCase(seed(3, "FOUNDER"), ADMIN),
                new DeniedCase(seed(4, "SYSTEM"), FOUNDER)
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            for (DeniedCase denied : deniedCases) {
                SanctionChangeRequest request = request(
                        denied.fixture(), denied.actor(), "hierarchy-denied-" + denied.fixture().sequence()
                );
                assertHierarchyDenied(runtime, request);
                assertHierarchyDenied(runtime, request);
            }
        }

        for (DeniedCase denied : deniedCases) {
            assertUnchanged(denied.fixture());
        }
        assertNoMutationSideEffects();
    }

    @Test
    void allowsModToMutateHelperAndModCasesAndReplaysAcrossRestart() throws Exception {
        Fixture helperCase = seed(5, "HELPER");
        Fixture modCase = seed(6, "MOD");
        SanctionChangeRequest helperRequest = request(helperCase, MODERATOR, "allowed-helper");
        SanctionChangeRequest modRequest = request(modCase, MODERATOR, "allowed-mod");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            assertApplied(runtime.sanctionMutationStore().apply(helperRequest), 1, false);
            assertApplied(runtime.sanctionMutationStore().apply(modRequest), 1, false);
        }
        try (MariaDbRuntime restarted = MariaDb.initialize(databaseConfig())) {
            assertApplied(restarted.sanctionMutationStore().apply(helperRequest), 0, true);
        }

        assertEquals("ENDED_EARLY", sanctionStatus(helperCase));
        assertEquals("ENDED_EARLY", sanctionStatus(modCase));
        assertEquals(2, count("sanction_events"));
        assertEquals(2, count("audit_events"));
        assertEquals(2, count("network_outbox"));
        assertEquals(2, count("discord_outbox"));
    }

    @Test
    void staleExpectationStillRejectsAuthorizedMutationWithoutSideEffects() throws Exception {
        Fixture fixture = seed(7, "HELPER");
        SanctionChangeExpectation stale = new SanctionChangeExpectation(
                1,
                Map.of(fixture.sanctionId(), 0L),
                Optional.of(true),
                Optional.empty()
        );
        SanctionChangeRequest request = request(fixture, MODERATOR, "stale-authorized", Optional.of(stale));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            SanctionChangeResult.Rejected rejected = assertInstanceOf(
                    SanctionChangeResult.Rejected.class,
                    runtime.sanctionMutationStore().apply(request)
            );
            assertEquals("STALE_CASE", rejected.code());
        }

        assertUnchanged(fixture);
        assertNoMutationSideEffects();
    }

    @Test
    void rejectedRequestReevaluatesPersistedIssuerRankAfterRestart() throws Exception {
        Fixture fixture = seed(8, "ADMIN");
        SanctionChangeRequest request = request(fixture, MODERATOR, "recovery-rank-change");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            assertHierarchyDenied(runtime, request);
        }
        updateIssuerRank(fixture, "HELPER");
        try (MariaDbRuntime restarted = MariaDb.initialize(databaseConfig())) {
            assertApplied(restarted.sanctionMutationStore().apply(request), 1, false);
        }

        assertEquals("ENDED_EARLY", sanctionStatus(fixture));
        assertEquals(1, count("sanction_events"));
        assertEquals(1, count("audit_events"));
    }

    @Test
    void concurrentIssuerRankChangeIsObservedUnderTheCaseLock() throws Exception {
        Fixture fixture = seed(9, "HELPER");
        SanctionChangeRequest request = request(fixture, MODERATOR, "concurrent-rank-change");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig());
             HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection updater = dataSource.getConnection();
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            updater.setAutoCommit(false);
            updateIssuerRank(updater, fixture, "ADMIN");
            Future<SanctionChangeResult> result = executor.submit(
                    () -> runtime.sanctionMutationStore().apply(request)
            );

            Thread.sleep(200);
            assertFalse(result.isDone(), "sanction mutation must wait on the locked case row");
            updater.commit();

            SanctionChangeResult.Rejected rejected = assertInstanceOf(
                    SanctionChangeResult.Rejected.class,
                    result.get(10, TimeUnit.SECONDS)
            );
            assertEquals("HIERARCHY_DENIED", rejected.code());
        }

        assertEquals("ADMIN", issuerRank(fixture));
        assertUnchanged(fixture);
        assertNoMutationSideEffects();
    }

    private static void assertHierarchyDenied(MariaDbRuntime runtime, SanctionChangeRequest request) {
        SanctionChangeResult.Rejected rejected = assertInstanceOf(
                SanctionChangeResult.Rejected.class,
                runtime.sanctionMutationStore().apply(request)
        );
        assertEquals("HIERARCHY_DENIED", rejected.code());
    }

    private static void assertApplied(SanctionChangeResult result, int affected, boolean replayed) {
        SanctionChangeResult.Applied applied = assertInstanceOf(SanctionChangeResult.Applied.class, result);
        assertEquals(affected, applied.affectedSanctions());
        assertEquals(replayed, applied.replayed());
    }

    private static void assertUnchanged(Fixture fixture) throws SQLException {
        assertEquals("ACTIVE", sanctionStatus(fixture));
        assertEquals("OPEN", caseState(fixture));
        assertTrue(escalationContributes(fixture));
    }

    private static void assertNoMutationSideEffects() throws SQLException {
        assertEquals(0, count("sanction_events"));
        assertEquals(0, count("audit_events"));
        assertEquals(0, count("network_outbox"));
        assertEquals(0, count("discord_outbox"));
    }

    private static Fixture seed(int sequence, String issuerRank) throws SQLException {
        Fixture fixture = new Fixture(
                sequence,
                uuid(sequence),
                uuid(100 + sequence),
                new CaseId("01HZX3K8M2N4P" + String.format(Locale.ROOT, "%03d", sequence))
        );
        Instant issuedAt = Instant.now().minusSeconds(3_600);
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection()) {
            insertPlayer(connection, fixture.subjectId(), "Player" + sequence);
            insertPlayer(connection, MODERATOR.id(), MODERATOR.displayName());
            insertPlayer(connection, ADMIN.id(), ADMIN.displayName());
            insertPlayer(connection, FOUNDER.id(), FOUNDER.displayName());
            insertCase(connection, fixture, issuerRank, issuedAt);
            insertPunishmentStep(connection, fixture);
            insertSanction(connection, fixture, issuedAt);
        }
        return fixture;
    }

    private static void insertPlayer(Connection connection, UUID playerId, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO players(
                    player_id, current_username, lowercase_username, platform, first_seen_at, last_seen_at)
                VALUES (?, ?, ?, 'JAVA', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(playerId));
            statement.setString(2, username);
            statement.setString(3, username.toLowerCase(Locale.ROOT));
            statement.executeUpdate();
        }
    }

    private static void insertCase(
            Connection connection,
            Fixture fixture,
            String issuerRank,
            Instant issuedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO cases(
                    case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                    public_reason, exact_reason_id, sanction_family, internal_explanation,
                    configuration_version, visibility, state, issued_at, revision)
                VALUES (?, ?, ?, ?, 'Issuer', ?, 'Public reason', 'integration.reason', 'BAN',
                    'Hierarchy integration detail', 'integration', 'PRIVATE', 'OPEN', ?, 0)
                """)) {
            statement.setString(1, fixture.caseId().value());
            statement.setString(2, "case-hierarchy-" + fixture.sequence());
            statement.setBytes(3, MariaDbIntegrationSupport.uuidBytes(fixture.subjectId()));
            statement.setBytes(4, MariaDbIntegrationSupport.uuidBytes(FOUNDER.id()));
            statement.setString(5, issuerRank);
            statement.setTimestamp(6, Timestamp.from(issuedAt));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertPunishmentStep(Connection connection, Fixture fixture) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO punishment_steps(
                    case_id, raw_ordinal, effective_ordinal, recency_bonus,
                    step_label, contribution_json, escalation_contributes)
                VALUES (?, 1, 1, 0, 'Step 1', JSON_OBJECT(), TRUE)
                """)) {
            statement.setString(1, fixture.caseId().value());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSanction(
            Connection connection,
            Fixture fixture,
            Instant issuedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sanctions(
                    sanction_id, case_id, target_id, sanction_type, status,
                    issued_at, activated_at, expiration_at, ended_at, revision)
                VALUES (?, ?, ?, 'BAN', 'ACTIVE', ?, ?, ?, NULL, 0)
                """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(fixture.sanctionId()));
            statement.setString(2, fixture.caseId().value());
            statement.setBytes(3, MariaDbIntegrationSupport.uuidBytes(fixture.subjectId()));
            statement.setTimestamp(4, Timestamp.from(issuedAt));
            statement.setTimestamp(5, Timestamp.from(issuedAt));
            statement.setTimestamp(6, Timestamp.from(Instant.now().plusSeconds(7_200)));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static SanctionChangeRequest request(Fixture fixture, Actor actor, String key) {
        return request(fixture, actor, key, Optional.empty());
    }

    private static SanctionChangeRequest request(
            Fixture fixture,
            Actor actor,
            String key,
            Optional<SanctionChangeExpectation> expectation
    ) {
        return new SanctionChangeRequest(
                new IdempotencyKey(key),
                fixture.caseId(),
                actor,
                SanctionChangeAction.END_EARLY,
                Optional.empty(),
                "Case hierarchy integration",
                expectation
        );
    }

    private static void updateIssuerRank(Fixture fixture, String rank) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection()) {
            updateIssuerRank(connection, fixture, rank);
        }
    }

    private static void updateIssuerRank(Connection connection, Fixture fixture, String rank) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE cases SET actor_rank = ? WHERE case_id = ?")) {
            statement.setString(1, rank);
            statement.setString(2, fixture.caseId().value());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static String sanctionStatus(Fixture fixture) throws SQLException {
        return stringValue("SELECT status FROM sanctions WHERE sanction_id = ?", fixture, true);
    }

    private static String caseState(Fixture fixture) throws SQLException {
        return stringValue("SELECT state FROM cases WHERE case_id = ?", fixture, false);
    }

    private static String issuerRank(Fixture fixture) throws SQLException {
        return stringValue("SELECT actor_rank FROM cases WHERE case_id = ?", fixture, false);
    }

    private static String stringValue(String sql, Fixture fixture, boolean sanctionId) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (sanctionId) {
                statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(fixture.sanctionId()));
            } else {
                statement.setString(1, fixture.caseId().value());
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static boolean escalationContributes(Fixture fixture) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT escalation_contributes FROM punishment_steps WHERE case_id = ?")) {
            statement.setString(1, fixture.caseId().value());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private static int count(String table) throws SQLException {
        String sql = switch (table) {
            case "sanction_events" -> "SELECT COUNT(*) FROM sanction_events";
            case "audit_events" -> "SELECT COUNT(*) FROM audit_events";
            case "network_outbox" -> "SELECT COUNT(*) FROM network_outbox";
            case "discord_outbox" -> "SELECT COUNT(*) FROM discord_outbox";
            default -> throw new IllegalArgumentException("unsupported count table");
        };
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static net.enthusia.staff.persistence.DatabaseConfig databaseConfig() {
        return MariaDbIntegrationSupport.databaseConfig(DATABASE);
    }

    private static UUID uuid(int value) {
        return new UUID(0L, value);
    }

    private record Fixture(int sequence, UUID subjectId, UUID sanctionId, CaseId caseId) {
    }

    private record DeniedCase(Fixture fixture, Actor actor) {
    }
}
