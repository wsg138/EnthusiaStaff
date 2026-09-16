package net.enthusia.staff.domain.discord;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DiscordPunishmentStateTest {
    @Test
    void failedRemovalRemainsActiveForExplicitRetry() {
        assertFalse(DiscordPunishmentState.FAILED_REMOVE.terminal());
        assertTrue(DiscordPunishmentState.FAILED_REMOVE.removalPending());
    }
}
