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
    void optInIsStrictAndDisabledByDefault() {
        assertFalse(DiscordAccountAgeGateConfiguration.fromEnvironment(Map.of()).enabled());
        assertTrue(DiscordAccountAgeGateConfiguration.fromEnvironment(Map.of(
                DiscordAccountAgeGateConfiguration.ENABLED_KEY, "true")).enabled());
        assertFalse(DiscordAccountAgeGateConfiguration.fromEnvironment(Map.of(
                DiscordAccountAgeGateConfiguration.ENABLED_KEY, "FALSE")).enabled());
        assertThrows(IllegalArgumentException.class,
                () -> DiscordAccountAgeGateConfiguration.fromEnvironment(Map.of(
                        DiscordAccountAgeGateConfiguration.ENABLED_KEY, "yes")));
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
}
