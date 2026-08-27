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
import java.util.Optional;
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
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
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
class DiscordAccountUnlinkAtomicityV20IntegrationTest {
    private static final Instant BASE_TIME = Instant.parse("2026-08-24T04:10:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_discord_v20_unlink")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void currentMainReplacementCommitsOnlyWithSuccessfulUnlink() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, first, "AtomicFirst", BASE_TIME);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, second, "AtomicSecond", BASE_TIME);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, unrelated, "AtomicOther", BASE_TIME);
        DiscordUserId discord = new DiscordUserId("18446744073709550101");

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            var firstLink = seedAtomicLinks(identities, discord, first, second);
            verifyInvalidReplacementIsAtomic(identities, discord, first, unrelated, firstLink.revision());
            verifySuccessfulSelfUnlink(dataSource, identities, codes, discord, first, second);
        }
    }

    private static net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink seedAtomicLinks(
            JdbcDiscordModerationPersistenceStore identities,
            DiscordUserId discord,
            UUID first,
            UUID second
    ) {
        var firstLink = identities.link(
                discord, first, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "d04-atomic-first-" + first, BASE_TIME.plusSeconds(1));
        identities.link(
                discord, second, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "d04-atomic-second-" + second, BASE_TIME.plusSeconds(2));
        assertEquals(first, mainPlayer(identities, discord));
        return firstLink;
    }

    private static void verifyInvalidReplacementIsAtomic(
            JdbcDiscordModerationPersistenceStore identities,
            DiscordUserId discord,
            UUID first,
            UUID unrelated,
            long revision
    ) {
        assertThrows(ModerationPersistenceException.class, () -> identities.unlink(
                discord,
                first,
                revision,
                Optional.of(new MainMinecraftAccount(unrelated, MainAccountSelectionSource.AUTOMATIC)),
                "d04-atomic-invalid-replacement",
                BASE_TIME.plusSeconds(3)
        ));
        assertEquals(first, mainPlayer(identities, discord));
        assertTrue(identities.currentLink(first).isPresent());
    }

    private static void verifySuccessfulSelfUnlink(
            HikariDataSource dataSource,
            JdbcDiscordModerationPersistenceStore identities,
            JdbcAccountLinkingStore codes,
            DiscordUserId discord,
            UUID first,
            UUID second
    ) {
        Clock clock = Clock.fixed(BASE_TIME.plusSeconds(4), ZoneOffset.UTC);
        MainAccountSelectionService mains = new MainAccountSelectionService(
                clock,
                identities,
                playerId -> OptionalLong.of(playerId.equals(first) ? 200L : 100L),
                new DefaultAuthorizationPolicy(),
                new JdbcAccountLinkAuditStore(dataSource)
        );
        AccountLinkingService linking = new AccountLinkingService(
                clock, new SecureRandom(), identities, codes, ignored -> true, mains);
        assertTrue(linking.unlinkFromMinecraft(first, true));
        assertFalse(identities.currentLink(first).isPresent());
        assertEquals(second, mainPlayer(identities, discord));
    }

    @Test
    void staffReassignmentOfCurrentMainPreservesOldSubjectMainAndHistory() throws Exception {
        UUID moving = UUID.randomUUID();
        UUID remaining = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, moving, "ReassignMoving", BASE_TIME.plusSeconds(20));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, remaining, "ReassignRemaining", BASE_TIME.plusSeconds(20));
        DiscordUserId oldDiscord = new DiscordUserId("18446744073709550111");
        DiscordUserId newDiscord = new DiscordUserId("18446744073709550112");
        Actor admin = new Actor(UUID.randomUUID(), "D04AtomicAdmin", StaffRank.ADMIN);
        Clock clock = Clock.fixed(BASE_TIME.plusSeconds(30), ZoneOffset.UTC);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore history = new JdbcAccountLinkingStore(dataSource);
            JdbcAccountLinkAuditStore audits = new JdbcAccountLinkAuditStore(dataSource);
            seedReassignmentLinks(identities, oldDiscord, moving, remaining);
            performReassignment(dataSource, identities, audits, oldDiscord, newDiscord, moving, remaining, admin, clock);
            assertReassignmentState(identities, history, oldDiscord, newDiscord, moving, remaining);
        }
    }

    private static void seedReassignmentLinks(
            JdbcDiscordModerationPersistenceStore identities,
            DiscordUserId oldDiscord,
            UUID moving,
            UUID remaining
    ) {
        identities.link(
                oldDiscord, moving, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "d04-reassign-moving-" + moving, BASE_TIME.plusSeconds(21));
        identities.link(
                oldDiscord, remaining, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "d04-reassign-remaining-" + remaining, BASE_TIME.plusSeconds(22));
        assertEquals(moving, mainPlayer(identities, oldDiscord));
    }

    private static void performReassignment(
            HikariDataSource dataSource,
            JdbcDiscordModerationPersistenceStore identities,
            JdbcAccountLinkAuditStore audits,
            DiscordUserId oldDiscord,
            DiscordUserId newDiscord,
            UUID moving,
            UUID remaining,
            Actor admin,
            Clock clock
    ) {
        MainAccountSelectionService mains = new MainAccountSelectionService(
                clock,
                identities,
                playerId -> OptionalLong.of(playerId.equals(moving) ? 500L : 250L),
                new DefaultAuthorizationPolicy(),
                audits
        );
        AccountLinkRecoveryService recovery = new AccountLinkRecoveryService(
                clock, new DefaultAuthorizationPolicy(), identities, audits, mains);
        recovery.reassign(admin, newDiscord, moving, "d04-atomic-reassign");
    }

    private static void assertReassignmentState(
            JdbcDiscordModerationPersistenceStore identities,
            JdbcAccountLinkingStore history,
            DiscordUserId oldDiscord,
            DiscordUserId newDiscord,
            UUID moving,
            UUID remaining
    ) {
        assertEquals(newDiscord, identities.currentLink(moving).orElseThrow().link().discordUserId());
        assertEquals(remaining, mainPlayer(identities, oldDiscord));
        assertEquals(moving, mainPlayer(identities, newDiscord));
        assertEquals(2, history.historyForMinecraft(moving).size());
        assertEquals(1, history.historyForMinecraft(moving).stream()
                .filter(link -> link.link().unlinkedAt().isPresent()).count());
        assertEquals(1, history.historyForMinecraft(moving).stream()
                .filter(link -> link.link().unlinkedAt().isEmpty()).count());
    }

    private static UUID mainPlayer(
            JdbcDiscordModerationPersistenceStore identities,
            DiscordUserId discordUserId
    ) {
        return identities.subjectForDiscord(discordUserId)
                .orElseThrow()
                .subject()
                .mainMinecraftAccount()
                .orElseThrow()
                .playerId();
    }
}
