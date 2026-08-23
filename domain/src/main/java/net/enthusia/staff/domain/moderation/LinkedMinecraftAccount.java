package net.enthusia.staff.domain.moderation;

import java.time.Instant;
import java.util.UUID;

public record LinkedMinecraftAccount(UUID playerId, Instant linkedAt, long activeMinutes) {
    public LinkedMinecraftAccount {
        if (playerId == null || linkedAt == null) {
            throw new IllegalArgumentException("linked Minecraft account fields must be present");
        }
        if (activeMinutes < 0) {
            throw new IllegalArgumentException("activeMinutes must not be negative");
        }
    }
}
