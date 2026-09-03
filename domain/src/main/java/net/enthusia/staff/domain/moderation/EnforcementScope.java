package net.enthusia.staff.domain.moderation;

public sealed interface EnforcementScope permits DiscordGuildScope, MinecraftServerScope, MinecraftNetworkScope {
    ModerationPlatform platform();

    String stableKey();
}
