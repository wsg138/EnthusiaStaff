package net.enthusia.staff.domain.moderation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A currently active Minecraft account that shares a verified Discord link with another account.
 * This projection deliberately excludes the Discord identity and all historical link data.
 */
public record CurrentLinkedMinecraftAccount(
        UUID playerId,
        Optional<String> currentUsername,
        Instant linkedAt
) {
    public CurrentLinkedMinecraftAccount {
        if (playerId == null || currentUsername == null || linkedAt == null) {
            throw new IllegalArgumentException("current linked Minecraft account fields must be present");
        }
        currentUsername = currentUsername.filter(value -> !value.isBlank());
    }
}
