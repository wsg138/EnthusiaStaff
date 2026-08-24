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
import java.util.UUID;
import net.enthusia.staff.domain.application.AccountLinkingService;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.AccountLinkingStore.Direction;
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
    void replacementExpiryReplayRestartRepairAndHistoryAreDurable() throws Exception {
        UUID player = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, player, "LinkV20", BASE_TIME);
        DiscordUserId discord = new DiscordUserId("18446744073709550001");
        MutableClock clock = new MutableClock(BASE_TIME);

        String firstCode;
        String secondCode;
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            AccountLinkingService service = new AccountLinkingService(
                    clock, new SecureRandom(), identities, codes, ignored -> true);

            firstCode = service.issueFromDiscord(discord).code();
            secondCode = service.issueFromDiscord(discord).code();
            assertNotEquals(firstCode, secondCode);
            assertThrows(ModerationPersistenceException.class,
                    () -> service.completeFromMinecraft(firstCode, player));

            var linked = service.completeFromMinecraft(secondCode, player);
            assertEquals(discord, linked.link().discordUserId());
            assertEquals(DiscordMinecraftLinkSource.DISCORD_CODE, linked.link().source());
            assertEquals(linked.linkId(), service.completeFromMinecraft(secondCode, player).linkId());

            assertTrue(service.unlinkFromMinecraft(player, true));
            assertFalse(identities.currentLink(player).isPresent());
            assertEquals(1, codes.historyForMinecraft(player).size());
            assertTrue(codes.historyForMinecraft(player).getFirst().link().unlinkedAt().isPresent());

            String expired = service.issueFromDiscord(discord).code();
            clock.advanceSeconds(301);
            assertThrows(ModerationPersistenceException.class,
                    () -> service.completeFromMinecraft(expired, player));
        }

        clock.set(BASE_TIME.plusSeconds(1_000));
        String repairCode;
        String repairHash;
        String repairOperation;
        DiscordUserId repairDiscord = new DiscordUserId("18446744073709550002");
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            AccountLinkingService service = new AccountLinkingService(
                    clock, new SecureRandom(), identities, codes, ignored -> true);
            repairCode = service.issueFromDiscord(repairDiscord).code();
            repairHash = sha256(repairCode);
            repairOperation = "d04-code:mc:" + repairHash.substring(0, 32) + ":" + player;
            var claim = codes.claim(repairHash, Direction.DISCORD_TO_MINECRAFT, repairOperation, clock.instant());
            identities.link(
                    repairDiscord, player, DiscordMinecraftLinkSource.DISCORD_CODE,
                    repairOperation, clock.instant().plusSeconds(1));
            assertFalse(claim.consumed());
        }
        clock.advanceSeconds(400);
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            AccountLinkingService restarted = new AccountLinkingService(
                    clock, new SecureRandom(), identities, codes, ignored -> true);
            var repaired = restarted.completeFromMinecraft(repairCode, player);
            assertEquals(repairDiscord, repaired.link().discordUserId());
            assertTrue(codes.claim(
                    repairHash, Direction.DISCORD_TO_MINECRAFT, repairOperation, clock.instant()).consumed());
        }

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
            TestDiscordSrvProvider provider = new TestDiscordSrvProvider(Map.of(
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

    private static String sha256(String raw) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(raw.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class TestDiscordSrvProvider implements DiscordSrvMigrationService.DiscordSrvLinkProvider {
        private final Map<String, UUID> links;

        private TestDiscordSrvProvider(Map<String, UUID> links) {
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

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        void set(Instant value) {
            instant = value;
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
