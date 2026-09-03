package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.MariaDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordStaffReadRuntimeIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");
    private static final String DATABASE_PASSWORD = Character.toString('p').repeat(24);

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_d06_reads")
            .withUsername("enthusia")
            .withPassword(DATABASE_PASSWORD);

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void readsAuthoritativeLinksBedrockIdentityAndExistingStaffNotes() throws Exception {
        UUID playerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID actorId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID noteId = UUID.fromString("99999999-8888-7777-6666-555555555555");
        DiscordUserId discord = new DiscordUserId("123456789012345678");
        MariaDbIntegrationSupport.insertPlayer(DATABASE, playerId, "BedrockLinked", NOW);
        setBedrock(playerId);
        insertNote(noteId, playerId, actorId);

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore store = new JdbcDiscordModerationPersistenceStore(dataSource);
            store.ensureMinecraftSubject(playerId, NOW);
            store.link(
                    discord,
                    playerId,
                    DiscordMinecraftLinkSource.DISCORD_CODE,
                    "d06-read-link-" + playerId,
                    NOW.plusSeconds(1)
            );
        }

        try (DiscordStaffReadRuntime reads = DiscordStaffReadRuntime.open(
                MariaDbIntegrationSupport.databaseConfig(DATABASE),
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC)
        )) {
            assertTrue(reads.subjectForDiscord(discord).isPresent());
            assertEquals(1L, reads.linkHistoryCountForDiscord(discord));
            assertEquals(PlayerPlatform.BEDROCK, reads.player(playerId).orElseThrow().platform());

            PlayerResolution.Resolved resolved = assertInstanceOf(
                    PlayerResolution.Resolved.class,
                    reads.resolvePlayer("BedrockLinked")
            );
            assertEquals(playerId, resolved.identity().playerId());
            assertEquals(PlayerPlatform.BEDROCK, resolved.identity().platform());

            var notes = reads.recentNotes(playerId, 8);
            assertEquals(1, notes.size());
            assertEquals(noteId, notes.getFirst().noteId());
            assertEquals("D06 read-only integration note", notes.getFirst().noteText());
        }
    }

    private static void setBedrock(UUID playerId) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE players SET platform = 'BEDROCK' WHERE player_id = ?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(playerId));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertNote(UUID noteId, UUID targetId, UUID actorId) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO staff_notes(note_id, target_id, actor_id, note_text, created_at)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(noteId));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(targetId));
            statement.setBytes(3, MariaDbIntegrationSupport.uuidBytes(actorId));
            statement.setString(4, "D06 read-only integration note");
            statement.setTimestamp(5, Timestamp.from(NOW));
            assertEquals(1, statement.executeUpdate());
        }
    }
}
