package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.UUID;
import net.enthusia.staff.domain.application.AccountLinkingService;
import net.enthusia.staff.domain.application.MainAccountSelectionService;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.moderation.DiscordUserId;
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
