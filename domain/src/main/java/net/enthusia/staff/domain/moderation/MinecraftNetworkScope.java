package net.enthusia.staff.domain.moderation;

public record MinecraftNetworkScope() implements EnforcementScope {
    @Override
    public ModerationPlatform platform() {
        return ModerationPlatform.MINECRAFT;
    }

    @Override
    public String stableKey() {
        return "minecraft-network";
    }
}
