package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.UUID;
import net.enthusia.staff.domain.application.AccountLinkRecoveryService;
import net.enthusia.staff.domain.application.AccountLinkingService;
import net.enthusia.staff.domain.application.MainAccountSelectionService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.persistence.JdbcAccountLinkAuditStore;
import net.enthusia.staff.persistence.JdbcAccountLinkingStore;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.MariaDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordAccountConsumedCodeReplayV20IntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-25T03:05:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_discord_stale_replay_v20")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void consumedCodeCannotReportSuccessAfterItsLinkWasUnlinkedAcrossRestart() throws Exception {
        UUID playerId = UUID.randomUUID();
        DiscordUserId discordUserId = new DiscordUserId("18446744073709550111");
        MariaDbIntegrationSupport.insertPlayer(DATABASE, playerId, "StaleReplayV20", NOW);
        Clock clock = Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC);
        String code;

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            AccountLinkingService service = service(clock, dataSource, identities, codes);

            code = service.issueFromDiscord(discordUserId).code();
            service.completeFromMinecraft(code, playerId);
            assertTrue(service.unlinkFromMinecraft(playerId, true));
            assertTrue(identities.currentLink(playerId).isEmpty());
        }

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            AccountLinkingService restarted = service(clock, dataSource, identities, codes);

            assertThrows(IllegalStateException.class, () -> restarted.completeFromMinecraft(code, playerId));
            assertTrue(identities.currentLink(playerId).isEmpty());
            var history = codes.historyForMinecraft(playerId);
            assertEquals(1, history.size());
            assertTrue(history.getFirst().link().unlinkedAt().isPresent());
        }
    }

    @Test
    void auditedRecoveryReplaysFailClosedAfterLaterLinkStateChanges() throws Exception {
        UUID forceLinkPlayer = UUID.randomUUID();
        UUID forceUnlinkPlayer = UUID.randomUUID();
        UUID reassignPlayer = UUID.randomUUID();
        UUID noOpLinkPlayer = UUID.randomUUID();
        UUID noOpUnlinkPlayer = UUID.randomUUID();
        UUID noOpReassignPlayer = UUID.randomUUID();
        for (UUID playerId : java.util.List.of(
                forceLinkPlayer, forceUnlinkPlayer, reassignPlayer,
                noOpLinkPlayer, noOpUnlinkPlayer, noOpReassignPlayer)) {
            MariaDbIntegrationSupport.insertPlayer(DATABASE, playerId, "Replay" + playerId.toString().substring(0, 8),
                    NOW.plusSeconds(20));
        }
        DiscordUserId firstDiscord = new DiscordUserId("18446744073709550121");
        DiscordUserId secondDiscord = new DiscordUserId("18446744073709550122");
        Actor admin = new Actor(UUID.randomUUID(), "ReplayAdmin", StaffRank.ADMIN);
        Clock clock = Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkAuditStore audits = new JdbcAccountLinkAuditStore(dataSource);
            MainAccountSelectionService mainAccounts = new MainAccountSelectionService(
                    clock,
                    identities,
                    ignored -> OptionalLong.empty(),
                    new DefaultAuthorizationPolicy(),
                    audits
            );
            AccountLinkRecoveryService recovery = new AccountLinkRecoveryService(
                    clock, new DefaultAuthorizationPolicy(), identities, audits, mainAccounts);

            String staleLinkKey = "d04-stale-force-link";
            recovery.forceLink(admin, firstDiscord, forceLinkPlayer, staleLinkKey);
            assertTrue(recovery.forceUnlink(admin, firstDiscord, forceLinkPlayer, "d04-after-force-link"));
            assertThrows(IllegalStateException.class,
                    () -> recovery.forceLink(admin, firstDiscord, forceLinkPlayer, staleLinkKey));
            assertTrue(identities.currentLink(forceLinkPlayer).isEmpty());

            recovery.forceLink(admin, firstDiscord, forceUnlinkPlayer, "d04-force-unlink-seed");
            String staleUnlinkKey = "d04-stale-force-unlink";
            assertTrue(recovery.forceUnlink(admin, firstDiscord, forceUnlinkPlayer, staleUnlinkKey));
            identities.link(
                    firstDiscord,
                    forceUnlinkPlayer,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-force-unlink-relink",
                    NOW.plusSeconds(31)
            );
            assertThrows(IllegalStateException.class,
                    () -> recovery.forceUnlink(admin, firstDiscord, forceUnlinkPlayer, staleUnlinkKey));
            assertEquals(firstDiscord,
                    identities.currentLink(forceUnlinkPlayer).orElseThrow().link().discordUserId());

            recovery.forceLink(admin, firstDiscord, reassignPlayer, "d04-reassign-seed");
            String staleReassignKey = "d04-stale-reassign";
            var reassigned = recovery.reassign(admin, secondDiscord, reassignPlayer, staleReassignKey);
            identities.unlink(
                    secondDiscord,
                    reassignPlayer,
                    reassigned.revision(),
                    "d04-reassign-later-unlink",
                    NOW.plusSeconds(31)
            );
            assertThrows(IllegalStateException.class,
                    () -> recovery.reassign(admin, secondDiscord, reassignPlayer, staleReassignKey));
            assertTrue(identities.currentLink(reassignPlayer).isEmpty());

            var noOpLink = identities.link(
                    firstDiscord,
                    noOpLinkPlayer,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-noop-force-link-seed",
                    NOW.plusSeconds(31)
            );
            String noOpLinkKey = "d04-noop-force-link";
            recovery.forceLink(admin, firstDiscord, noOpLinkPlayer, noOpLinkKey);
            identities.unlink(
                    firstDiscord,
                    noOpLinkPlayer,
                    noOpLink.revision(),
                    "d04-noop-force-link-later-unlink",
                    NOW.plusSeconds(32)
            );
            assertThrows(IllegalStateException.class,
                    () -> recovery.forceLink(admin, firstDiscord, noOpLinkPlayer, noOpLinkKey));
            assertTrue(identities.currentLink(noOpLinkPlayer).isEmpty());

            String noOpUnlinkKey = "d04-noop-force-unlink";
            assertFalse(recovery.forceUnlink(admin, firstDiscord, noOpUnlinkPlayer, noOpUnlinkKey));
            identities.link(
                    firstDiscord,
                    noOpUnlinkPlayer,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-noop-force-unlink-later-link",
                    NOW.plusSeconds(31)
            );
            assertThrows(IllegalStateException.class,
                    () -> recovery.forceUnlink(admin, firstDiscord, noOpUnlinkPlayer, noOpUnlinkKey));
            assertEquals(firstDiscord,
                    identities.currentLink(noOpUnlinkPlayer).orElseThrow().link().discordUserId());

            var noOpReassign = identities.link(
                    secondDiscord,
                    noOpReassignPlayer,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-noop-reassign-seed",
                    NOW.plusSeconds(31)
            );
            String noOpReassignKey = "d04-noop-reassign";
            recovery.reassign(admin, secondDiscord, noOpReassignPlayer, noOpReassignKey);
            identities.unlink(
                    secondDiscord,
                    noOpReassignPlayer,
                    noOpReassign.revision(),
                    "d04-noop-reassign-later-unlink",
                    NOW.plusSeconds(32)
            );
            assertThrows(IllegalStateException.class,
                    () -> recovery.reassign(admin, secondDiscord, noOpReassignPlayer, noOpReassignKey));
            assertTrue(identities.currentLink(noOpReassignPlayer).isEmpty());
        }
    }

    @Test
    void auditedMainOverrideReplaysVerifyCurrentAuthoritativeState() throws Exception {
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, firstPlayer, "ReplayMainOne", NOW.plusSeconds(40));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, secondPlayer, "ReplayMainTwo", NOW.plusSeconds(40));
        DiscordUserId discordUserId = new DiscordUserId("18446744073709550131");
        Actor admin = new Actor(UUID.randomUUID(), "MainReplayAdmin", StaffRank.ADMIN);
        Clock clock = Clock.fixed(NOW.plusSeconds(50), ZoneOffset.UTC);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkAuditStore audits = new JdbcAccountLinkAuditStore(dataSource);
            identities.link(
                    discordUserId,
                    firstPlayer,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-main-replay-first",
                    NOW.plusSeconds(41)
            );
            identities.link(
                    discordUserId,
                    secondPlayer,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-main-replay-second",
                    NOW.plusSeconds(42)
            );
            MainAccountSelectionService mainAccounts = new MainAccountSelectionService(
                    clock,
                    identities,
                    ignored -> OptionalLong.empty(),
                    new DefaultAuthorizationPolicy(),
                    audits
            );

            String setKey = "d04-main-replay-set";
            var override = mainAccounts.setStaffOverride(admin, discordUserId, secondPlayer, setKey);
            assertEquals(MainAccountSelectionSource.STAFF_OVERRIDE, override.source());
            assertEquals(override, mainAccounts.setStaffOverride(admin, discordUserId, secondPlayer, setKey));

            String clearKey = "d04-main-replay-clear";
            var automatic = mainAccounts.clearStaffOverride(admin, discordUserId, clearKey);
            assertEquals(MainAccountSelectionSource.AUTOMATIC, automatic.source());
            assertEquals(automatic, mainAccounts.clearStaffOverride(admin, discordUserId, clearKey));
            assertThrows(IllegalStateException.class,
                    () -> mainAccounts.setStaffOverride(admin, discordUserId, secondPlayer, setKey));
            assertEquals(MainAccountSelectionSource.AUTOMATIC,
                    identities.subjectForDiscord(discordUserId).orElseThrow()
                            .subject().mainMinecraftAccount().orElseThrow().source());

            mainAccounts.setStaffOverride(admin, discordUserId, firstPlayer, "d04-main-replay-set-later");
            assertThrows(IllegalStateException.class,
                    () -> mainAccounts.clearStaffOverride(admin, discordUserId, clearKey));
            var currentOverride = identities.subjectForDiscord(discordUserId).orElseThrow()
                    .subject().mainMinecraftAccount().orElseThrow();
            assertEquals(firstPlayer, currentOverride.playerId());
            assertEquals(MainAccountSelectionSource.STAFF_OVERRIDE, currentOverride.source());

            mainAccounts.clearStaffOverride(admin, discordUserId, "d04-main-replay-fresh-clear");
            String noOpClearKey = "d04-main-replay-noop-clear";
            var noOpClear = mainAccounts.clearStaffOverride(admin, discordUserId, noOpClearKey);
            assertEquals(MainAccountSelectionSource.AUTOMATIC, noOpClear.source());
            mainAccounts.setStaffOverride(admin, discordUserId, secondPlayer, "d04-main-replay-after-noop");
            assertThrows(IllegalStateException.class,
                    () -> mainAccounts.clearStaffOverride(admin, discordUserId, noOpClearKey));
            var finalMain = identities.subjectForDiscord(discordUserId).orElseThrow()
                    .subject().mainMinecraftAccount().orElseThrow();
            assertEquals(secondPlayer, finalMain.playerId());
            assertEquals(MainAccountSelectionSource.STAFF_OVERRIDE, finalMain.source());
        }
    }

    private static AccountLinkingService service(
            Clock clock,
            HikariDataSource dataSource,
            JdbcDiscordModerationPersistenceStore identities,
            JdbcAccountLinkingStore codes
    ) {
        MainAccountSelectionService mainAccounts = new MainAccountSelectionService(
                clock,
                identities,
                ignored -> OptionalLong.empty(),
                new DefaultAuthorizationPolicy(),
                new JdbcAccountLinkAuditStore(dataSource)
        );
        return new AccountLinkingService(
                clock,
                new SecureRandom(),
                identities,
                codes,
                ignored -> true,
                mainAccounts
        );
    }
}
