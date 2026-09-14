package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JdaDiscordPunishmentGatewayTest {
    @Test
    void freshMuteRejectsAnAlreadyPresentManagedRole() {
        DiscordPunishmentGateway.EffectException failure = assertThrows(
                DiscordPunishmentGateway.EffectException.class,
                () -> JdaDiscordPunishmentGateway.requireMuteRoleUnowned(true)
        );

        assertEquals("MUTE_ROLE_ALREADY_PRESENT", failure.errorCode());
        assertFalse(failure.retryable());
    }

    @Test
    void freshMuteAcceptsAnAbsentManagedRole() {
        assertDoesNotThrow(() -> JdaDiscordPunishmentGateway.requireMuteRoleUnowned(false));
    }
}
