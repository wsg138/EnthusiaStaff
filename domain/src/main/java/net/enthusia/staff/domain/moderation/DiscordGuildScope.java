package net.enthusia.staff.domain.moderation;

public record DiscordGuildScope(DiscordGuildId guildId) implements EnforcementScope {
    public DiscordGuildScope {
        if (guildId == null) {
            throw new IllegalArgumentException("discord guild id must be present");
        }
    }

    @Override
    public ModerationPlatform platform() {
        return ModerationPlatform.DISCORD;
    }

    @Override
    public String stableKey() {
        return "discord-guild:" + guildId.value();
    }
}
