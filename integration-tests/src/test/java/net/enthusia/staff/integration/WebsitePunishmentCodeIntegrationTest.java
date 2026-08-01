package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.clearWebsiteModerationFixtures;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayerName;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertSanction;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;
import net.enthusia.staff.domain.website.PunishmentCodeDisplay;
import net.enthusia.staff.domain.website.WebsiteModerationException;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WebsitePunishmentCodeIntegrationTest {
    private static final int KEY_VERSION = 1;
    private static final int BATCH_LIMIT = 20;
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String ACTIVE = "ACTIVE";
    private static final String APPLIED = "APPLIED";
    private static final String BAN_TYPE = "BAN";
    private static final String ACCOUNT_ONE = uuid(901).toString();
    private static final String ACCOUNT_TWO = uuid(902).toString();
    private static final String CURRENT_NAME = "CurrentPlayer";
    private static final String HISTORIC_NAME = "FormerPlayer";
    private static final String TEST_USERNAME = "website_code_user";
    private static final String TEST_PASSWORD = UUID.randomUUID().toString();
    private static final PunishmentCodeProtector CODE_PROTECTOR = new PunishmentCodeProtector(
            KEY_VERSION,
            new SecretKeySpec(
                    UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            )
    );

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_website_code_test")
            .withUsername(TEST_USERNAME)
            .withPassword(TEST_PASSWORD);

    @BeforeAll
    static void migrateSchema() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            assertNotNull(runtime.websiteModerationStore(CODE_PROTECTOR));
        }
    }

    @BeforeEach
    void clearWebsiteFixtures() throws SQLException {
        clearWebsiteModerationFixtures(DATABASE);
    }

    @Test
    void createsClaimsAndRevalidatesCodesIdempotently() throws SQLException {
        CodeFixture fixture = seedEligiblePunishment(1, NOW.minusSeconds(60));
        insertPlayerName(
                DATABASE,
                fixture.playerId(),
                HISTORIC_NAME,
                NOW.minusSeconds(600),
                NOW.minusSeconds(300)
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            PunishmentCodeDisplay created = store.codeForSanction(fixture.sanctionId(), NOW).orElseThrow();
            assertEquals(created, store.codeForSanction(fixture.sanctionId(), NOW).orElseThrow());
            assertEquals(List.of(created), store.codesForCase(fixture.caseId(), NOW));

            PunishmentCodeBinding first = store.claimCode(created.code(), ACCOUNT_ONE, HISTORIC_NAME, NOW);
            PunishmentCodeBinding replay = store.claimCode(created.code(), ACCOUNT_ONE, CURRENT_NAME, NOW);
            assertEquals(first, replay);
            assertEligibleBinding(first, fixture, 1);
            assertEligibleBinding(store.revalidateCode(fixture.sanctionId(), 1, ACCOUNT_ONE, NOW), fixture, 1);
            assertError("PUNISHMENT_ALREADY_BOUND", () -> store.claimCode(
                    created.code(), ACCOUNT_TWO, CURRENT_NAME, NOW
            ));
        }

        assertEquals(1, auditCount(fixture.caseId(), "PUNISHMENT_CODE_CLAIMED"));
        assertPersistedCode(fixture.sanctionId(), 1, ACTIVE, ACCOUNT_ONE);
    }

    @Test
    void rotationClearsTheBindingAndRevocationIsIdempotent() throws SQLException {
        CodeFixture fixture = seedEligiblePunishment(2, NOW.minusSeconds(60));
        UUID actorId = uuid(950);

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            PunishmentCodeDisplay first = store.codeForSanction(fixture.sanctionId(), NOW).orElseThrow();
            store.claimCode(first.code(), ACCOUNT_ONE, CURRENT_NAME, NOW);

            PunishmentCodeDisplay rotated = store.rotateCode(fixture.sanctionId(), actorId, NOW.plusSeconds(1));
            assertEquals(2, rotated.generation());
            assertNotEquals(first.code(), rotated.code());
            assertBindingState(
                    store.revalidateCode(fixture.sanctionId(), 1, ACCOUNT_ONE, NOW.plusSeconds(1)),
                    false,
                    "CODE_ROTATED"
            );
            assertError("PUNISHMENT_CODE_INVALID", () -> store.claimCode(
                    first.code(), ACCOUNT_ONE, CURRENT_NAME, NOW.plusSeconds(1)
            ));

            assertEligibleBinding(
                    store.claimCode(rotated.code(), ACCOUNT_TWO, CURRENT_NAME, NOW.plusSeconds(2)),
                    fixture,
                    2
            );
            assertError("BINDING_ACCOUNT_MISMATCH", () -> store.revalidateCode(
                    fixture.sanctionId(), 2, ACCOUNT_ONE, NOW.plusSeconds(2)
            ));
            assertTrue(store.revokeCode(fixture.sanctionId(), actorId, NOW.plusSeconds(3)));
            assertFalse(store.revokeCode(fixture.sanctionId(), actorId, NOW.plusSeconds(4)));
            assertBindingState(
                    store.revalidateCode(fixture.sanctionId(), 2, ACCOUNT_TWO, NOW.plusSeconds(4)),
                    false,
                    "CODE_REVOKED"
            );
            assertError("PUNISHMENT_INELIGIBLE", () -> store.claimCode(
                    rotated.code(), ACCOUNT_TWO, CURRENT_NAME, NOW.plusSeconds(4)
            ));
        }

        assertEquals(2, auditCount(fixture.caseId(), "PUNISHMENT_CODE_CLAIMED"));
        assertEquals(1, auditCount(fixture.caseId(), "PUNISHMENT_CODE_ROTATED"));
        assertEquals(1, auditCount(fixture.caseId(), "PUNISHMENT_CODE_REVOKED"));
        assertPersistedCode(fixture.sanctionId(), 2, "REVOKED", ACCOUNT_TWO);
    }

    @Test
    void concurrentClaimsBindExactlyOneAccount() throws Exception {
        CodeFixture fixture = seedEligiblePunishment(3, NOW.minusSeconds(60));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE));
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            String code = store.codeForSanction(fixture.sanctionId(), NOW).orElseThrow().code();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<ClaimOutcome> first = executor.submit(
                    () -> claimWhenReleased(store, code, ACCOUNT_ONE, ready, start)
            );
            Future<ClaimOutcome> second = executor.submit(
                    () -> claimWhenReleased(store, code, ACCOUNT_TWO, ready, start)
            );

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<ClaimOutcome> outcomes = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
            ClaimOutcome winner = outcomes.stream().filter(ClaimOutcome::claimed).findFirst().orElseThrow();
            ClaimOutcome loser = outcomes.stream().filter(outcome -> !outcome.claimed()).findFirst().orElseThrow();
            assertEquals(1, outcomes.stream().filter(ClaimOutcome::claimed).count());
            assertEquals("PUNISHMENT_ALREADY_BOUND", loser.errorCode());
            assertPersistedCode(fixture.sanctionId(), 1, ACTIVE, winner.accountId());
        }

        assertEquals(1, auditCount(fixture.caseId(), "PUNISHMENT_CODE_CLAIMED"));
    }

    @Test
    void caseCodeCreationRollsBackWhenAnyIntegrityCheckFails() throws SQLException {
        CaseId caseId = caseId(4);
        UUID playerId = uuid(4);
        UUID firstSanction = uuid(404);
        UUID corruptSanction = uuid(405);
        insertPlayer(DATABASE, playerId, CURRENT_NAME, NOW.minusSeconds(300));
        insertCase(DATABASE, caseId.value(), playerId, uuid(904), "PUBLIC", NOW.minusSeconds(300));
        insertSanction(
                DATABASE, firstSanction, caseId.value(), playerId, BAN_TYPE, ACTIVE,
                NOW.minusSeconds(120), NOW.plusSeconds(3_600)
        );
        insertSanction(
                DATABASE, corruptSanction, caseId.value(), playerId, "MUTE", ACTIVE,
                NOW.minusSeconds(60), NOW.plusSeconds(3_600)
        );
        insertCorruptCode(corruptSanction, caseId);

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            assertError(
                    "PUNISHMENT_CODE_INTEGRITY_FAILURE",
                    () -> store.codesForCase(caseId, NOW)
            );
        }

        assertEquals(0, codeCount(firstSanction));
        assertEquals(1, codeCount(corruptSanction));
    }

    @Test
    void concurrentMissingCodeReadsCreateOneReplayableCode() throws Exception {
        CodeFixture fixture = seedEligiblePunishment(13, NOW.minusSeconds(60));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE));
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<PunishmentCodeDisplay> first = executor.submit(
                    () -> codeWhenReleased(store, fixture.sanctionId(), ready, start)
            );
            Future<PunishmentCodeDisplay> second = executor.submit(
                    () -> codeWhenReleased(store, fixture.sanctionId(), ready, start)
            );

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        assertEquals(1, codeCount(fixture.sanctionId()));
    }

    @Test
    void existingCodeReadsDoNotTakeExclusiveSanctionLocks() throws Exception {
        CodeFixture fixture = seedEligiblePunishment(12, NOW.minusSeconds(60));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE));
             ExecutorService executor = Executors.newSingleThreadExecutor();
             Connection blocker = connection(DATABASE)) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            PunishmentCodeDisplay expected = store.codeForSanction(fixture.sanctionId(), NOW).orElseThrow();
            blocker.setAutoCommit(false);
            try (PreparedStatement statement = blocker.prepareStatement(
                    "SELECT sanction_id FROM sanctions WHERE sanction_id = ? FOR UPDATE")) {
                statement.setBytes(1, uuidBytes(fixture.sanctionId()));
                try (ResultSet result = statement.executeQuery()) {
                    requireRow(result);
                }
            }

            Future<List<PunishmentCodeDisplay>> reads = executor.submit(() -> List.of(
                    store.codeForSanction(fixture.sanctionId(), NOW).orElseThrow(),
                    store.codesForCase(fixture.caseId(), NOW).getFirst()
            ));
            assertEquals(List.of(expected, expected), reads.get(5, TimeUnit.SECONDS));
            blocker.rollback();
        }
    }

    @Test
    void eligibleCodeBackfillSkipsExpiredWarningAndOverturnedSanctions() throws SQLException {
        CodeFixture eligibleBan = seedEligiblePunishment(5, NOW.minusSeconds(120));
        CodeFixture eligibleMute = seedPunishment(6, "MUTE", ACTIVE, NOW.plusSeconds(3_600));
        CodeFixture expired = seedPunishment(7, BAN_TYPE, ACTIVE, NOW);
        CodeFixture warning = seedPunishment(8, "WARNING", ACTIVE, NOW.plusSeconds(3_600));
        CodeFixture overturned = seedPunishment(9, BAN_TYPE, ACTIVE, NOW.plusSeconds(3_600));
        CodeFixture applied = seedPunishment(10, BAN_TYPE, APPLIED, NOW.plusSeconds(3_600));
        CodeFixture expiredApplied = seedPunishment(11, BAN_TYPE, APPLIED, NOW);
        setCaseState(overturned.caseId(), "FULLY_OVERTURNED");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            assertEquals(3, store.ensureEligibleCodes(NOW, BATCH_LIMIT));
            assertEquals(0, store.ensureEligibleCodes(NOW, BATCH_LIMIT));
            assertTrue(store.codeForSanction(eligibleBan.sanctionId(), NOW).isPresent());
            assertTrue(store.codeForSanction(eligibleMute.sanctionId(), NOW).isPresent());
            assertTrue(store.codeForSanction(applied.sanctionId(), NOW).isPresent());
            assertTrue(store.codeForSanction(expired.sanctionId(), NOW).isEmpty());
            assertTrue(store.codeForSanction(expiredApplied.sanctionId(), NOW).isEmpty());
            assertTrue(store.codeForSanction(warning.sanctionId(), NOW).isEmpty());
            assertTrue(store.codeForSanction(overturned.sanctionId(), NOW).isEmpty());
        }
    }

    private static ClaimOutcome claimWhenReleased(
            WebsiteModerationStore store,
            String code,
            String accountId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent claim start was not released");
        }
        try {
            store.claimCode(code, accountId, CURRENT_NAME, NOW);
            return new ClaimOutcome(accountId, true, null);
        } catch (WebsiteModerationException exception) {
            return new ClaimOutcome(accountId, false, exception.code());
        }
    }

    private static PunishmentCodeDisplay codeWhenReleased(
            WebsiteModerationStore store,
            UUID sanctionId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent code start was not released");
        }
        return store.codeForSanction(sanctionId, NOW).orElseThrow();
    }

    private static CodeFixture seedEligiblePunishment(int suffix, Instant expirationBasis) throws SQLException {
        return seedPunishment(suffix, BAN_TYPE, ACTIVE, expirationBasis.plusSeconds(3_600));
    }

    private static CodeFixture seedPunishment(
            int suffix,
            String type,
            String status,
            Instant expiration
    ) throws SQLException {
        CaseId caseId = caseId(suffix);
        UUID playerId = uuid(suffix);
        UUID sanctionId = uuid(400L + suffix);
        Instant issuedAt = NOW.minusSeconds(180L - suffix);
        insertPlayer(DATABASE, playerId, CURRENT_NAME, issuedAt);
        insertCase(DATABASE, caseId.value(), playerId, uuid(900L + suffix), "PUBLIC", issuedAt);
        insertSanction(DATABASE, sanctionId, caseId.value(), playerId, type, status, issuedAt, expiration);
        return new CodeFixture(caseId, playerId, sanctionId);
    }

    private static void insertCorruptCode(UUID sanctionId, CaseId caseId) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement("""
                     INSERT INTO punishment_codes(
                         sanction_id, case_id, key_version, generation, code_hash, status, created_at
                     ) VALUES (?, ?, ?, 1, ?, 'ACTIVE', ?)
                     """)) {
            statement.setBytes(1, uuidBytes(sanctionId));
            statement.setString(2, caseId.value());
            statement.setInt(3, KEY_VERSION);
            statement.setBytes(4, new byte[32]);
            statement.setTimestamp(5, Timestamp.from(NOW));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void setCaseState(CaseId caseId, String state) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement(
                     "UPDATE cases SET state = ? WHERE case_id = ?")) {
            statement.setString(1, state);
            statement.setString(2, caseId.value());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertEligibleBinding(
            PunishmentCodeBinding binding,
            CodeFixture fixture,
            int generation
    ) {
        assertEquals(fixture.sanctionId(), binding.punishmentId());
        assertEquals(fixture.caseId(), binding.caseId());
        assertEquals(generation, binding.codeGeneration());
        assertEquals(BAN_TYPE, binding.punishmentType());
        assertEquals(CURRENT_NAME, binding.boundUsername());
        assertBindingState(binding, true, "ELIGIBLE");
    }

    private static void assertBindingState(
            PunishmentCodeBinding binding,
            boolean eligible,
            String state
    ) {
        assertEquals(eligible, binding.eligible());
        assertEquals(state, binding.eligibilityState());
    }

    private static void assertError(String expectedCode, Executable operation) {
        WebsiteModerationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                WebsiteModerationException.class,
                operation
        );
        assertEquals(expectedCode, exception.code());
    }

    private static void assertPersistedCode(
            UUID sanctionId,
            int generation,
            String status,
            String accountId
    ) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement("""
                     SELECT generation, status, claimed_account_token
                     FROM punishment_codes
                     WHERE sanction_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(sanctionId));
            try (ResultSet result = statement.executeQuery()) {
                requireRow(result);
                assertEquals(generation, result.getInt("generation"));
                assertEquals(status, result.getString("status"));
                assertArrayEquals(CODE_PROTECTOR.accountToken(accountId), result.getBytes("claimed_account_token"));
                requireNoMoreRows(result);
            }
        }
    }

    private static int auditCount(CaseId caseId, String eventType) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement("""
                     SELECT COUNT(*)
                     FROM audit_events
                     WHERE case_id = ? AND event_type = ?
                     """)) {
            statement.setString(1, caseId.value());
            statement.setString(2, eventType);
            try (ResultSet result = statement.executeQuery()) {
                requireRow(result);
                return result.getInt(1);
            }
        }
    }

    private static int codeCount(UUID sanctionId) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement(
                     "SELECT COUNT(*) FROM punishment_codes WHERE sanction_id = ?")) {
            statement.setBytes(1, uuidBytes(sanctionId));
            try (ResultSet result = statement.executeQuery()) {
                requireRow(result);
                return result.getInt(1);
            }
        }
    }

    private static CaseId caseId(long suffix) {
        return new CaseId("%016d".formatted(suffix));
    }

    private static void requireRow(ResultSet result) throws SQLException {
        if (!result.next()) {
            fail("Expected one database result row");
        }
    }

    private static void requireNoMoreRows(ResultSet result) throws SQLException {
        if (result.next()) {
            fail("Expected exactly one database result row");
        }
    }

    private static UUID uuid(long suffix) {
        return new UUID(0, suffix);
    }

    private record CodeFixture(CaseId caseId, UUID playerId, UUID sanctionId) {
    }

    private record ClaimOutcome(String accountId, boolean claimed, String errorCode) {
    }
}
