package net.enthusia.staff.domain.moderation;

public record DiscordUserId(String value) {
    public DiscordUserId {
        value = DiscordSnowflake.normalize(value, "discordUserId");
    }

    @Override
    public String toString() {
        return value;
    }
}
