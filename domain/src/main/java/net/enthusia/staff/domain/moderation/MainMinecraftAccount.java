package net.enthusia.staff.domain.moderation;

import java.util.UUID;

public record MainMinecraftAccount(UUID playerId, MainAccountSelectionSource source) {
    public MainMinecraftAccount {
        if (playerId == null || source == null) {
            throw new IllegalArgumentException("main Minecraft account fields must be present");
        }
    }
}
