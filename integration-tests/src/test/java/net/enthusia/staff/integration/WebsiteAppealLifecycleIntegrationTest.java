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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.WebsiteAppealMutation;
import net.enthusia.staff.domain.website.WebsiteAppealSubmission;
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
class WebsiteAppealLifecycleIntegrationTest {
    private static final int KEY_VERSION = 1;
    private static final Instant NOW = Instant.parse("2026-09-28T20:00:00Z");
    private static final String ACCOUNT_ONE = uuid(901).toString();
    private static final String ACCOUNT_TWO = uuid(902).toString();
    private static final String PLAYER_NAME = "AppealPlayer";
    private static final String MODERATOR_RANK = "MOD";
    private static final String ADMIN_RANK = "ADMIN";
    private static final UUID MODERATOR = uuid(950);
    private static final UUID ADMIN = uuid(951);
    private static final String TEST_USERNAME = "website_appeal_lifecycle";
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
            .withDatabaseName("enthusia_staff_website_appeal_lifecycle")
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
    void playerCanEditUntilClaimAndRetriesRemainIdempotent() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(1);
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = submittedStore(runtime, fixture, ACCOUNT_ONE);
            WebsiteAppealSubmission submission = latestSubmission(store, fixture, ACCOUNT_ONE);

            WebsiteAppealMutation edited = store.editAppeal(
                    submission.appeal().appealId(), 1, ACCOUNT_ONE,
                    "Updated appeal reason with enough detail.", "appeal-edit-0001",
                    NOW.plusSeconds(1)
            );
            assertFalse(edited.replayed());
            assertFalse(edited.claimed());
            assertEquals(2, edited.appeal().version());

            WebsiteAppealMutation editReplay = store.editAppeal(
                    submission.appeal().appealId(), 1, ACCOUNT_ONE,
                    "Updated appeal reason with enough detail.", "appeal-edit-0001",
                    NOW.plusSeconds(2)
            );
            assertTrue(editReplay.replayed());
            assertEquals(2, editReplay.appeal().version());

            WebsiteAppealMutation claimed = store.claimAppeal(
                    submission.appeal().appealId(), 2, MODERATOR, MODERATOR_RANK,
                    "appeal-claim-0001", NOW.plusSeconds(3)
            );
            assertFalse(claimed.replayed());
            assertTrue(claimed.claimed());
            assertEquals(3, claimed.appeal().version());

            WebsiteAppealMutation claimReplay = store.claimAppeal(
                    submission.appeal().appealId(), 2, MODERATOR, MODERATOR_RANK,
                    "appeal-claim-0001", NOW.plusSeconds(4)
            );
            assertTrue(claimReplay.replayed());
            assertTrue(claimReplay.claimed());

