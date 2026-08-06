package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPresence;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PlayerDirectoryIdentityIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_identity_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    private static MariaDbRuntime runtime;
    private PlayerDirectory directory;

    @BeforeAll
    static void initializeRuntime() {
        runtime = MariaDb.initialize(databaseConfig(DATABASE));
    }

    @AfterAll
    static void closeRuntime() {
        runtime.close();
    }

    @BeforeEach
    void clearDirectory() throws SQLException {
        try (java.sql.Connection database = connection(DATABASE);
             Statement statement = database.createStatement()) {
            statement.executeUpdate("DELETE FROM player_names");
            statement.executeUpdate("DELETE FROM players");
        }
        directory = runtime.playerDirectory();
    }

    @Test
    void preservesBedrockAliasAndHistoricalRename() {
        UUID playerId = UUID.fromString("95777ea2-2911-4a47-86f2-90bd1bd12a2f");
        Instant firstSeen = Instant.parse("2026-08-06T12:00:00Z");
        Instant renamedAt = firstSeen.plus(2, ChronoUnit.HOURS);

        directory.recordSeen(playerId, "*BedrockOne", PlayerPlatform.BEDROCK, "hub", firstSeen);
        directory.recordSeen(playerId, "*BedrockTwo", PlayerPlatform.BEDROCK, "smp", renamedAt);

        PlayerResolution.Resolved current = assertInstanceOf(
                PlayerResolution.Resolved.class,
                directory.resolve("*bedrocktwo")
        );
        assertEquals(PlayerResolution.MatchKind.CURRENT_USERNAME, current.matchKind());
        assertEquals("*BedrockTwo", current.identity().currentUsername().orElseThrow());
        assertEquals(PlayerPlatform.BEDROCK, current.identity().platform());

        PlayerResolution.Resolved historical = assertInstanceOf(
                PlayerResolution.Resolved.class,
                directory.resolve("*BEDROCKONE")
        );
        assertEquals(PlayerResolution.MatchKind.HISTORICAL_USERNAME, historical.matchKind());
        assertEquals(playerId, historical.identity().playerId());

        List<PlayerIdentity> prefixMatches = directory.search("*bedrock", 10);
        assertEquals(1, prefixMatches.size());
        assertEquals(playerId, prefixMatches.getFirst().playerId());
    }

    @Test
    void verifiedBedrockRepairsLegacyJavaAndCannotBeDowngraded() {
        UUID playerId = UUID.fromString("c414dcab-d4ce-4178-8f2c-c4979fba9b82");
        Instant base = Instant.parse("2026-08-06T13:00:00Z");

        directory.recordSeen(playerId, "LegacyName", PlayerPlatform.JAVA, "proxy", base.plusSeconds(30));
        directory.recordSeen(playerId, "LegacyName", PlayerPlatform.BEDROCK, "hub", base);
        directory.recordSeen(playerId, "LegacyName", PlayerPlatform.UNKNOWN, "smp", base.plusSeconds(60));
        directory.recordSeen(playerId, "LegacyName", PlayerPlatform.JAVA, "smp", base.plusSeconds(90));

        PlayerIdentity identity = directory.find(playerId.toString()).orElseThrow();
        assertEquals(PlayerPlatform.BEDROCK, identity.platform());
    }

    @Test
    void outOfOrderWritesKeepLatestNameAndPresenceButRetainHistory() {
        UUID playerId = UUID.fromString("e9dcc75f-ed16-4d89-9f56-42eaec0986cf");
        Instant older = Instant.parse("2026-08-06T14:00:00Z");
        Instant newer = older.plus(5, ChronoUnit.MINUTES);

        directory.recordSeen(playerId, "CurrentName", PlayerPlatform.JAVA, "smp", newer);
        directory.recordSeen(playerId, "OldName", PlayerPlatform.JAVA, "hub", older);

        PlayerIdentity identity = directory.find(playerId.toString()).orElseThrow();
        assertEquals("CurrentName", identity.currentUsername().orElseThrow());
        assertEquals(newer, identity.lastSeenAt());
        assertEquals(playerId, directory.find("OldName").orElseThrow().playerId());

        PlayerPresence presence = directory.presence(playerId).orElseThrow();
        assertEquals("smp", presence.currentServer().orElseThrow());
        assertEquals(newer, presence.lastSeenAt());
    }

    @Test
    void staleDisconnectCannotClearANewerConnection() {
        UUID playerId = UUID.fromString("517ebd6b-56d9-4027-9be2-33f2a7fcc243");
        Instant firstConnection = Instant.parse("2026-08-06T15:00:00Z");
        Instant newerConnection = firstConnection.plus(10, ChronoUnit.MINUTES);

        directory.recordSeen(playerId, "ReconnectName", PlayerPlatform.JAVA, "hub", firstConnection);
        directory.recordSeen(playerId, "ReconnectName", PlayerPlatform.JAVA, "hub", newerConnection);
        directory.recordDisconnected(playerId, "hub", firstConnection.plusSeconds(30));

        PlayerPresence presence = directory.presence(playerId).orElseThrow();
        assertTrue(presence.online());
        assertEquals("hub", presence.currentServer().orElseThrow());
        assertEquals(newerConnection, presence.lastSeenAt());
    }

    @Test
    void rejectsUntrustedAliasShapesWithoutInferringPlatform() {
        UUID playerId = UUID.fromString("1fa0b562-bd07-4ef0-b2fc-0cac0f98bd08");
        Instant now = Instant.parse("2026-08-06T16:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> directory.recordSeen(
                playerId,
                "**spoofed",
                PlayerPlatform.BEDROCK,
                "hub",
                now
        ));
        assertThrows(IllegalArgumentException.class, () -> directory.search("**", 10));
    }
}
