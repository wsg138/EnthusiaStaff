package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.clearWebsiteModerationFixtures;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;
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
class WebsiteAppealIntegrationTest {
    private static final int KEY_VERSION = 1;
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String APPLIED_STATE = "APPLIED";
    private static final String REJECTED_STATE = "REJECTED";
    private static final String ACCOUNT_ONE = uuid(901).toString();
    private static final String ACCOUNT_TWO = uuid(902).toString();
    private static final String CURRENT_NAME = "AppealPlayer";
    private static final String TEST_USERNAME = "website_appeal_user";
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
            .withDatabaseName("enthusia_staff_website_appeal_test")
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
    void completionAndReplayRemainIdempotentAcrossRestart() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(1);
        UUID appealId = uuid(501);
        String idempotencyKey = "appeal-accept-1";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = claimedStore(runtime, fixture, ACCOUNT_ONE);
            assertReady(
                    store.prepareAppealAcceptance(
                            appealId, fixture.sanctionId(), fixture.caseId(),
                            ACCOUNT_ONE, idempotencyKey, NOW
                    ),
                    false
            );
            store.completeAppealAcceptance(appealId, APPLIED_STATE, APPLIED_STATE, NOW.plusSeconds(1));
            store.completeAppealAcceptance(appealId, APPLIED_STATE, APPLIED_STATE, NOW.plusSeconds(2));
            assertError("APPEAL_STATE_CONFLICT", () -> store.completeAppealAcceptance(
                    appealId, APPLIED_STATE, "REPLAYED", NOW.plusSeconds(3)
            ));
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            assertReady(
                    runtime.websiteModerationStore(CODE_PROTECTOR).prepareAppealAcceptance(
                            appealId, fixture.sanctionId(), fixture.caseId(),
                            ACCOUNT_ONE, idempotencyKey, NOW.plusSeconds(4)
                    ),
                    true
            );
        }
        assertAppealState(appealId, APPLIED_STATE, APPLIED_STATE);
    }

    @Test
    void rejectedCompletionReturnsTheDurableOutcomeOnReplay() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(2);
        UUID appealId = uuid(502);
        String idempotencyKey = "appeal-reject-2";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = claimedStore(runtime, fixture, ACCOUNT_ONE);
            assertReady(store.prepareAppealAcceptance(
                    appealId, fixture.sanctionId(), fixture.caseId(),
                    ACCOUNT_ONE, idempotencyKey, NOW
            ), false);
            store.completeAppealAcceptance(appealId, REJECTED_STATE, "POLICY_DENIED", NOW.plusSeconds(1));
            AppealAcceptancePreparation.Rejected replay = assertInstanceOf(
                    AppealAcceptancePreparation.Rejected.class,
                    store.prepareAppealAcceptance(
                            appealId, fixture.sanctionId(), fixture.caseId(),
                            ACCOUNT_ONE, idempotencyKey, NOW.plusSeconds(2)
                    )
            );
            assertEquals("POLICY_DENIED", replay.code());
        }
        assertAppealState(appealId, REJECTED_STATE, "POLICY_DENIED");
    }

    @Test
    void preparationRejectsMismatchedAndIneligibleBindingsWithoutWriting() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(3);

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = claimedStore(runtime, fixture, ACCOUNT_ONE);
            assertRejected(store.prepareAppealAcceptance(
                    uuid(503), fixture.sanctionId(), fixture.caseId(),
                    ACCOUNT_TWO, "appeal-wrong-account", NOW
            ), "BINDING_ACCOUNT_MISMATCH");
            assertRejected(store.prepareAppealAcceptance(
                    uuid(504), fixture.sanctionId(), caseId(999),
                    ACCOUNT_ONE, "appeal-wrong-case", NOW
            ), "PUNISHMENT_NOT_FOUND");
            assertTrue(store.revokeCode(fixture.sanctionId(), uuid(950), NOW.plusSeconds(1)));
            assertRejected(store.prepareAppealAcceptance(
                    uuid(505), fixture.sanctionId(), fixture.caseId(),
                    ACCOUNT_ONE, "appeal-revoked", NOW.plusSeconds(2)
            ), "PUNISHMENT_INELIGIBLE");
        }
        assertEquals(0, appealCount());
    }

    @Test
    void onePunishmentCannotBePreparedUnderConflictingRequestIdentities() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(4);
        UUID appealId = uuid(506);
        String idempotencyKey = "appeal-conflict-4";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = claimedStore(runtime, fixture, ACCOUNT_ONE);
            assertReady(store.prepareAppealAcceptance(
                    appealId, fixture.sanctionId(), fixture.caseId(),
                    ACCOUNT_ONE, idempotencyKey, NOW
            ), false);
            assertError("APPEAL_IDEMPOTENCY_CONFLICT", () -> store.prepareAppealAcceptance(
                    uuid(507), fixture.sanctionId(), fixture.caseId(),
                    ACCOUNT_ONE, "appeal-conflict-other", NOW.plusSeconds(1)
            ));
            assertError("APPEAL_IDEMPOTENCY_CONFLICT", () -> store.prepareAppealAcceptance(
                    appealId, fixture.sanctionId(), fixture.caseId(),
                    ACCOUNT_ONE, "appeal-conflict-key", NOW.plusSeconds(1)
            ));
            assertError("APPEAL_IDEMPOTENCY_CONFLICT", () -> store.prepareAppealAcceptance(
                    uuid(508), fixture.sanctionId(), fixture.caseId(),
                    ACCOUNT_ONE, idempotencyKey, NOW.plusSeconds(1)
            ));
        }
        assertEquals(1, appealCount());
    }

    @Test
    void concurrentIdenticalPreparationCreatesOneReplayableRequest() throws Exception {
        AppealFixture fixture = seedEligiblePunishment(5);
        UUID appealId = uuid(509);
        String idempotencyKey = "appeal-concurrent-5";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE));
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            WebsiteModerationStore store = claimedStore(runtime, fixture, ACCOUNT_ONE);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<PreparationOutcome> first = executor.submit(() -> prepareWhenReleased(
                    store, fixture, appealId, idempotencyKey, ready, start
            ));
            Future<PreparationOutcome> second = executor.submit(() -> prepareWhenReleased(
                    store, fixture, appealId, idempotencyKey, ready, start
            ));

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<PreparationOutcome> outcomes = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
            assertEquals(1, outcomes.stream().filter(outcome -> !outcome.replayed()).count());
            assertEquals(1, outcomes.stream().filter(PreparationOutcome::replayed).count());
        }
        assertEquals(1, appealCount());
    }

    @Test
    void missingAppealCannotBeCompleted() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            assertError("APPEAL_NOT_FOUND", () -> store.completeAppealAcceptance(
                    uuid(510), APPLIED_STATE, APPLIED_STATE, NOW
            ));
        }
    }

    private static PreparationOutcome prepareWhenReleased(
            WebsiteModerationStore store,
            AppealFixture fixture,
            UUID appealId,
            String idempotencyKey,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent appeal preparation was not released");
        }
        AppealAcceptancePreparation result = store.prepareAppealAcceptance(
                appealId,
                fixture.sanctionId(),
                fixture.caseId(),
                ACCOUNT_ONE,
                idempotencyKey,
                NOW
        );
        AppealAcceptancePreparation.Ready readyResult = assertInstanceOf(
                AppealAcceptancePreparation.Ready.class,
                result
        );
        return new PreparationOutcome(readyResult.replayed());
    }

    private static WebsiteModerationStore claimedStore(
            MariaDbRuntime runtime,
            AppealFixture fixture,
            String accountId
    ) {
        WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
        String code = store.codeForSanction(fixture.sanctionId(), NOW).orElseThrow().code();
        store.claimCode(code, accountId, CURRENT_NAME, NOW);
        return store;
    }

    private static AppealFixture seedEligiblePunishment(int suffix) throws SQLException {
        CaseId caseId = caseId(suffix);
        UUID playerId = uuid(suffix);
        UUID sanctionId = uuid(400L + suffix);
        Instant issuedAt = NOW.minusSeconds(60);
        insertPlayer(DATABASE, playerId, CURRENT_NAME, issuedAt);
        insertCase(DATABASE, caseId.value(), playerId, uuid(900L + suffix), "PUBLIC", issuedAt);
        insertSanction(
                DATABASE,
                sanctionId,
                caseId.value(),
                playerId,
                "BAN",
                "ACTIVE",
                issuedAt,
                NOW.plusSeconds(3_600)
        );
        return new AppealFixture(caseId, sanctionId);
    }

    private static void assertReady(AppealAcceptancePreparation preparation, boolean replayed) {
        AppealAcceptancePreparation.Ready ready = assertInstanceOf(
                AppealAcceptancePreparation.Ready.class,
                preparation
        );
        assertEquals(replayed, ready.replayed());
    }

    private static void assertRejected(AppealAcceptancePreparation preparation, String code) {
        AppealAcceptancePreparation.Rejected rejected = assertInstanceOf(
                AppealAcceptancePreparation.Rejected.class,
                preparation
        );
        assertEquals(code, rejected.code());
    }

    private static void assertError(String expectedCode, Executable operation) {
        WebsiteModerationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                WebsiteModerationException.class,
                operation
        );
        assertEquals(expectedCode, exception.code());
    }

    private static void assertAppealState(UUID appealId, String state, String outcome) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement("""
                     SELECT state, outcome_code
                     FROM website_appeal_requests
                     WHERE appeal_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(appealId));
            try (ResultSet result = statement.executeQuery()) {
                requireRow(result);
                assertEquals(state, result.getString("state"));
                assertEquals(outcome, result.getString("outcome_code"));
                requireNoMoreRows(result);
            }
        }
    }

    private static int appealCount() throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement(
                     "SELECT COUNT(*) FROM website_appeal_requests");
             ResultSet result = statement.executeQuery()) {
            requireRow(result);
            return result.getInt(1);
        }
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

    private static CaseId caseId(long suffix) {
        return new CaseId("%016d".formatted(suffix));
    }

    private static UUID uuid(long suffix) {
        return new UUID(0, suffix);
    }

    private record AppealFixture(CaseId caseId, UUID sanctionId) {
    }

    private record PreparationOutcome(boolean replayed) {
    }
}
