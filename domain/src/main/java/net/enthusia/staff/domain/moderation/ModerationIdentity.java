package net.enthusia.staff.domain.moderation;

public sealed interface ModerationIdentity permits DiscordIdentityRef, MinecraftIdentityRef {
    ModerationPlatform platform();

    String stableKey();
}
