package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
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
class DiscordAccountCodeExpiryV20IntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-24T05:00:00Z");
    private static final String CODE_HASH = "a".repeat(64);

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_discord_v20_expiry")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void elapsedCodeCommitsExpiredStateBeforeFailureAndSurvivesRestart() throws Exception {
        DiscordUserId discordUserId = new DiscordUserId("18446744073709550121");
        UUID completingPlayer = UUID.randomUUID();

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            identities.ensureDiscordSubject(discordUserId, CREATED_AT);
            JdbcAccountLinkingStore codes = new JdbcAccountLinkingStore(dataSource);
            codes.issueFromDiscord(discordUserId, CODE_HASH, CREATED_AT, CREATED_AT.plusSeconds(300));

            assertThrows(ModerationPersistenceException.class, () -> codes.completeFromMinecraft(
                    CODE_HASH,
                    completingPlayer,
                    "d04-expired-code",
                    CREATED_AT.plusSeconds(301)
            ));
        }

        assertCodeState("EXPIRED", 1L);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcAccountLinkingStore restarted = new JdbcAccountLinkingStore(dataSource);
            assertThrows(ModerationPersistenceException.class, () -> restarted.completeFromMinecraft(
                    CODE_HASH,
                    completingPlayer,
                    "d04-expired-code",
                    CREATED_AT.plusSeconds(600)
            ));
        }

        assertCodeState("EXPIRED", 1L);
    }

    private static void assertCodeState(String expectedState, long expectedRevision) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state, revision
                     FROM discord_link_codes
                     WHERE code_hash = ?
                     """)) {
            statement.setString(1, CODE_HASH);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AssertionError("expected durable account-link code row");
                }
                assertEquals(expectedState, result.getString("state"));
                assertEquals(expectedRevision, result.getLong("revision"));
            }
        }
    }
}
