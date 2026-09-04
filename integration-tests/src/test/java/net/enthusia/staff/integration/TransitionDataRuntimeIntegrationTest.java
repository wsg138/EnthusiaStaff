package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.DiscordSrvLinkProvider;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.MirrorResult;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.persistence.TransitionDataRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class TransitionDataRuntimeIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-04T04:00:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_transition")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void cleanDatabaseMigratesAndDiscordSrvImportSurvivesRestart() {
        UUID player = UUID.randomUUID();
        DiscordUserId discord = new DiscordUserId("123456789012345678");
        var database = MariaDbIntegrationSupport.databaseConfig(DATABASE);

        try (TransitionDataRuntime runtime = TransitionDataRuntime.open(database)) {
            runtime.players().recordSeen(player, "TransitionUser", PlayerPlatform.UNKNOWN, "SMP", NOW);
            DiscordSrvMigrationService migration = new DiscordSrvMigrationService(
                    Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC), runtime.identities());
            var report = migration.importSnapshot(new SnapshotProvider(Map.of(discord.value(), player)));
            assertEquals(1, report.imported());
            assertTrue(report.conflicts().isEmpty());
        }

        try (TransitionDataRuntime restarted = TransitionDataRuntime.open(database)) {
            assertEquals(discord, restarted.identities().currentLink(player).orElseThrow().link().discordUserId());
            assertTrue(restarted.identities().subjectForDiscord(discord).orElseThrow()
                    .subject().minecraftAccountIds().contains(player));
        }
    }

    private record SnapshotProvider(Map<String, UUID> links) implements DiscordSrvLinkProvider {
        @Override
        public Map<String, UUID> snapshotLinks() {
            return links;
        }

        @Override
        public MirrorResult mirrorMain(String discordUserId, UUID minecraftPlayerId) {
            return MirrorResult.UNAVAILABLE;
        }
    }
}
