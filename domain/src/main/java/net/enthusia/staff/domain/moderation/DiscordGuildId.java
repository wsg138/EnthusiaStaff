package net.enthusia.staff.domain.moderation;

public record DiscordGuildId(String value) {
    public DiscordGuildId {
        value = DiscordSnowflake.normalize(value, "discordGuildId");
    }

    @Override
    public String toString() {
        return value;
    }
}
