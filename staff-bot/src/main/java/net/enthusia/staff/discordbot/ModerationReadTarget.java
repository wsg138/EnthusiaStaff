package net.enthusia.staff.discordbot;

import java.util.OptionalLong;
import java.util.regex.Pattern;

/** Strict target carried from a signed Discord launch into the read-only moderation session. */
sealed interface ModerationReadTarget permits ModerationReadTarget.DiscordUser,
        ModerationReadTarget.DiscordUserContext,
        ModerationReadTarget.MessageContext {
    int DISCORD_PARTS = 2;
    int DISCORD_CONTEXT_PARTS = 3;
    int MESSAGE_PARTS = 4;
    String DISCORD_KIND = "discord";
    String DISCORD_CONTEXT_KIND = "discord-channel";
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
        validateKey(key);
        return parseParts(key.split(":", -1));
    }

    private static void validateKey(String key) {
        if (key == null) {
            throw invalidTarget();
        }
        if (key.isBlank() || key.length() > 96) {
            throw invalidTarget();
        }
    }

    private static ModerationReadTarget parseParts(String[] parts) {
        return switch (parts[0]) {
            case DISCORD_KIND -> parseDiscordUser(parts);
            case DISCORD_CONTEXT_KIND -> parseDiscordUserContext(parts);
            case MESSAGE_KIND -> parseMessageContext(parts);
            default -> throw invalidTarget();
        };
    }

    private static ModerationReadTarget parseDiscordUser(String[] parts) {
        requireParts(parts, DISCORD_PARTS);
        return new DiscordUser(snowflake(parts[1], "user"));
    }

    private static ModerationReadTarget parseDiscordUserContext(String[] parts) {
        requireParts(parts, DISCORD_CONTEXT_PARTS);
        return new DiscordUserContext(
                snowflake(parts[1], "channel"),
                snowflake(parts[2], "user")
        );
    }

    private static ModerationReadTarget parseMessageContext(String[] parts) {
        requireParts(parts, MESSAGE_PARTS);
        return new MessageContext(
                snowflake(parts[1], "channel"),
                snowflake(parts[2], "message"),
                snowflake(parts[3], "user")
        );
    }

    private static void requireParts(String[] parts, int expected) {
        if (parts.length != expected) {
            throw invalidTarget();
        }
    }

    private static IllegalArgumentException invalidTarget() {
        return new IllegalArgumentException("moderation read target is invalid");
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

    record DiscordUserContext(long channelIdValue, long userId) implements ModerationReadTarget {
        public DiscordUserContext {
            if (channelIdValue <= 0 || userId <= 0) {
                throw new IllegalArgumentException("Discord channel target IDs must be positive");
            }
        }

        @Override
        public OptionalLong channelId() {
            return OptionalLong.of(channelIdValue);
        }

        @Override
        public String key() {
            return "discord-channel:" + Long.toUnsignedString(channelIdValue)
                    + ":" + Long.toUnsignedString(userId);
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
