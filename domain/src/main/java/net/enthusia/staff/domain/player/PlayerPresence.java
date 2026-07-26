package net.enthusia.staff.domain.player;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PlayerPresence(
        UUID playerId,
        Optional<String> currentServer,
        Optional<String> lastServer,
        Instant lastSeenAt
) {
    public PlayerPresence {
        Objects.requireNonNull(playerId, "playerId");
        currentServer = Objects.requireNonNull(currentServer, "currentServer");
        lastServer = Objects.requireNonNull(lastServer, "lastServer");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
    }

    public boolean online() {
        return currentServer.isPresent();
    }
}
