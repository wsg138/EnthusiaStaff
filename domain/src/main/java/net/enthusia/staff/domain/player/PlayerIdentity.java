package net.enthusia.staff.domain.player;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record PlayerIdentity(
        UUID playerId,
        Optional<String> currentUsername,
        PlayerPlatform platform,
        Instant firstSeenAt,
        Instant lastSeenAt
) {
    public PlayerIdentity {
        if (playerId == null || currentUsername == null || platform == null
                || firstSeenAt == null || lastSeenAt == null) {
            throw new IllegalArgumentException("player identity fields must be present");
        }
    }
}