            assertError("APPEAL_EDIT_LOCKED", () -> store.editAppeal(
                    submission.appeal().appealId(), 3, ACCOUNT_ONE,
                    "This edit is too late because staff claimed it.", "appeal-edit-locked",
                    NOW.plusSeconds(5)
            ));
        }
        assertEquals(
                List.of("WEBSITE_APPEAL_EDITED", "WEBSITE_APPEAL_CLAIMED"),
                lifecycleAuditTypes()
        );
    }

    @Test
    void ownershipVersionCompetingClaimAndKeyConflictsAreRejected() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(2);
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = submittedStore(runtime, fixture, ACCOUNT_ONE);
            UUID appealId = latestSubmission(store, fixture, ACCOUNT_ONE).appeal().appealId();

            assertError("APPEAL_ACCOUNT_CONFLICT", () -> store.editAppeal(
                    appealId, 1, ACCOUNT_TWO, "A detailed appeal edit from the wrong account.",
                    "wrong-account-edit", NOW.plusSeconds(1)
            ));
            assertError("STALE_APPEAL_STATE", () -> store.claimAppeal(
                    appealId, 9, MODERATOR, MODERATOR_RANK, "stale-claim-0002", NOW.plusSeconds(2)
            ));

            store.claimAppeal(
                    appealId, 1, MODERATOR, MODERATOR_RANK,
                    "first-claim-0002", NOW.plusSeconds(3)
            );
            assertError("APPEAL_IDEMPOTENCY_CONFLICT", () -> store.claimAppeal(
                    appealId, 1, ADMIN, ADMIN_RANK, "first-claim-0002", NOW.plusSeconds(4)
            ));
            assertError("APPEAL_ALREADY_CLAIMED", () -> store.claimAppeal(
                    appealId, 2, uuid(952), MODERATOR_RANK,
                    "second-mod-claim-0002", NOW.plusSeconds(5)
            ));
            WebsiteAppealMutation reassigned = store.claimAppeal(
                    appealId, 2, ADMIN, ADMIN_RANK,
                    "admin-recovery-claim-0002", NOW.plusSeconds(6)
            );
            assertTrue(reassigned.claimed());
            assertEquals(3, reassigned.appeal().version());
        }
    }

    @Test
    void adminReopenResetsDecisionAndClaimStateDurably() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(3);
        UUID appealId;
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = submittedStore(runtime, fixture, ACCOUNT_ONE);
            appealId = latestSubmission(store, fixture, ACCOUNT_ONE).appeal().appealId();

            WebsiteAppealMutation claim = store.claimAppeal(
                    appealId, 1, MODERATOR, MODERATOR_RANK,
                    "claim-before-deny", NOW.plusSeconds(1)
            );
            store.prepareAppealDecision(
                    appealId, claim.appeal().version(), "DENY",
                    "The appeal does not justify removal.", MODERATOR, MODERATOR_RANK,
                    "deny-before-reopen", NOW.plusSeconds(2)
            );

            WebsiteAppealMutation reopened = store.reopenAppeal(
                    appealId, 3, ADMIN, ADMIN_RANK, "Senior review found new information.",
                    "admin-reopen-0003", NOW.plusSeconds(3)
            );
            assertFalse(reopened.replayed());
            assertFalse(reopened.claimed());
            assertEquals("OPEN", reopened.appeal().state());
            assertEquals(4, reopened.appeal().version());
            assertNull(reopened.appeal().decision());
            assertNull(reopened.appeal().decisionNote());

            WebsiteAppealMutation replay = store.reopenAppeal(
                    appealId, 3, ADMIN, ADMIN_RANK, "Senior review found new information.",
                    "admin-reopen-0003", NOW.plusSeconds(4)
            );
            assertTrue(replay.replayed());
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            WebsiteAppealMutation claimed = store.claimAppeal(
                    appealId, 4, ADMIN, ADMIN_RANK, "post-restart-claim", NOW.plusSeconds(5)
            );
            assertTrue(claimed.claimed());
            assertEquals(5, claimed.appeal().version());
        }
        assertTrue(lifecycleAuditTypes().contains("WEBSITE_APPEAL_REOPENED"));
    }

    @Test
    void moderatorCannotUseStoreReopenOperation() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(4);
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = submittedStore(runtime, fixture, ACCOUNT_ONE);
            UUID appealId = latestSubmission(store, fixture, ACCOUNT_ONE).appeal().appealId();
            WebsiteAppealMutation claim = store.claimAppeal(
                    appealId, 1, MODERATOR, MODERATOR_RANK,
                    "claim-before-rank-check", NOW.plusSeconds(1)
            );
            store.prepareAppealDecision(
                    appealId, claim.appeal().version(), "DENY",
                    "The appeal does not justify removal.", MODERATOR, MODERATOR_RANK,
                    "deny-before-rank-check", NOW.plusSeconds(2)
            );
            assertError("INVALID_APPEAL_REVIEW_ACTION", () -> store.reopenAppeal(
                    appealId, 3, MODERATOR, MODERATOR_RANK, "Moderator cannot reopen this appeal.",
                    "mod-reopen-forbidden", NOW.plusSeconds(3)
            ));
        }
    }

    @Test
    void auditFailureRollsBackClaimMutation() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(5);
        UUID appealId;
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = submittedStore(runtime, fixture, ACCOUNT_ONE);
            appealId = latestSubmission(store, fixture, ACCOUNT_ONE).appeal().appealId();
            createAuditFailureTrigger();
            try {
                assertThrows(RuntimeException.class, () -> store.claimAppeal(
                        appealId, 1, MODERATOR, MODERATOR_RANK,
                        "rollback-claim-0005", NOW.plusSeconds(1)
                ));
            } finally {
                dropAuditFailureTrigger();
            }
        }
        assertUnclaimed(appealId, 1);
    }

    private static WebsiteModerationStore submittedStore(
            MariaDbRuntime runtime,
            AppealFixture fixture,
            String accountId
    ) {
        WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
        String code = store.codeForSanction(fixture.sanctionId(), NOW).orElseThrow().code();
        store.claimCode(code, accountId, PLAYER_NAME, NOW);
        return store;
    }

    private static WebsiteAppealSubmission latestSubmission(
            WebsiteModerationStore store,
            AppealFixture fixture,
            String accountId
    ) {
        return store.submitAppeal(
                fixture.sanctionId(), accountId, PLAYER_NAME,
                "Original appeal reason with enough detail.",
                "appeal-submit-" + fixture.sanctionId(), NOW
        );
    }

    private static AppealFixture seedEligiblePunishment(int suffix) throws SQLException {
        CaseId caseId = caseId(suffix);
        UUID playerId = uuid(suffix);
        UUID sanctionId = uuid(400L + suffix);
        Instant issuedAt = NOW.minusSeconds(60);
        insertPlayer(DATABASE, playerId, PLAYER_NAME, issuedAt);
        insertCase(DATABASE, caseId.value(), playerId, uuid(900L + suffix), "PUBLIC", issuedAt);
        insertSanction(
                DATABASE, sanctionId, caseId.value(), playerId, "BAN", "ACTIVE",
                issuedAt, NOW.plusSeconds(3_600)
        );
        return new AppealFixture(caseId, sanctionId);
    }

    private static List<String> lifecycleAuditTypes() throws SQLException {
        List<String> events = new ArrayList<>();
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement("""
                     SELECT event_type FROM audit_events
                     WHERE event_type LIKE 'WEBSITE_APPEAL_%'
                     ORDER BY sequence_id
                     """)) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) events.add(result.getString(1));
            }
        }
        return List.copyOf(events);
    }

    private static void createAuditFailureTrigger() throws SQLException {
        try (Connection database = connection(DATABASE); Statement statement = database.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER fail_website_appeal_lifecycle_audit
                    BEFORE INSERT ON audit_events
                    FOR EACH ROW
                    BEGIN
                        IF NEW.event_type = 'WEBSITE_APPEAL_CLAIMED' THEN
                            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced lifecycle audit failure';
                        END IF;
                    END
                    """);
        }
    }

    private static void dropAuditFailureTrigger() throws SQLException {
        try (Connection database = connection(DATABASE); Statement statement = database.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS fail_website_appeal_lifecycle_audit");
        }
    }

    private static void assertUnclaimed(UUID appealId, long revision) throws SQLException {
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement("""
                     SELECT revision, reviewer_account_id
                     FROM website_appeal_requests WHERE appeal_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(appealId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(revision, result.getLong("revision"));
                assertNull(result.getBytes("reviewer_account_id"));
            }
        }
    }

    private static void assertError(String expectedCode, Executable operation) {
        WebsiteModerationException exception = assertThrows(WebsiteModerationException.class, operation);
        assertEquals(expectedCode, exception.code());
    }

    private static CaseId caseId(long suffix) {
        return new CaseId("%016d".formatted(suffix));
    }

    private static UUID uuid(long suffix) {
        return new UUID(0, suffix);
    }

    private record AppealFixture(CaseId caseId, UUID sanctionId) {
    }
}
