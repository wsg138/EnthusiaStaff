package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModerationDiscordMessageReaderTest {
    @Test
    void messageReadsRequireChannelViewAndMessageHistory() {
        assertFalse(ModerationDiscordMessageReader.hasReadPermissions(false, false));
        assertFalse(ModerationDiscordMessageReader.hasReadPermissions(false, true));
        assertFalse(ModerationDiscordMessageReader.hasReadPermissions(true, false));
        assertTrue(ModerationDiscordMessageReader.hasReadPermissions(true, true));
    }
}
