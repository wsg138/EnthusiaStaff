package net.enthusia.staff.discordbot;

import java.util.OptionalLong;
import java.util.regex.Pattern;

/** Strict target carried from a signed Discord launch into the read-only moderation session. */
sealed interface ModerationReadTarget permits ModerationReadTarget.DiscordUser, ModerationReadTarget.MessageContext {
    int DISCORD_PARTS = 2;
    int MESSAGE_PARTS = 4;
    String DISCORD_KIND = "discord";
    String MESSAGE_KIND = "message";
    Pattern SNOWFLAKE = Pattern.compile("[1-9][0-9]{0,19}");

    long userId();

    String key();

    default OptionalLong channelId() {
        return OptionalLong.empty();
    }

    default OptionalLong messageId() {
        return OptionalLong.empty();
    }

    static ModerationReadTarget parse(String key) {
        if (key == null || key.isBlank() || key.length() > 96) {
            throw new IllegalArgumentException("moderation read target is invalid");
        }
        String[] parts = key.split(":", -1);
        if (parts.length == DISCORD_PARTS && DISCORD_KIND.equals(parts[0])) {
            return new DiscordUser(snowflake(parts[1], "user"));
        }
        if (parts.length == MESSAGE_PARTS && MESSAGE_KIND.equals(parts[0])) {
            return new MessageContext(
                    snowflake(parts[1], "channel"),
                    snowflake(parts[2], "message"),
                    snowflake(parts[3], "user")
            );
        }
        throw new IllegalArgumentException("moderation read target is invalid");
    }

    private static long snowflake(String value, String label) {
        if (value == null || !SNOWFLAKE.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " snowflake is invalid");
        }
        try {
            long parsed = Long.parseUnsignedLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(label + " snowflake is invalid");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " snowflake is invalid", exception);
        }
    }

    record DiscordUser(long userId) implements ModerationReadTarget {
        public DiscordUser {
            if (userId <= 0) {
                throw new IllegalArgumentException("user ID must be positive");
            }
        }

        @Override
        public String key() {
            return "discord:" + Long.toUnsignedString(userId);
        }
    }

    record MessageContext(long channelIdValue, long messageIdValue, long userId) implements ModerationReadTarget {
        public MessageContext {
            if (channelIdValue <= 0 || messageIdValue <= 0 || userId <= 0) {
                throw new IllegalArgumentException("message target IDs must be positive");
            }
        }

        @Override
        public OptionalLong channelId() {
            return OptionalLong.of(channelIdValue);
        }

        @Override
        public OptionalLong messageId() {
            return OptionalLong.of(messageIdValue);
        }

        @Override
        public String key() {
            return "message:" + Long.toUnsignedString(channelIdValue)
                    + ":" + Long.toUnsignedString(messageIdValue)
                    + ":" + Long.toUnsignedString(userId);
        }
    }
}
