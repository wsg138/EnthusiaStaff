package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DiscordChatPublicationTest {
    private static final String DISCORD_MESSAGE_ID = "1234567890123456789";

    @Test
    void preservesActualPostSendIdentityAndUndecoratedText() {
        DiscordChatPublication receipt = new DiscordChatPublication(
                1234567890123456789L, 1234567890123456788L,
                DISCORD_MESSAGE_ID, "rosechat-canonical-message", "hello *world*"
        );
        assertEquals(DISCORD_MESSAGE_ID, receipt.discordMessageId());
        assertEquals("rosechat-canonical-message", receipt.canonicalMessageId());
        assertEquals("hello *world*", receipt.canonicalPlainText());
    }

    @Test
    void rejectsMissingOrSyntheticDiscordSnowflake() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiscordChatPublication(1, 2, "not-a-real-id", "canonical", "text"));
        assertThrows(IllegalArgumentException.class,
                () -> new DiscordChatPublication(1, 2, "123", "canonical", "text"));
        assertThrows(IllegalArgumentException.class,
                () -> new DiscordChatPublication(0, 2, DISCORD_MESSAGE_ID, "canonical", "text"));
    }

    @Test
    void rejectsMissingCanonicalSourceMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiscordChatPublication(1, 2, DISCORD_MESSAGE_ID, " ", "text"));
        assertThrows(IllegalArgumentException.class,
                () -> new DiscordChatPublication(1, 2, DISCORD_MESSAGE_ID, "canonical", " "));
    }
}
