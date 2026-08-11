package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.clearWebsiteModerationFixtures;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertSanction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.WebsiteAppealDecisionPreparation;
import net.enthusia.staff.domain.website.WebsiteAppealPage;
import net.enthusia.staff.domain.website.WebsiteAppealSubmission;
import net.enthusia.staff.domain.website.WebsiteModerationException;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SuppressWarnings("PMD.NcssCount")
class WebsiteAppealWorkflowIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");
    private static final String OPEN_STATE = "OPEN";
    private static final String PLAYER_NAME = "AppealPlayer";
    private static final String ACCOUNT_ID = uuid(801).toString();
    private static final UUID REVIEWER_ID = uuid(901);
    private static final String REVIEWER_RANK = "MOD";
    private static final PunishmentCodeProtector CODE_PROTECTOR = testProtector();

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_website_appeal_workflow_test")
            .withUsername("website_appeal_workflow_user")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrateSchema() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            assertNotNull(runtime.websiteModerationStore(CODE_PROTECTOR));
        }
    }

    @BeforeEach
    void clearFixtures() throws SQLException {
        clearWebsiteModerationFixtures(DATABASE);
    }

    @Test
    void submissionReplayInformationRequestAndApprovalRemainDurable() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(1);
        UUID appealId;

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = claimedStore(runtime, fixture, ACCOUNT_ID);
            appealId = submitAndRequestInformation(store, fixture);
            resubmitAndApprove(store, fixture, appealId);
        }

        assertApprovalReplayAfterRestart(appealId);
    }

    private static UUID submitAndRequestInformation(
            WebsiteModerationStore store,
            AppealFixture fixture
    ) {
        assertEquals(1, store.eligibleAppeals(ACCOUNT_ID, 100, NOW).size());
        WebsiteAppealSubmission submitted = store.submitAppeal(
                fixture.sanctionId(), ACCOUNT_ID, PLAYER_NAME,
                "Please review the exact punishment and its context.",
                "submission-workflow-1", NOW.plusSeconds(1)
        );
        UUID appealId = submitted.appeal().appealId();
        assertFalse(submitted.replayed());
        assertEquals(OPEN_STATE, submitted.appeal().state());
        assertEquals(1, submitted.appeal().version());

        WebsiteAppealSubmission replay = store.submitAppeal(
                fixture.sanctionId(), ACCOUNT_ID, PLAYER_NAME,
                "Please review the exact punishment and its context.",
                "submission-workflow-1", NOW.plusSeconds(2)
        );
        assertTrue(replay.replayed());
        assertEquals(appealId, replay.appeal().appealId());

        WebsiteAppealDecisionPreparation information = store.prepareAppealDecision(
                appealId, 1, "request_information",
                "Please provide the missing event context.",
                REVIEWER_ID, REVIEWER_RANK, "decision-information-1", NOW.plusSeconds(3)
        );
        assertFalse(information.requiresAcceptance());
        assertEquals("INFORMATION_REQUESTED", information.appeal().state());
        assertEquals(2, information.appeal().version());
        assertEquals(1, store.eligibleAppeals(ACCOUNT_ID, 100, NOW.plusSeconds(4)).size());
        return appealId;
    }

    private static void resubmitAndApprove(
            WebsiteModerationStore store,
            AppealFixture fixture,
            UUID appealId
    ) {
        WebsiteAppealSubmission resubmitted = store.submitAppeal(
                fixture.sanctionId(), ACCOUNT_ID, PLAYER_NAME,
                "Additional event context requested by the reviewer is included.",
                "submission-workflow-2", NOW.plusSeconds(5)
        );
        assertFalse(resubmitted.replayed());
        assertEquals(OPEN_STATE, resubmitted.appeal().state());
        assertEquals(3, resubmitted.appeal().version());

        WebsiteModerationException stale = assertThrows(
                WebsiteModerationException.class,
                () -> store.prepareAppealDecision(
                        appealId, 1, "deny",
                        "This stale decision must not be accepted.",
                        REVIEWER_ID, REVIEWER_RANK, "decision-stale-1", NOW.plusSeconds(6)
                )
        );
        assertEquals("STALE_APPEAL_STATE", stale.code());

        WebsiteAppealDecisionPreparation approved = store.prepareAppealDecision(
                appealId, 3, "approve",
                "The appeal is supported by the reviewed evidence.",
                REVIEWER_ID, REVIEWER_RANK, "decision-approve-1", NOW.plusSeconds(7)
        );
        assertTrue(approved.requiresAcceptance());
        assertEquals(ACCOUNT_ID, approved.playerAccountId());
        assertEquals("APPROVAL_PENDING", approved.appeal().state());
        assertEquals(4, approved.appeal().version());
    }

    private static void assertApprovalReplayAfterRestart(UUID appealId) {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteAppealDecisionPreparation replay = runtime.websiteModerationStore(CODE_PROTECTOR)
                    .prepareAppealDecision(
                            appealId,
                            3,
                            "approve",
                            "The appeal is supported by the reviewed evidence.",
                            REVIEWER_ID,
                            REVIEWER_RANK,
                            "decision-approve-1",
                            NOW.plusSeconds(8)
                    );
            assertTrue(replay.replayed());
            assertTrue(replay.requiresAcceptance());
            assertEquals("APPROVAL_PENDING", replay.appeal().state());
        }
    }

    @Test
    void anotherAccountCannotAppealTheClaimedPunishment() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(2);
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = claimedStore(runtime, fixture, ACCOUNT_ID);
            String otherAccount = uuid(802).toString();
            assertTrue(store.eligibleAppeals(otherAccount, 100, NOW).isEmpty());
            WebsiteModerationException error = assertThrows(
                    WebsiteModerationException.class,
                    () -> store.submitAppeal(
                            fixture.sanctionId(),
                            otherAccount,
                            PLAYER_NAME,
                            "This account does not own the punishment binding.",
                            "submission-wrong-account",
                            NOW.plusSeconds(1)
                    )
            );
            assertEquals("BINDING_ACCOUNT_MISMATCH", error.code());
        }
    }

    @Test
    void oneExactPunishmentCannotReceiveTwoOpenAppeals() throws SQLException {
        AppealFixture fixture = seedEligiblePunishment(3);
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = claimedStore(runtime, fixture, ACCOUNT_ID);
            store.submitAppeal(
                    fixture.sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    "The first durable appeal submission is valid.",
                    "submission-first-open",
                    NOW.plusSeconds(1)
            );
            WebsiteModerationException conflict = assertThrows(
                    WebsiteModerationException.class,
                    () -> store.submitAppeal(
                            fixture.sanctionId(),
                            ACCOUNT_ID,
                            PLAYER_NAME,
                            "A second open appeal must not replace the first one.",
                            "submission-second-open",
                            NOW.plusSeconds(2)
                    )
            );
            assertEquals("APPEAL_ALREADY_EXISTS", conflict.code());
            assertEquals(1, store.listAppeals(OPEN_STATE, Optional.empty(), 100, NOW).items().size());
        }
    }

    @Test
    void appealPaginationUsesTheLastReturnedRowWithoutDuplicates() throws SQLException {
        AppealFixture older = seedEligiblePunishment(6);
        AppealFixture newer = seedEligiblePunishment(7);
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = claimedStore(runtime, older, ACCOUNT_ID);
            claimedStore(runtime, newer, ACCOUNT_ID);
            UUID olderAppeal = store.submitAppeal(
                    older.sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    "The older appeal should appear on the second page.",
                    "submission-pagination-older",
                    NOW.plusSeconds(1)
            ).appeal().appealId();
            UUID newerAppeal = store.submitAppeal(
                    newer.sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    "The newer appeal should appear on the first page.",
                    "submission-pagination-newer",
                    NOW.plusSeconds(2)
            ).appeal().appealId();

            WebsiteAppealPage first = store.listAppeals(OPEN_STATE, Optional.empty(), 1, NOW.plusSeconds(3));
            assertEquals(1, first.items().size());
            assertEquals(newerAppeal, first.items().getFirst().appealId());
            assertTrue(first.nextCursor().isPresent());

            WebsiteAppealPage second = store.listAppeals(
                    OPEN_STATE,
                    first.nextCursor(),
                    1,
                    NOW.plusSeconds(3)
            );
            assertEquals(1, second.items().size());
            assertEquals(olderAppeal, second.items().getFirst().appealId());
            assertTrue(second.nextCursor().isEmpty());
        }
    }

    @Test
    void reviewerReplayKeysAreScopedToTheExactAppeal() throws SQLException {
        AppealFixture first = seedEligiblePunishment(4);
        AppealFixture second = seedEligiblePunishment(5);
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = claimedStore(runtime, first, ACCOUNT_ID);
            claimedStore(runtime, second, ACCOUNT_ID);
            UUID firstAppeal = store.submitAppeal(
                    first.sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    "The first appeal is ready for an independent decision.",
                    "submission-decision-scope-1",
                    NOW.plusSeconds(1)
            ).appeal().appealId();
            UUID secondAppeal = store.submitAppeal(
                    second.sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    "The second appeal is ready for an independent decision.",
                    "submission-decision-scope-2",
                    NOW.plusSeconds(2)
            ).appeal().appealId();

            WebsiteAppealDecisionPreparation firstDecision = store.prepareAppealDecision(
                    firstAppeal,
                    1,
                    "deny",
                    "The first exact appeal is denied after review.",
                    REVIEWER_ID,
                    REVIEWER_RANK,
                    "shared-reviewer-decision-key",
                    NOW.plusSeconds(3)
            );
            WebsiteAppealDecisionPreparation secondDecision = store.prepareAppealDecision(
                    secondAppeal,
                    1,
                    "deny",
                    "The second exact appeal is denied independently.",
                    REVIEWER_ID,
                    REVIEWER_RANK,
                    "shared-reviewer-decision-key",
                    NOW.plusSeconds(4)
            );
            assertEquals("DENIED", firstDecision.appeal().state());
            assertEquals("DENIED", secondDecision.appeal().state());
        }
    }

    private static WebsiteModerationStore claimedStore(
            MariaDbRuntime runtime,
            AppealFixture fixture,
            String accountId
    ) {
        WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
        String code = store.codeForSanction(fixture.sanctionId(), NOW).orElseThrow().code();
        store.claimCode(code, accountId, PLAYER_NAME, NOW);
        return store;
    }

    private static AppealFixture seedEligiblePunishment(int suffix) throws SQLException {
        CaseId caseId = new CaseId("%016d".formatted(suffix));
        UUID playerId = uuid(suffix);
        UUID sanctionId = uuid(400L + suffix);
        Instant issuedAt = NOW.minusSeconds(60);
        insertPlayer(DATABASE, playerId, PLAYER_NAME, issuedAt);
        insertCase(DATABASE, caseId.value(), playerId, uuid(700L + suffix), "PUBLIC", issuedAt);
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

    private static PunishmentCodeProtector testProtector() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new PunishmentCodeProtector(
                1,
                new SecretKeySpec(key, "HmacSHA256")
        );
    }

    private static UUID uuid(long suffix) {
        return new UUID(0, suffix);
    }

    private record AppealFixture(CaseId caseId, UUID sanctionId) {
    }
}
