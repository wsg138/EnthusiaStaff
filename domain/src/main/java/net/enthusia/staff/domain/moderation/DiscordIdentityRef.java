package net.enthusia.staff.domain.moderation;

public record DiscordIdentityRef(DiscordUserId userId) implements ModerationIdentity {
    public DiscordIdentityRef {
        if (userId == null) {
            throw new IllegalArgumentException("discord user id must be present");
        }
    }

    @Override
    public ModerationPlatform platform() {
        return ModerationPlatform.DISCORD;
    }

    @Override
    public String stableKey() {
        return "discord:" + userId.value();
    }
}
