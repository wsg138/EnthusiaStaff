package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.clearWebsiteModerationFixtures;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayerName;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertSanction;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentFilter;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import net.enthusia.staff.domain.website.PublicPunishmentState;
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
class WebsitePublicRegistryIntegrationTest {
    private static final int KEY_VERSION = 1;
    private static final int CODE_GENERATION = 1;
    private static final int QUERY_LIMIT = 20;
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String PUBLIC_VISIBILITY = "PUBLIC";
    private static final String PRIVATE_VISIBILITY = "PRIVATE";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String BAN_TYPE = "BAN";
    private static final String MUTE_TYPE = "MUTE";
    private static final String CURRENT_NAME = "CurrentName";
    private static final String TEST_USERNAME = "website_registry_user";
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
            .withDatabaseName("enthusia_staff_website_registry_test")
            .withUsername(TEST_USERNAME)
            .withPassword(TEST_PASSWORD);

    @BeforeAll
    static void migrateSchema() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            assertNotNull(runtime.operationalStateStore());
        }
    }

    @BeforeEach
    void clearWebsiteFixtures() throws SQLException {
        clearWebsiteModerationFixtures(DATABASE);
    }

    @Test
    void listsOnlyPublicSafeSanctionsAndDerivesAppealState() throws SQLException {
        VisibilityCases fixtures = seedVisibilityFixtures();

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            PublicPunishmentPage page = store.listPublic(
                    PublicPunishmentFilter.ALL,
                    Optional.empty(),
                    QUERY_LIMIT,
                    NOW
            );

            assertEquals(2, page.items().size());
            assertTrue(page.nextCursor().isEmpty());
            assertActivePunishment(page.items().getFirst(), fixtures.activeCase());
            assertExpiredPunishment(page.items().getLast(), fixtures.expiredCase());
            assertEquals(1, store.listPublic(
                    PublicPunishmentFilter.BAN, Optional.empty(), QUERY_LIMIT, NOW
            ).items().size());
            assertEquals(1, store.listPublic(
                    PublicPunishmentFilter.MUTE, Optional.empty(), QUERY_LIMIT, NOW
            ).items().size());
            assertTrue(store.listPublic(
                    PublicPunishmentFilter.WARNING, Optional.empty(), QUERY_LIMIT, NOW
            ).items().isEmpty());
            assertTrue(store.publicCase(fixtures.activeCase(), NOW).isPresent());
            assertTrue(store.publicCase(caseId(3), NOW).isEmpty());
        }
    }

    @Test
    void cursorPaginationIsStableForEqualIssueTimes() throws SQLException {
        Instant issuedAt = NOW.minusSeconds(60);
        insertPublicPunishment(
                uuid(11), "PageOne", caseId(11), uuid(201), BAN_TYPE,
                PUBLIC_VISIBILITY, issuedAt, null
        );
        insertPublicPunishment(
                uuid(12), "PageTwo", caseId(12), uuid(202), BAN_TYPE,
                PUBLIC_VISIBILITY, issuedAt, null
        );
        insertPublicPunishment(
                uuid(13), "PageThree", caseId(13), uuid(203), BAN_TYPE,
                PUBLIC_VISIBILITY, issuedAt, null
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            PublicPunishmentPage first = store.listPublic(
                    PublicPunishmentFilter.ALL,
                    Optional.empty(),
                    2,
                    NOW
            );
            PublicPunishmentPage second = store.listPublic(
                    PublicPunishmentFilter.ALL,
                    first.nextCursor(),
                    2,
                    NOW
            );

            assertEquals(List.of("PageThree", "PageTwo"), players(first));
            assertTrue(first.nextCursor().isPresent());
            assertEquals(List.of("PageOne"), players(second));
            assertTrue(second.nextCursor().isEmpty());
        }
    }

    @Test
    void exactSearchUsesCurrentNameHistoryAndCaseId() throws SQLException {
        UUID playerId = uuid(21);
        CaseId publicCase = caseId(21);
        insertPublicPunishment(
                playerId, CURRENT_NAME, publicCase, uuid(301), BAN_TYPE,
                PUBLIC_VISIBILITY, NOW.minusSeconds(60), null
        );
        insertHistoricName(playerId, "FormerName");
        insertPublicPunishment(
                uuid(22), "HiddenName", caseId(22), uuid(302), BAN_TYPE,
                PRIVATE_VISIBILITY, NOW.minusSeconds(30), null
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);

            assertEquals(List.of(CURRENT_NAME), players(store.searchPublic("currentname", QUERY_LIMIT, NOW)));
            assertEquals(List.of(CURRENT_NAME), players(store.searchPublic("formername", QUERY_LIMIT, NOW)));
            assertEquals(List.of(CURRENT_NAME), players(store.searchPublic(publicCase.value(), QUERY_LIMIT, NOW)));
            assertTrue(store.searchPublic("Current", QUERY_LIMIT, NOW).isEmpty());
            assertTrue(store.searchPublic("HiddenName", QUERY_LIMIT, NOW).isEmpty());
            WebsiteModerationException invalid = assertThrows(
                    WebsiteModerationException.class,
                    () -> store.searchPublic("*", QUERY_LIMIT, NOW)
            );
            assertEquals("INVALID_SEARCH", invalid.code());
        }
    }

    private static void assertActivePunishment(PublicPunishment punishment, CaseId expectedCase) {
        assertEquals("ActivePlayer", punishment.player());
        assertEquals(BAN_TYPE, punishment.punishmentType());
        assertEquals("TEST", punishment.broadReason());
        assertEquals("Integration test", punishment.publicReason());
        assertEquals(PublicPunishmentState.ACTIVE, punishment.state());
        assertEquals(expectedCase, punishment.caseId());
        assertTrue(punishment.appealAvailable());
        assertEquals(3_600, punishment.remainingSeconds().orElseThrow());
    }

    private static void assertExpiredPunishment(PublicPunishment punishment, CaseId expectedCase) {
        assertEquals("ExpiredPlayer", punishment.player());
        assertEquals(MUTE_TYPE, punishment.punishmentType());
        assertEquals(PublicPunishmentState.EXPIRED, punishment.state());
        assertEquals(expectedCase, punishment.caseId());
        assertFalse(punishment.appealAvailable());
        assertEquals(0, punishment.remainingSeconds().orElseThrow());
    }

    private static List<String> players(PublicPunishmentPage page) {
        return players(page.items());
    }

    private static List<String> players(List<PublicPunishment> punishments) {
        return punishments.stream().map(PublicPunishment::player).toList();
    }

    private static VisibilityCases seedVisibilityFixtures() throws SQLException {
        CaseId activeCase = caseId(1);
        UUID activeSanction = uuid(101);
        insertPublicPunishment(
                uuid(1), "ActivePlayer", activeCase, activeSanction, BAN_TYPE,
                PUBLIC_VISIBILITY, NOW.minusSeconds(60), NOW.plusSeconds(3_600)
        );
        insertCode(activeSanction, activeCase, ACTIVE_STATUS);

        CaseId expiredCase = caseId(2);
        insertPublicPunishment(
                uuid(2), "ExpiredPlayer", expiredCase, uuid(102), MUTE_TYPE,
                PUBLIC_VISIBILITY, NOW.minusSeconds(120), NOW
        );
        insertPublicPunishment(
                uuid(3), "PrivatePlayer", caseId(3), uuid(103), BAN_TYPE,
                PRIVATE_VISIBILITY, NOW.minusSeconds(180), NOW.plusSeconds(3_600)
        );
        insertPublicPunishment(
                uuid(4), "InventoryPlayer", caseId(4), uuid(104), "INVENTORY_CONFISCATION",
                PUBLIC_VISIBILITY, NOW.minusSeconds(240), null
        );
        insertPublicPunishment(
                uuid(5), "Bad Name", caseId(5), uuid(105), BAN_TYPE,
                PUBLIC_VISIBILITY, NOW.minusSeconds(300), NOW.plusSeconds(3_600)
        );
        return new VisibilityCases(activeCase, expiredCase);
    }

    private static void insertPublicPunishment(
            UUID playerId,
            String username,
            CaseId caseId,
            UUID sanctionId,
            String sanctionType,
            String visibility,
            Instant issuedAt,
            Instant expiration
    ) throws SQLException {
        insertPlayer(DATABASE, playerId, username, issuedAt);
        insertCase(DATABASE, caseId.value(), playerId, uuid(900), visibility, issuedAt);
        insertSanction(
                DATABASE,
                sanctionId,
                caseId.value(),
                playerId,
                sanctionType,
                ACTIVE_STATUS,
                issuedAt,
                expiration
        );
    }

    private static void insertCode(UUID sanctionId, CaseId caseId, String status) throws SQLException {
        String derived = CODE_PROTECTOR.code(sanctionId, CODE_GENERATION);
        try (Connection database = connection(DATABASE);
             PreparedStatement statement = database.prepareStatement("""
                     INSERT INTO punishment_codes(
                         sanction_id, case_id, key_version, generation, code_hash, status, created_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(sanctionId));
            statement.setString(2, caseId.value());
            statement.setInt(3, KEY_VERSION);
            statement.setInt(4, CODE_GENERATION);
            statement.setBytes(5, CODE_PROTECTOR.verificationHash(derived));
            statement.setString(6, status);
            statement.setTimestamp(7, Timestamp.from(NOW));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertHistoricName(UUID playerId, String username) throws SQLException {
        insertPlayerName(DATABASE, playerId, username, NOW.minusSeconds(600), NOW.minusSeconds(300));
    }

    private static CaseId caseId(long suffix) {
        return new CaseId("%016d".formatted(suffix));
    }

    private static UUID uuid(long suffix) {
        return new UUID(0L, suffix);
    }

    private record VisibilityCases(CaseId activeCase, CaseId expiredCase) {
    }
}
