package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.CurrentLinkedMinecraftAccount;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.persistence.JdbcAccountLinkingStore;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.MariaDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CurrentLinkedMinecraftAccountsIntegrationTest {
    private static final Instant BASE_TIME = Instant.parse("2026-09-10T12:00:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_current_linked_accounts")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void returnsOnlyOtherCurrentAccountsForTheSameVerifiedDiscordLink() throws Exception {
        UUID target = UUID.randomUUID();
        UUID firstCurrent = UUID.randomUUID();
        UUID secondCurrent = UUID.randomUUID();
        UUID formerAccount = UUID.randomUUID();
        UUID unrelatedAccount = UUID.randomUUID();
        DiscordUserId sharedDiscord = new DiscordUserId("18446744073709550071");
        DiscordUserId unrelatedDiscord = new DiscordUserId("18446744073709550072");
        insertPlayers(target, firstCurrent, secondCurrent, formerAccount, unrelatedAccount);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            JdbcAccountLinkingStore links = new JdbcAccountLinkingStore(dataSource);
            link(identities, sharedDiscord, target, "target", 1);
            link(identities, sharedDiscord, firstCurrent, "first", 2);
            link(identities, sharedDiscord, secondCurrent, "second", 3);
            var former = link(identities, sharedDiscord, formerAccount, "former", 4);
            identities.unlink(
                    sharedDiscord,
                    formerAccount,
                    former.revision(),
                    "current-accounts-unlink-" + formerAccount,
                    BASE_TIME.plusSeconds(5)
            );
            link(identities, unrelatedDiscord, unrelatedAccount, "unrelated", 6);

            assertEquals(List.of(
                    account(firstCurrent, "FirstCurrent", 2),
                    account(secondCurrent, "SecondCurrent", 3)
            ), links.currentLinkedMinecraftAccounts(target, 20));
            assertEquals(List.of(account(firstCurrent, "FirstCurrent", 2)),
                    links.currentLinkedMinecraftAccounts(target, 1));
            assertEquals(List.of(), links.currentLinkedMinecraftAccounts(unrelatedAccount, 20));
            assertThrows(IllegalArgumentException.class, () -> links.currentLinkedMinecraftAccounts(target, 0));
            assertThrows(IllegalArgumentException.class, () -> links.currentLinkedMinecraftAccounts(target, 101));
        }
    }

    @Test
    void projectionHasNoDiscordIdentifierField() {
        assertEquals(List.of("playerId", "currentUsername", "linkedAt"), Arrays.stream(
                CurrentLinkedMinecraftAccount.class.getRecordComponents()
        ).map(component -> component.getName()).toList());
    }

    private static void insertPlayers(UUID... playerIds) throws Exception {
        String[] names = {"Target", "FirstCurrent", "SecondCurrent", "FormerAccount", "Unrelated"};
        for (int index = 0; index < playerIds.length; index++) {
            MariaDbIntegrationSupport.insertPlayer(DATABASE, playerIds[index], names[index], BASE_TIME);
        }
    }

    private static net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink link(
            JdbcDiscordModerationPersistenceStore identities,
            DiscordUserId discordUserId,
            UUID playerId,
            String key,
            long offsetSeconds
    ) {
        return identities.link(
                discordUserId,
                playerId,
                DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "current-accounts-" + key + '-' + playerId,
                BASE_TIME.plusSeconds(offsetSeconds)
        );
    }

    private static CurrentLinkedMinecraftAccount account(UUID playerId, String username, long offsetSeconds) {
        return new CurrentLinkedMinecraftAccount(
                playerId,
                Optional.of(username),
                BASE_TIME.plusSeconds(offsetSeconds)
        );
    }
}
