package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordGuildScope;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.EnforcementTarget;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.EvidenceMetadata;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordPersistenceV19IntegrationTest {
    private static final Instant BASE_TIME = Instant.parse("2026-08-23T18:30:00Z");

    @Container
    private static final MariaDBContainer<?> CLEAN_DATABASE = database("enthusia_staff_discord_v19_clean");

    @Container
    private static final MariaDBContainer<?> UPGRADE_DATABASE = database("enthusia_staff_discord_v19_upgrade");

    @BeforeAll
    static void migrateCleanDatabase() {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(CLEAN_DATABASE))) {
            migrate(dataSource, "19");
            migrate(dataSource, "19");
        }
    }

    @Test
    void cleanInstallCreatesV19SchemaAndRequiredUniquenessIndexes() throws Exception {
        assertEquals("19", currentFlywayVersion(CLEAN_DATABASE));
        assertTrue(indexExists(CLEAN_DATABASE, "discord_minecraft_links", "uq_discord_current_minecraft"));
        assertTrue(indexExists(CLEAN_DATABASE, "moderation_subject_discord_identities", "PRIMARY"));
        assertTrue(indexExists(CLEAN_DATABASE, "discord_security_locks", "uq_security_lock_active_user"));
        assertTrue(indexExists(CLEAN_DATABASE, "discord_maintenance_work", "idx_discord_maintenance_due"));
    }

    @Test
    void upgradeFromV18BackfillsLegacyPlayersWithoutChangingLegacyIds() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(UPGRADE_DATABASE))) {
            migrate(dataSource, "18");
        }
        MariaDbIntegrationSupport.insertPlayer(UPGRADE_DATABASE, first, "LegacyFirst", BASE_TIME);
        MariaDbIntegrationSupport.insertPlayer(
                UPGRADE_DATABASE,
                second,
                "LegacySecond",
                BASE_TIME.plusSeconds(1)
        );
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(UPGRADE_DATABASE))) {
            migrate(dataSource, "19");
        }

        assertEquals("19", currentFlywayVersion(UPGRADE_DATABASE));
        assertBackfilledSubject(UPGRADE_DATABASE, first);
        assertBackfilledSubject(UPGRADE_DATABASE, second);
    }

    @Test
    void linksAreTransactionalIdempotentAndSupportOneDiscordToManyMinecraftAccounts() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(CLEAN_DATABASE, first, "LinkedFirst", BASE_TIME);
        MariaDbIntegrationSupport.insertPlayer(CLEAN_DATABASE, second, "LinkedSecond", BASE_TIME);
        DiscordUserId discord = new DiscordUserId("18446744073709551615");
        DiscordUserId otherDiscord = new DiscordUserId("18446744073709551614");

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(CLEAN_DATABASE))) {
            JdbcDiscordModerationPersistenceStore store = new JdbcDiscordModerationPersistenceStore(dataSource);
            store.ensureMinecraftSubject(first, BASE_TIME);
            store.ensureMinecraftSubject(second, BASE_TIME);

            var firstLink = store.link(
                    discord,
                    first,
                    DiscordMinecraftLinkSource.DISCORD_CODE,
                    "d02-link-first-" + first,
                    BASE_TIME.plusSeconds(10)
            );
            var replay = store.link(
                    discord,
                    first,
                    DiscordMinecraftLinkSource.DISCORD_CODE,
                    "d02-link-first-" + first,
                    BASE_TIME.plusSeconds(10)
            );
            assertEquals(firstLink.linkId(), replay.linkId());
            assertTrue(replay.replayed());

            var secondLink = store.link(
                    discord,
                    second,
                    DiscordMinecraftLinkSource.MINECRAFT_CODE,
                    "d02-link-second-" + second,
                    BASE_TIME.plusSeconds(20)
            );
            assertEquals(firstLink.subjectId(), secondLink.subjectId());
            assertEquals(2, store.subjectForDiscord(discord).orElseThrow().subject().minecraftAccountIds().size());

            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.link(
                            otherDiscord,
                            first,
                            DiscordMinecraftLinkSource.STAFF_RECOVERY,
                            "d02-conflict-" + first,
                            BASE_TIME.plusSeconds(30)
                    )
            );
            assertEquals(discord, store.currentLink(first).orElseThrow().link().discordUserId());

            var subject = store.subjectForDiscord(discord).orElseThrow();
            var changed = store.setMainMinecraftAccount(
                    subject.subject().subjectId(),
                    new MainMinecraftAccount(second, MainAccountSelectionSource.STAFF_OVERRIDE),
                    subject.revision(),
                    BASE_TIME.plusSeconds(40)
            );
            assertEquals(second, changed.subject().mainMinecraftAccount().orElseThrow().playerId());
            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.setMainMinecraftAccount(
                            subject.subject().subjectId(),
                            new MainMinecraftAccount(first, MainAccountSelectionSource.AUTOMATIC),
                            subject.revision(),
                            BASE_TIME.plusSeconds(41)
                    )
            );

            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.unlink(
                            discord,
                            second,
                            secondLink.revision(),
                            "d02-unlink-main-unsafe-" + second,
                            BASE_TIME.plusSeconds(42)
                    )
            );
            assertEquals(discord, store.currentLink(second).orElseThrow().link().discordUserId());

            var replacement = store.setMainMinecraftAccount(
                    changed.subject().subjectId(),
                    new MainMinecraftAccount(first, MainAccountSelectionSource.AUTOMATIC),
                    changed.revision(),
                    BASE_TIME.plusSeconds(43)
            );
            assertEquals(first, replacement.subject().mainMinecraftAccount().orElseThrow().playerId());

            var unlinked = store.unlink(
                    discord,
                    second,
                    secondLink.revision(),
                    "d02-unlink-second-" + second,
                    BASE_TIME.plusSeconds(50)
            );
            assertTrue(unlinked.link().unlinkedAt().isPresent());
            assertFalse(store.currentLink(second).isPresent());
            var detached = store.subjectForMinecraft(second).orElseThrow();
            assertFalse(detached.subject().linkedAcrossPlatforms());
            assertNotEquals(firstLink.subjectId(), detached.subject().subjectId());
            assertEquals(1, store.subjectForDiscord(discord).orElseThrow().subject().minecraftAccountIds().size());
        }
    }

    @Test
    void operationalStateIsRevisionSafeRestartSafeAndBounded() throws Exception {
        UUID player = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(CLEAN_DATABASE, player, "Operational", BASE_TIME);
        DiscordUserId discord = new DiscordUserId("9223372036854775808");

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(CLEAN_DATABASE))) {
            JdbcDiscordModerationPersistenceStore store = new JdbcDiscordModerationPersistenceStore(dataSource);
            var subject = store.ensureMinecraftSubject(player, BASE_TIME);
            var link = store.link(
                    discord,
                    player,
                    DiscordMinecraftLinkSource.DISCORD_CODE,
                    "d02-operational-link-" + player,
                    BASE_TIME.plusSeconds(1)
            );
            subject = store.subjectForDiscord(discord).orElseThrow();

            EnforcementTarget target = new EnforcementTarget(
                    new DiscordIdentityRef(discord),
                    new DiscordGuildScope(new DiscordGuildId("123456789012345678"))
            );
            var enforcement = store.recordEnforcementTarget(
                    subject.subject().subjectId(),
                    target,
                    "d02-enforcement-" + player,
                    BASE_TIME.plusSeconds(2)
            );
            var enforcementReplay = store.recordEnforcementTarget(
                    subject.subject().subjectId(),
                    target,
                    "d02-enforcement-" + player,
                    BASE_TIME.plusSeconds(2)
            );
            assertEquals(enforcement.targetId(), enforcementReplay.targetId());
            assertTrue(enforcementReplay.replayed());

            Instant retainUntil = BASE_TIME.plus(Duration.ofDays(30));
            UUID evidenceId = UUID.randomUUID();
            EvidenceMetadata evidence = new EvidenceMetadata(
                    evidenceId,
                    "d02-evidence-" + evidenceId,
                    subject.subject().subjectId(),
                    Optional.empty(),
                    "111111111111111111",
                    "222222222222222222",
                    "333333333333333333",
                    discord,
                    BASE_TIME.plusSeconds(3),
                    retainUntil,
                    "{\"kind\":\"discord-message\",\"contentStored\":false}"
            );
            assertFalse(store.recordEvidence(evidence).replayed());
            assertTrue(store.recordEvidence(evidence).replayed());
            assertTrue(store.claimDueMaintenance(BASE_TIME.plusSeconds(4), 10, "worker-a", BASE_TIME.plusSeconds(64)).isEmpty());
            var claimed = store.claimDueMaintenance(retainUntil, 1, "worker-a", retainUntil.plusSeconds(60));
            assertEquals(1, claimed.size());
            assertTrue(store.completeMaintenance(
                    claimed.getFirst().workId(),
                    claimed.getFirst().revision(),
                    "worker-a",
                    retainUntil.plusSeconds(1)
            ));
            assertFalse(store.completeMaintenance(
                    claimed.getFirst().workId(),
                    claimed.getFirst().revision(),
                    "worker-a",
                    retainUntil.plusSeconds(2)
            ));

            var securityLock = store.activateSecurityLock(
                    subject.subject().subjectId(),
                    discord,
                    "ACCOUNT_SECURITY",
                    "d02-security-lock-" + player,
                    BASE_TIME.plusSeconds(5)
            );
            assertTrue(store.activateSecurityLock(
                    subject.subject().subjectId(),
                    discord,
                    "ACCOUNT_SECURITY",
                    "d02-security-lock-" + player,
                    BASE_TIME.plusSeconds(5)
            ).replayed());
            var released = store.releaseSecurityLock(
                    securityLock.lockId(),
                    securityLock.revision(),
                    "d02-security-release-" + player,
                    BASE_TIME.plusSeconds(6)
            );
            assertEquals("RELEASED", released.state());
            assertTrue(store.releaseSecurityLock(
                    securityLock.lockId(),
                    securityLock.revision(),
                    "d02-security-release-" + player,
                    BASE_TIME.plusSeconds(6)
            ).replayed());

            ReconciliationState initial = new ReconciliationState(
                    "discord-ban:" + discord.value(),
                    "DISCORD_BAN",
                    discord.value(),
                    "{\"banned\":true}",
                    Optional.empty(),
                    "PENDING",
                    0,
                    Optional.of(BASE_TIME.plusSeconds(10)),
                    Optional.empty(),
                    0
            );
            var created = store.saveReconciliation(initial, -1, BASE_TIME.plusSeconds(7));
            var updated = store.saveReconciliation(
                    new ReconciliationState(
                            created.reconciliationKey(),
                            created.resourceType(),
                            created.resourceId(),
                            created.desiredStateJson(),
                            Optional.of("{\"banned\":false}"),
                            "PENDING",
                            1,
                            Optional.of(BASE_TIME.plusSeconds(20)),
                            Optional.of("REMOTE_STATE_MISMATCH"),
                            created.revision()
                    ),
                    created.revision(),
                    BASE_TIME.plusSeconds(8)
            );
            assertEquals(1, updated.revision());
            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.saveReconciliation(initial, 0, BASE_TIME.plusSeconds(9))
            );
            assertEquals(discord, link.link().discordUserId());
        }
    }

    private static MariaDBContainer<?> database(String databaseName) {
        return new MariaDBContainer<>("mariadb:11.8.3")
                .withDatabaseName(databaseName)
                .withUsername("enthusia_test")
                .withPassword("enthusia_test_password");
    }

    private static void migrate(HikariDataSource dataSource, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .target(target)
                .load()
                .migrate();
    }

    private static String currentFlywayVersion(MariaDBContainer<?> database) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT version FROM flyway_schema_history
                     WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1
                     """)) {
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString("version");
            }
        }
    }

    private static boolean indexExists(MariaDBContainer<?> database, String table, String index) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                     """)) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void assertBackfilledSubject(MariaDBContainer<?> database, UUID playerId) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT m.subject_id, a.player_id, a.selection_source
                     FROM moderation_subject_minecraft_identities m
                     JOIN moderation_subject_main_accounts a ON a.subject_id = m.subject_id
                     WHERE m.player_id = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(playerId, uuid(result.getBytes("subject_id")));
                assertEquals(playerId, uuid(result.getBytes("player_id")));
                assertEquals("AUTOMATIC", result.getString("selection_source"));
            }
        }
    }

    private static UUID uuid(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
