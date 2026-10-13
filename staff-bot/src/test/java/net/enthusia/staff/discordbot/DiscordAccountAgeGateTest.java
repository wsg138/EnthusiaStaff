package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.junit.jupiter.api.Test;

class DiscordAccountAgeGateTest {
    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");

    @Test
    void underTwentyOneDaysIsRejectedButExactBoundaryIsAllowed() {
        assertTrue(DiscordAccountAgeGateListener.tooNew(NOW.minus(Duration.ofDays(20)), NOW));
        assertTrue(DiscordAccountAgeGateListener.tooNew(
                NOW.minus(Duration.ofDays(21)).plusNanos(1), NOW));
        assertFalse(DiscordAccountAgeGateListener.tooNew(NOW.minus(Duration.ofDays(21)), NOW));
        assertFalse(DiscordAccountAgeGateListener.tooNew(NOW.minus(Duration.ofDays(22)), NOW));
        assertTrue(DiscordAccountAgeGateListener.tooNew(NOW.plusSeconds(1), NOW));
    }

    @Test
    void accountAgeGateIsProductionDefaultOnAndNeverEnforcedBySharedGuildStagingBot() {
        assertTrue(DiscordAccountAgeGateConfiguration.fromEnvironment(
                StaffBotEnvironment.PRODUCTION, Map.of()).enabled());
        assertFalse(DiscordAccountAgeGateConfiguration.fromEnvironment(
                StaffBotEnvironment.STAGING, Map.of()).enabled());
        assertTrue(DiscordAccountAgeGateConfiguration.fromEnvironment(
                StaffBotEnvironment.PRODUCTION,
                Map.of(DiscordAccountAgeGateConfiguration.ENABLED_KEY, "true")).enabled());
        assertFalse(DiscordAccountAgeGateConfiguration.fromEnvironment(
                StaffBotEnvironment.PRODUCTION,
                Map.of(DiscordAccountAgeGateConfiguration.ENABLED_KEY, "FALSE")).enabled());
        assertThrows(IllegalArgumentException.class,
                () -> DiscordAccountAgeGateConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING,
                        Map.of(DiscordAccountAgeGateConfiguration.ENABLED_KEY, "true")));
        assertThrows(IllegalArgumentException.class,
                () -> DiscordAccountAgeGateConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION,
                        Map.of(DiscordAccountAgeGateConfiguration.ENABLED_KEY, "yes")));
    }

    @Test
    void onlyEnabledStaffGatewayGetsPrivilegedMemberJoinIntent() {
        assertFalse(JdaDiscordGateway.gatewayIntents(false, false, false, false)
                .contains(GatewayIntent.GUILD_MEMBERS));
        assertTrue(JdaDiscordGateway.gatewayIntents(false, false, false, true)
                .contains(GatewayIntent.GUILD_MEMBERS));
        assertEquals(JdaDiscordGateway.gatewayIntents(false, false, false),
                JdaDiscordGateway.gatewayIntents(false, false, false, false));
        assertTrue(JdaDiscordGateway.gatewayIntents(true, false, false, false)
                .contains(GatewayIntent.GUILD_MEMBERS));
    }
    @org.junit.jupiter.api.Test
    void registerBypassCommandOnlyWhenEnabled() {
        var defaultNames = JdaStaffModerationListener.commands(false, false, false, false)
                .stream().map(net.dv8tion.jda.api.interactions.commands.build.CommandData::getName).toList();
        var enabledNames = JdaStaffModerationListener.commands(false, false, false, false, true)
                .stream().map(net.dv8tion.jda.api.interactions.commands.build.CommandData::getName).toList();
        assertFalse(defaultNames.contains(DiscordAccountAgeGateListener.COMMAND));
        assertTrue(enabledNames.contains(DiscordAccountAgeGateListener.COMMAND));
        assertFalse(JdaModerationUiPreviewListener.commands().stream()
                .anyMatch(c -> DiscordAccountAgeGateListener.COMMAND.equals(c.getName())));
        assertTrue(JdaModerationUiPreviewListener.commands(true).stream()
                .anyMatch(c -> DiscordAccountAgeGateListener.COMMAND.equals(c.getName())));
    }

    @org.junit.jupiter.api.Test
    void bypassSurvivesRestartAndCanBeRevoked(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder)
            throws Exception {
        java.nio.file.Path file = folder.resolve("discord-age-bypass-ids.txt");
        long account = DiscordAccountAgeBypassStore.parseId("1410303324745371709");
        DiscordAccountAgeBypassStore store = DiscordAccountAgeBypassStore.open(file);
        assertFalse(store.contains(account));
        assertTrue(store.add(account));
        assertFalse(store.add(account));
        assertTrue(DiscordAccountAgeBypassStore.open(file).contains(account));
        assertTrue(store.remove(account));
        assertFalse(store.remove(account));
        assertFalse(DiscordAccountAgeBypassStore.open(file).contains(account));
        assertThrows(IllegalArgumentException.class,
                () -> DiscordAccountAgeBypassStore.parseId("username"));
    }

    @org.junit.jupiter.api.Test
    void invalidFileIsNotTreatedAsAnEmptyBypassList(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder)
            throws Exception {
        java.nio.file.Path file = folder.resolve("bad-bypass-list.txt");
        java.nio.file.Files.writeString(file, "not-a-valid-user-id\\n");
        assertThrows(java.io.IOException.class, () -> DiscordAccountAgeBypassStore.open(file));
    }

}
