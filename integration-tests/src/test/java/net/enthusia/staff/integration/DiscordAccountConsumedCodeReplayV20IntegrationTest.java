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
import java.util.List;
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
        String code = consumeAndUnlinkCode(playerId, discordUserId, clock);
        verifyConsumedCodeCannotReplay(playerId, code, clock);
    }

    private static String consumeAndUnlinkCode(UUID playerId, DiscordUserId discordUserId, Clock clock)
            throws Exception {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            AccountLinkingService service = service(clock, dataSource, identities, codes);
            String code = service.issueFromDiscord(discordUserId).code();
            service.completeFromMinecraft(code, playerId);
            assertTrue(service.unlinkFromMinecraft(playerId, true));
            assertTrue(identities.currentLink(playerId).isEmpty());
            return code;
        }
    }

    private static void verifyConsumedCodeCannotReplay(UUID playerId, String code, Clock clock) throws Exception {
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
        RecoveryPlayers players = createRecoveryPlayers();
        DiscordUserId firstDiscord = new DiscordUserId("18446744073709550121");
        DiscordUserId secondDiscord = new DiscordUserId("18446744073709550122");
        Actor admin = new Actor(UUID.randomUUID(), "ReplayAdmin", StaffRank.ADMIN);
        Clock clock = Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            RecoveryContext context = recoveryContext(dataSource, firstDiscord, secondDiscord, admin, clock);
            verifyStaleForceLink(context, players.forceLinkPlayer());
            verifyStaleForceUnlink(context, players.forceUnlinkPlayer());
            verifyStaleReassign(context, players.reassignPlayer());
            verifyStaleNoOpForceLink(context, players.noOpLinkPlayer());
            verifyStaleNoOpForceUnlink(context, players.noOpUnlinkPlayer());
            verifyStaleNoOpReassign(context, players.noOpReassignPlayer());
        }
    }

    private static RecoveryPlayers createRecoveryPlayers() throws Exception {
        RecoveryPlayers players = new RecoveryPlayers(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        for (UUID playerId : players.all()) {
            MariaDbIntegrationSupport.insertPlayer(
                    DATABASE, playerId, "Replay" + playerId.toString().substring(0, 8), NOW.plusSeconds(20));
        }
        return players;
    }

    private static RecoveryContext recoveryContext(
            HikariDataSource dataSource,
            DiscordUserId firstDiscord,
            DiscordUserId secondDiscord,
            Actor admin,
            Clock clock
    ) {
        JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
        JdbcAccountLinkAuditStore audits = new JdbcAccountLinkAuditStore(dataSource);
        MainAccountSelectionService mainAccounts = new MainAccountSelectionService(
                clock, identities, ignored -> OptionalLong.empty(), new DefaultAuthorizationPolicy(), audits);
        AccountLinkRecoveryService recovery = new AccountLinkRecoveryService(
                clock, new DefaultAuthorizationPolicy(), identities, audits, mainAccounts);
        return new RecoveryContext(identities, recovery, firstDiscord, secondDiscord, admin);
    }

    private static void verifyStaleForceLink(RecoveryContext context, UUID playerId) {
        String key = "d04-stale-force-link";
        context.recovery().forceLink(context.admin(), context.firstDiscord(), playerId, key);
        assertTrue(context.recovery().forceUnlink(
                context.admin(), context.firstDiscord(), playerId, "d04-after-force-link"));
        assertThrows(IllegalStateException.class,
                () -> context.recovery().forceLink(context.admin(), context.firstDiscord(), playerId, key));
        assertTrue(context.identities().currentLink(playerId).isEmpty());
    }

    private static void verifyStaleForceUnlink(RecoveryContext context, UUID playerId) {
        context.recovery().forceLink(context.admin(), context.firstDiscord(), playerId, "d04-force-unlink-seed");
        String key = "d04-stale-force-unlink";
        assertTrue(context.recovery().forceUnlink(context.admin(), context.firstDiscord(), playerId, key));
        context.identities().link(
                context.firstDiscord(), playerId, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "d04-force-unlink-relink", NOW.plusSeconds(31));
        assertThrows(IllegalStateException.class,
                () -> context.recovery().forceUnlink(context.admin(), context.firstDiscord(), playerId, key));
        assertEquals(context.firstDiscord(),
                context.identities().currentLink(playerId).orElseThrow().link().discordUserId());
    }

    private static void verifyStaleReassign(RecoveryContext context, UUID playerId) {
        context.recovery().forceLink(context.admin(), context.firstDiscord(), playerId, "d04-reassign-seed");
        String key = "d04-stale-reassign";
        var reassigned = context.recovery().reassign(context.admin(), context.secondDiscord(), playerId, key);
        context.identities().unlink(
                context.secondDiscord(), playerId, reassigned.revision(),
                "d04-reassign-later-unlink", NOW.plusSeconds(31));
        assertThrows(IllegalStateException.class,
                () -> context.recovery().reassign(context.admin(), context.secondDiscord(), playerId, key));
        assertTrue(context.identities().currentLink(playerId).isEmpty());
    }

    private static void verifyStaleNoOpForceLink(RecoveryContext context, UUID playerId) {
        var link = context.identities().link(
                context.firstDiscord(), playerId, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "d04-noop-force-link-seed", NOW.plusSeconds(31));
        String key = "d04-noop-force-link";
        context.recovery().forceLink(context.admin(), context.firstDiscord(), playerId, key);
        context.identities().unlink(
                context.firstDiscord(), playerId, link.revision(),
                "d04-noop-force-link-later-unlink", NOW.plusSeconds(32));
        assertThrows(IllegalStateException.class,
                () -> context.recovery().forceLink(context.admin(), context.firstDiscord(), playerId, key));
        assertTrue(context.identities().currentLink(playerId).isEmpty());
    }

    private static void verifyStaleNoOpForceUnlink(RecoveryContext context, UUID playerId) {
        String key = "d04-noop-force-unlink";
        assertFalse(context.recovery().forceUnlink(context.admin(), context.firstDiscord(), playerId, key));
        context.identities().link(
                context.firstDiscord(), playerId, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "d04-noop-force-unlink-later-link", NOW.plusSeconds(31));
        assertThrows(IllegalStateException.class,
                () -> context.recovery().forceUnlink(context.admin(), context.firstDiscord(), playerId, key));
        assertEquals(context.firstDiscord(),
                context.identities().currentLink(playerId).orElseThrow().link().discordUserId());
    }

    private static void verifyStaleNoOpReassign(RecoveryContext context, UUID playerId) {
        var link = context.identities().link(
                context.secondDiscord(), playerId, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "d04-noop-reassign-seed", NOW.plusSeconds(31));
        String key = "d04-noop-reassign";
        context.recovery().reassign(context.admin(), context.secondDiscord(), playerId, key);
        context.identities().unlink(
                context.secondDiscord(), playerId, link.revision(),
                "d04-noop-reassign-later-unlink", NOW.plusSeconds(32));
        assertThrows(IllegalStateException.class,
                () -> context.recovery().reassign(context.admin(), context.secondDiscord(), playerId, key));
        assertTrue(context.identities().currentLink(playerId).isEmpty());
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
            MainReplayContext context = mainReplayContext(
                    dataSource, firstPlayer, secondPlayer, discordUserId, admin, clock);
            verifySetAndClearReplay(context);
            verifyStaleClearReplay(context);
            verifyNoOpClearReplay(context);
        }
    }

    private static MainReplayContext mainReplayContext(
            HikariDataSource dataSource,
            UUID firstPlayer,
            UUID secondPlayer,
            DiscordUserId discordUserId,
            Actor admin,
            Clock clock
    ) {
        JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
        JdbcAccountLinkAuditStore audits = new JdbcAccountLinkAuditStore(dataSource);
        identities.link(
                discordUserId, firstPlayer, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "d04-main-replay-first", NOW.plusSeconds(41));
        identities.link(
                discordUserId, secondPlayer, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "d04-main-replay-second", NOW.plusSeconds(42));
        MainAccountSelectionService mainAccounts = new MainAccountSelectionService(
                clock, identities, ignored -> OptionalLong.empty(), new DefaultAuthorizationPolicy(), audits);
        return new MainReplayContext(identities, mainAccounts, discordUserId, admin, firstPlayer, secondPlayer);
    }

    private static void verifySetAndClearReplay(MainReplayContext context) {
        String setKey = "d04-main-replay-set";
        var override = context.mainAccounts().setStaffOverride(
                context.admin(), context.discordUserId(), context.secondPlayer(), setKey);
        assertEquals(MainAccountSelectionSource.STAFF_OVERRIDE, override.source());
        assertEquals(override, context.mainAccounts().setStaffOverride(
                context.admin(), context.discordUserId(), context.secondPlayer(), setKey));
        String clearKey = "d04-main-replay-clear";
        var automatic = context.mainAccounts().clearStaffOverride(context.admin(), context.discordUserId(), clearKey);
        assertEquals(MainAccountSelectionSource.AUTOMATIC, automatic.source());
        assertEquals(automatic, context.mainAccounts().clearStaffOverride(context.admin(), context.discordUserId(), clearKey));
        assertThrows(IllegalStateException.class, () -> context.mainAccounts().setStaffOverride(
                context.admin(), context.discordUserId(), context.secondPlayer(), setKey));
    }

    private static void verifyStaleClearReplay(MainReplayContext context) {
        String clearKey = "d04-main-replay-clear";
        assertEquals(MainAccountSelectionSource.AUTOMATIC,
                currentMain(context).source());
        context.mainAccounts().setStaffOverride(
                context.admin(), context.discordUserId(), context.firstPlayer(), "d04-main-replay-set-later");
        assertThrows(IllegalStateException.class,
                () -> context.mainAccounts().clearStaffOverride(context.admin(), context.discordUserId(), clearKey));
        var currentOverride = currentMain(context);
        assertEquals(context.firstPlayer(), currentOverride.playerId());
        assertEquals(MainAccountSelectionSource.STAFF_OVERRIDE, currentOverride.source());
    }

    private static void verifyNoOpClearReplay(MainReplayContext context) {
        context.mainAccounts().clearStaffOverride(
                context.admin(), context.discordUserId(), "d04-main-replay-fresh-clear");
        String key = "d04-main-replay-noop-clear";
        var noOpClear = context.mainAccounts().clearStaffOverride(context.admin(), context.discordUserId(), key);
        assertEquals(MainAccountSelectionSource.AUTOMATIC, noOpClear.source());
        context.mainAccounts().setStaffOverride(
                context.admin(), context.discordUserId(), context.secondPlayer(), "d04-main-replay-after-noop");
        assertThrows(IllegalStateException.class,
                () -> context.mainAccounts().clearStaffOverride(context.admin(), context.discordUserId(), key));
        var finalMain = currentMain(context);
        assertEquals(context.secondPlayer(), finalMain.playerId());
        assertEquals(MainAccountSelectionSource.STAFF_OVERRIDE, finalMain.source());
    }

    private static net.enthusia.staff.domain.moderation.MainMinecraftAccount currentMain(MainReplayContext context) {
        return context.identities().subjectForDiscord(context.discordUserId()).orElseThrow()
                .subject().mainMinecraftAccount().orElseThrow();
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
                clock, new SecureRandom(), identities, codes, ignored -> true, mainAccounts);
    }

    private record RecoveryPlayers(
            UUID forceLinkPlayer,
            UUID forceUnlinkPlayer,
            UUID reassignPlayer,
            UUID noOpLinkPlayer,
            UUID noOpUnlinkPlayer,
            UUID noOpReassignPlayer
    ) {
        private List<UUID> all() {
            return List.of(forceLinkPlayer, forceUnlinkPlayer, reassignPlayer,
                    noOpLinkPlayer, noOpUnlinkPlayer, noOpReassignPlayer);
        }
    }

    private record RecoveryContext(
            JdbcDiscordModerationPersistenceStore identities,
            AccountLinkRecoveryService recovery,
            DiscordUserId firstDiscord,
            DiscordUserId secondDiscord,
            Actor admin
    ) {
    }

    private record MainReplayContext(
            JdbcDiscordModerationPersistenceStore identities,
            MainAccountSelectionService mainAccounts,
            DiscordUserId discordUserId,
            Actor admin,
            UUID firstPlayer,
            UUID secondPlayer
    ) {
    }
}
