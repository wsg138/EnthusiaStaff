package net.enthusia.staff.domain.moderation;

import net.enthusia.staff.common.Checks;

public record MinecraftServerScope(String serverId) implements EnforcementScope {
    public MinecraftServerScope {
        serverId = Checks.nonBlank(serverId, "serverId", 64);
    }

    @Override
    public ModerationPlatform platform() {
        return ModerationPlatform.MINECRAFT;
    }

    @Override
    public String stableKey() {
        return "minecraft-server:" + serverId;
    }
}
