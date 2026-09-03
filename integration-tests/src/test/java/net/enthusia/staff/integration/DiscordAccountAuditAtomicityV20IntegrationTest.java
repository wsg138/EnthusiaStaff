package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.moderation.AccountLinkAudit;
import net.enthusia.staff.domain.moderation.AccountLinkAuditAction;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.persistence.JdbcAccountLinkAuditStore;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordAccountAuditAtomicityV20IntegrationTest {
    private static final Instant BASE_TIME = Instant.parse("2026-08-24T12:00:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_discord_audit_v20")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void failedAuditedMainMutationRollsBackAuditRow() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DiscordUserId discord = new DiscordUserId("18446744073709550101");
        MariaDbIntegrationSupport.insertPlayer(DATABASE, first, "AtomicMainFirst", BASE_TIME);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, second, "AtomicMainSecond", BASE_TIME);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkAuditStore audits = new JdbcAccountLinkAuditStore(dataSource);
            identities.link(discord, first, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "audit-atomic-main-first", BASE_TIME.plusSeconds(1));
            identities.link(discord, second, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "audit-atomic-main-second", BASE_TIME.plusSeconds(2));

            var stale = identities.subjectForDiscord(discord).orElseThrow();
            identities.setMainMinecraftAccount(
                    stale.subject().subjectId(),
                    new MainMinecraftAccount(second, MainAccountSelectionSource.AUTOMATIC),
                    stale.revision(),
                    BASE_TIME.plusSeconds(3)
            );

            Actor admin = new Actor(UUID.randomUUID(), "AtomicAdmin", StaffRank.ADMIN);
            String auditKey = "audit-atomic-main-rollback";
            AccountLinkAudit audit = new AccountLinkAudit(
                    auditKey,
                    admin,
                    AccountLinkAuditAction.MAIN_OVERRIDE_SET,
                    Optional.of(discord),
                    Optional.of(first),
                    "Atomic rollback proof",
                    BASE_TIME.plusSeconds(4)
            );

            assertThrows(ModerationPersistenceException.class, () -> identities.setMainMinecraftAccountWithAudit(
                    stale.subject().subjectId(),
                    new MainMinecraftAccount(first, MainAccountSelectionSource.STAFF_OVERRIDE),
                    stale.revision(),
                    BASE_TIME.plusSeconds(4),
                    audit
            ));

            assertFalse(audits.findByOperationKey(auditKey).isPresent());
            assertEquals(second, identities.subjectForDiscord(discord).orElseThrow()
                    .subject().mainMinecraftAccount().orElseThrow().playerId());
        }
    }

    @Test
    void failedAuditedLinkMutationRollsBackAuditRow() throws Exception {
        UUID playerId = UUID.randomUUID();
        DiscordUserId currentOwner = new DiscordUserId("18446744073709550111");
        DiscordUserId attemptedOwner = new DiscordUserId("18446744073709550112");
        MariaDbIntegrationSupport.insertPlayer(DATABASE, playerId, "AtomicLinkPlayer", BASE_TIME.plusSeconds(10));

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkAuditStore audits = new JdbcAccountLinkAuditStore(dataSource);
            identities.link(
                    currentOwner,
                    playerId,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "audit-atomic-existing-link",
                    BASE_TIME.plusSeconds(11)
            );

            Actor admin = new Actor(UUID.randomUUID(), "AtomicAdmin", StaffRank.ADMIN);
            String auditKey = "audit-atomic-link-rollback";
            AccountLinkAudit audit = new AccountLinkAudit(
                    auditKey,
                    admin,
                    AccountLinkAuditAction.FORCE_LINK,
                    Optional.of(attemptedOwner),
                    Optional.of(playerId),
                    "Atomic link rollback proof",
                    BASE_TIME.plusSeconds(12)
            );

            assertThrows(ModerationPersistenceException.class, () -> identities.linkWithAudit(
                    attemptedOwner,
                    playerId,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    "audit-atomic-conflicting-link",
                    BASE_TIME.plusSeconds(12),
                    audit
            ));

            assertFalse(audits.findByOperationKey(auditKey).isPresent());
            assertEquals(currentOwner, identities.currentLink(playerId).orElseThrow().link().discordUserId());
        }
    }
}
