package net.enthusia.staff.domain.moderation;

import java.util.UUID;

public record MinecraftIdentityRef(UUID playerId) implements ModerationIdentity {
    public MinecraftIdentityRef {
        if (playerId == null) {
            throw new IllegalArgumentException("minecraft player id must be present");
        }
    }

    @Override
    public ModerationPlatform platform() {
        return ModerationPlatform.MINECRAFT;
    }

    @Override
    public String stableKey() {
        return "minecraft:" + playerId;
    }
}
