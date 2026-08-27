package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.application.AccountLinkRecoveryService;
import net.enthusia.staff.domain.application.AccountLinkingService;
import net.enthusia.staff.domain.application.ActivePlaytimeProvider;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService;
import net.enthusia.staff.domain.application.MainAccountSelectionService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.moderation.AccountLinkAuditAction;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.persistence.JdbcAccountLinkAuditStore;
import net.enthusia.staff.persistence.JdbcAccountLinkingStore;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordAccountLinkingV20IntegrationTest {
    private static final Instant BASE_TIME = Instant.parse("2026-08-23T21:00:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_discord_v20")
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
    void replacementExpiryReplayRestartAndHistoryAreDurable() throws Exception {
        UUID player = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, player, "LinkV20", BASE_TIME);
        DiscordUserId discord = new DiscordUserId("18446744073709550001");
        MutableClock clock = new MutableClock(BASE_TIME);

        LinkReplayFixture fixture = createAndConsumeReplacementCodes(player, discord, clock);
        verifyRestartUnlinkAndExpiry(player, discord, clock, fixture);
        assertRawCodesNotPersisted(fixture.firstCode(), fixture.secondCode());
    }

    private static LinkReplayFixture createAndConsumeReplacementCodes(
            UUID player,
            DiscordUserId discord,
            MutableClock clock
    ) throws Exception {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            AccountLinkingService service = linkingService(clock, dataSource, identities, codes, ignored -> OptionalLong.empty());
            String firstCode = service.issueFromDiscord(discord).code();
            String secondCode = service.issueFromDiscord(discord).code();
            assertNotEquals(firstCode, secondCode);
            assertThrows(ModerationPersistenceException.class,
                    () -> service.completeFromMinecraft(firstCode, player));

            var linked = service.completeFromMinecraft(secondCode, player);
            assertEquals(discord, linked.link().discordUserId());
            assertEquals(DiscordMinecraftLinkSource.DISCORD_CODE, linked.link().source());
            assertEquals(linked.linkId(), service.completeFromMinecraft(secondCode, player).linkId());
            return new LinkReplayFixture(firstCode, secondCode, linked.linkId());
        }
    }

    private static void verifyRestartUnlinkAndExpiry(
            UUID player,
            DiscordUserId discord,
            MutableClock clock,
            LinkReplayFixture fixture
    ) throws Exception {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            AccountLinkingService restarted = linkingService(clock, dataSource, identities, codes, ignored -> OptionalLong.empty());

            assertEquals(fixture.linkedId(), restarted.completeFromMinecraft(fixture.secondCode(), player).linkId());
            assertTrue(restarted.unlinkFromMinecraft(player, true));
            assertFalse(identities.currentLink(player).isPresent());
            assertEquals(1, codes.historyForMinecraft(player).size());
            assertTrue(codes.historyForMinecraft(player).getFirst().link().unlinkedAt().isPresent());

            String expired = restarted.issueFromDiscord(discord).code();
            clock.advanceSeconds(301);
            assertThrows(ModerationPersistenceException.class,
                    () -> restarted.completeFromMinecraft(expired, player));
        }
    }

    private static void assertRawCodesNotPersisted(String firstCode, String secondCode) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) AS rows_with_raw
                     FROM discord_link_codes
                     WHERE code_hash IN (?, ?)
                     """)) {
            statement.setString(1, firstCode);
            statement.setString(2, secondCode);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(0, result.getInt("rows_with_raw"));
            }
        }
    }

    @Test
    void aCodeCanOnlyBeCompletedByOneMinecraftAccountUnderConcurrency() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Instant now = BASE_TIME.plusSeconds(500);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, first, "RaceFirst", now);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, second, "RaceSecond", now);
        DiscordUserId discord = new DiscordUserId("18446744073709550021");
        MutableClock clock = new MutableClock(now);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            AccountLinkingService service = linkingService(clock, dataSource, identities, codes, ignored -> OptionalLong.empty());
            String code = service.issueFromDiscord(discord).code();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();
            try {
                Future<?> firstAttempt = executor.submit(() -> completeAfter(start, service, code, first, successes));
                Future<?> secondAttempt = executor.submit(() -> completeAfter(start, service, code, second, successes));
                start.countDown();
                firstAttempt.get();
                secondAttempt.get();
            } finally {
                executor.shutdownNow();
            }

            assertEquals(1, successes.get());
            assertEquals(1, codes.historyForDiscord(discord).size());
            assertTrue(identities.currentLink(first).isPresent() ^ identities.currentLink(second).isPresent());
        }
    }

    @Test
    void unlinkThenDifferentDiscordRelinkPreservesHistoricalOwnership() throws Exception {
        UUID player = UUID.randomUUID();
        Instant now = BASE_TIME.plusSeconds(1_000);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, player, "RelinkV20", now);
        DiscordUserId oldDiscord = new DiscordUserId("18446744073709550031");
        DiscordUserId newDiscord = new DiscordUserId("18446744073709550032");

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            var oldLink = identities.link(
                    oldDiscord,
                    player,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-history-old-" + player,
                    now.plusSeconds(1)
            );
            identities.unlink(
                    oldDiscord,
                    player,
                    oldLink.revision(),
                    "d04-history-unlink-" + player,
                    now.plusSeconds(2)
            );
            var newLink = identities.link(
                    newDiscord,
                    player,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-history-new-" + player,
                    now.plusSeconds(3)
            );

            assertEquals(newDiscord, identities.currentLink(player).orElseThrow().link().discordUserId());
            assertTrue(identities.subjectForDiscord(oldDiscord).orElseThrow().subject().minecraftAccountIds().isEmpty());
            assertTrue(identities.subjectForDiscord(newDiscord).orElseThrow().subject().minecraftAccountIds().contains(player));
            var history = codes.historyForMinecraft(player);
            assertEquals(2, history.size());
            assertTrue(history.stream().anyMatch(link -> link.linkId().equals(oldLink.linkId())
                    && link.link().unlinkedAt().isPresent()));
            assertTrue(history.stream().anyMatch(link -> link.linkId().equals(newLink.linkId())
                    && link.link().unlinkedAt().isEmpty()));
        }
    }

    @Test
    void unlinkingCurrentMainFailsClosedUntilReplacementIsPersisted() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Instant now = BASE_TIME.plusSeconds(1_500);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, first, "MainFirst", now);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, second, "MainSecond", now);
        DiscordUserId discord = new DiscordUserId("18446744073709550041");
        MutableClock clock = new MutableClock(now.plusSeconds(10));

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            var firstLink = identities.link(
                    discord,
                    first,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-main-first-" + first,
                    now.plusSeconds(1)
            );
            identities.link(
                    discord,
                    second,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-main-second-" + second,
                    now.plusSeconds(2)
            );
            assertEquals(first, identities.subjectForDiscord(discord).orElseThrow()
                    .subject().mainMinecraftAccount().orElseThrow().playerId());

            assertThrows(ModerationPersistenceException.class, () -> identities.unlink(
                    discord,
                    first,
                    firstLink.revision(),
                    "d04-main-unsafe-" + first,
                    now.plusSeconds(3)
            ));
            assertEquals(discord, identities.currentLink(first).orElseThrow().link().discordUserId());

            ActivePlaytimeProvider playtime = playerId -> OptionalLong.of(playerId.equals(first) ? 200L : 100L);
            AccountLinkingService service = linkingService(clock, dataSource, identities, codes, playtime);
            assertTrue(service.unlinkFromMinecraft(first, true));
            assertEquals(second, identities.subjectForDiscord(discord).orElseThrow()
                    .subject().mainMinecraftAccount().orElseThrow().playerId());
            assertFalse(identities.currentLink(first).isPresent());
        }
    }

    @Test
    void playtimeProviderMissingPreservesMainAndThresholdSwitchesOnlyAtTwentyFivePercent() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Instant now = BASE_TIME.plusSeconds(1_750);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, first, "PlaytimeFirst", now);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, second, "PlaytimeSecond", now);
        DiscordUserId discord = new DiscordUserId("18446744073709550051");
        Clock clock = Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkAuditStore audits = new JdbcAccountLinkAuditStore(dataSource);
            identities.link(discord, first, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-playtime-first-" + first, now.plusSeconds(1));
            identities.link(discord, second, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-playtime-second-" + second, now.plusSeconds(2));

            MainAccountSelectionService missingProvider = new MainAccountSelectionService(
                    clock, identities, ignored -> OptionalLong.empty(), new DefaultAuthorizationPolicy(), audits);
            assertEquals(first, missingProvider.evaluate(discord).orElseThrow().playerId());

            MainAccountSelectionService belowThreshold = new MainAccountSelectionService(
                    clock,
                    identities,
                    playerId -> OptionalLong.of(playerId.equals(first) ? 100L : 124L),
                    new DefaultAuthorizationPolicy(),
                    audits
            );
            assertEquals(first, belowThreshold.evaluate(discord).orElseThrow().playerId());

            MainAccountSelectionService atThreshold = new MainAccountSelectionService(
                    clock,
                    identities,
                    playerId -> OptionalLong.of(playerId.equals(first) ? 100L : 125L),
                    new DefaultAuthorizationPolicy(),
                    audits
            );
            var changed = atThreshold.evaluate(discord).orElseThrow();
            assertEquals(second, changed.playerId());
            assertEquals(MainAccountSelectionSource.AUTOMATIC, changed.source());
        }
    }

    @Test
    void staffAuditKeysFailClosedBeforeMutationAndOverrideReplayIsIdempotent() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Instant now = BASE_TIME.plusSeconds(1_900);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, first, "AuditFirst", now);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, second, "AuditSecond", now);
        DiscordUserId discord = new DiscordUserId("18446744073709550061");
        DiscordUserId otherDiscord = new DiscordUserId("18446744073709550062");
        Actor admin = new Actor(UUID.randomUUID(), "D04Admin", StaffRank.ADMIN);
        Clock clock = Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkAuditStore audits = new JdbcAccountLinkAuditStore(dataSource);
            identities.link(discord, first, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-audit-first-" + first, now.plusSeconds(1));
            identities.link(discord, second, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-audit-second-" + second, now.plusSeconds(2));
            MainAccountSelectionService mainAccounts = new MainAccountSelectionService(
                    clock, identities, ignored -> OptionalLong.empty(), new DefaultAuthorizationPolicy(), audits);

            verifyMainOverrideReplay(first, second, discord, admin, identities, audits, mainAccounts);
            verifyRecoveryAuditKeyReuse(first, discord, otherDiscord, admin, clock, identities, audits, mainAccounts);
        }
    }

    private static void verifyMainOverrideReplay(
            UUID first,
            UUID second,
            DiscordUserId discord,
            Actor admin,
            JdbcDiscordModerationPersistenceStore identities,
            JdbcAccountLinkAuditStore audits,
            MainAccountSelectionService mainAccounts
    ) {
        assertThrows(IllegalArgumentException.class,
                () -> mainAccounts.setStaffOverride(admin, discord, second, ""));
        assertEquals(first, identities.subjectForDiscord(discord).orElseThrow()
                .subject().mainMinecraftAccount().orElseThrow().playerId());

        String setKey = "d04-main-override-set";
        var override = mainAccounts.setStaffOverride(admin, discord, second, setKey);
        assertEquals(second, override.playerId());
        assertEquals(MainAccountSelectionSource.STAFF_OVERRIDE, override.source());
        assertEquals(AccountLinkAuditAction.MAIN_OVERRIDE_SET,
                audits.findByOperationKey(setKey).orElseThrow().action());
        long setRevision = identities.subjectForDiscord(discord).orElseThrow().revision();
        assertEquals(override, mainAccounts.setStaffOverride(admin, discord, second, setKey));
        assertEquals(setRevision, identities.subjectForDiscord(discord).orElseThrow().revision());

        String clearKey = "d04-main-override-clear";
        var automatic = mainAccounts.clearStaffOverride(admin, discord, clearKey);
        assertEquals(second, automatic.playerId());
        assertEquals(MainAccountSelectionSource.AUTOMATIC, automatic.source());
        assertEquals(AccountLinkAuditAction.MAIN_OVERRIDE_CLEAR,
                audits.findByOperationKey(clearKey).orElseThrow().action());
        long clearRevision = identities.subjectForDiscord(discord).orElseThrow().revision();
        assertEquals(automatic, mainAccounts.clearStaffOverride(admin, discord, clearKey));
        assertEquals(clearRevision, identities.subjectForDiscord(discord).orElseThrow().revision());
    }

    private static void verifyRecoveryAuditKeyReuse(
            UUID first,
            DiscordUserId discord,
            DiscordUserId otherDiscord,
            Actor admin,
            Clock clock,
            JdbcDiscordModerationPersistenceStore identities,
            JdbcAccountLinkAuditStore audits,
            MainAccountSelectionService mainAccounts
    ) {
        AccountLinkRecoveryService recovery = new AccountLinkRecoveryService(
                clock, new DefaultAuthorizationPolicy(), identities, audits, mainAccounts);
        String recoveryKey = "d04-recovery-audit-key";
        recovery.forceLink(admin, discord, first, recoveryKey);
        assertEquals(AccountLinkAuditAction.FORCE_LINK,
                audits.findByOperationKey(recoveryKey).orElseThrow().action());
        assertThrows(IllegalStateException.class,
                () -> recovery.reassign(admin, otherDiscord, first, recoveryKey));
        assertEquals(discord, identities.currentLink(first).orElseThrow().link().discordUserId());
    }

    @Test
    void discordSrvSnapshotImportIsIdempotentAndDoesNotOverwriteConflicts() throws Exception {
        UUID importedPlayer = UUID.randomUUID();
        UUID conflictPlayer = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, importedPlayer, "ImportedV20", BASE_TIME.plusSeconds(2_000));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, conflictPlayer, "ConflictV20", BASE_TIME.plusSeconds(2_000));
        DiscordUserId importedDiscord = new DiscordUserId("18446744073709550011");
        DiscordUserId authoritativeDiscord = new DiscordUserId("18446744073709550012");
        DiscordUserId legacyConflictDiscord = new DiscordUserId("18446744073709550013");

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            identities.ensureMinecraftSubject(importedPlayer, BASE_TIME.plusSeconds(2_001));
            identities.ensureMinecraftSubject(conflictPlayer, BASE_TIME.plusSeconds(2_001));
            identities.link(
                    authoritativeDiscord, conflictPlayer, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "d04-authoritative-conflict", BASE_TIME.plusSeconds(2_002));
            DiscordSrvMigrationService service = new DiscordSrvMigrationService(
                    Clock.fixed(BASE_TIME.plusSeconds(2_003), ZoneOffset.UTC), identities);
            FakeDiscordSrvProvider provider = new FakeDiscordSrvProvider(Map.of(
                    importedDiscord.value(), importedPlayer,
                    legacyConflictDiscord.value(), conflictPlayer));

            var first = service.importSnapshot(provider);
            assertEquals(1, first.imported());
            assertEquals(0, first.unchanged());
            assertEquals(1, first.conflicts().size());
            assertEquals(authoritativeDiscord,
                    identities.currentLink(conflictPlayer).orElseThrow().link().discordUserId());

            var replay = service.importSnapshot(provider);
            assertEquals(0, replay.imported());
            assertEquals(1, replay.unchanged());
            assertEquals(1, replay.conflicts().size());
            assertEquals(importedDiscord,
                    identities.currentLink(importedPlayer).orElseThrow().link().discordUserId());
        }
    }

    private static AccountLinkingService linkingService(
            Clock clock,
            HikariDataSource dataSource,
            JdbcDiscordModerationPersistenceStore identities,
            JdbcAccountLinkingStore codes,
            ActivePlaytimeProvider playtime
    ) {
        MainAccountSelectionService mainAccounts = new MainAccountSelectionService(
                clock,
                identities,
                playtime,
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

    private static void completeAfter(
            CountDownLatch start,
            AccountLinkingService service,
            String code,
            UUID playerId,
            AtomicInteger successes
    ) {
        try {
            start.await();
            service.completeFromMinecraft(code, playerId);
            successes.incrementAndGet();
        } catch (RuntimeException expectedRaceLoss) {
            assertTrue(expectedRaceLoss instanceof ModerationPersistenceException);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static final class FakeDiscordSrvProvider implements DiscordSrvMigrationService.DiscordSrvLinkProvider {
        private final Map<String, UUID> links;

        private FakeDiscordSrvProvider(Map<String, UUID> links) {
            this.links = links;
        }

        @Override
        public Map<String, UUID> snapshotLinks() {
            return links;
        }

        @Override
        public DiscordSrvMigrationService.MirrorResult mirrorMain(String discordUserId, UUID minecraftPlayerId) {
            return DiscordSrvMigrationService.MirrorResult.UNCHANGED;
        }
    }

    private record LinkReplayFixture(String firstCode, String secondCode, UUID linkedId) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
