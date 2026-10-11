package net.enthusia.staff.discordbot;

import java.util.Objects;

/**
 * Actual Discord post-send identity for one eligible public Minecraft chat copy.
 *
 * <p>The Discord snowflake must come from JDA's completed message creation,
 * never from a predicted ID, raw chat text, or a pre-send event UUID.
 * The canonical text is the original undecorated Minecraft chat body.</p>
 */
record DiscordChatPublication(
        long guildId,
        long channelId,
        String discordMessageId,
        String canonicalMessageId,
        String canonicalPlainText
) {
    DiscordChatPublication {
        if (guildId <= 0 || channelId <= 0) {
            throw new IllegalArgumentException("published Discord scope must be positive");
        }
        Objects.requireNonNull(discordMessageId, "discordMessageId");
        if (!discordMessageId.matches("[0-9]{16,20}")) {
            throw new IllegalArgumentException("published Discord message ID must be a snowflake");
        }
        Objects.requireNonNull(canonicalMessageId, "canonicalMessageId");
        if (canonicalMessageId.isBlank() || canonicalMessageId.length() > 200) {
            throw new IllegalArgumentException("original canonical message ID is invalid");
        }
        Objects.requireNonNull(canonicalPlainText, "canonicalPlainText");
        if (canonicalPlainText.isBlank() || canonicalPlainText.length() > 4_096) {
            throw new IllegalArgumentException("original canonical message text is invalid");
        }
    }
}
