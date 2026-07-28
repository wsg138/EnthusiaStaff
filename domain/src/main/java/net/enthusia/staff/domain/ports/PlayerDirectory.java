package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPresence;

public interface PlayerDirectory {
    Optional<PlayerIdentity> find(String uuidOrUsername);

    List<PlayerIdentity> search(String prefix, int limit);

    Optional<PlayerPresence> presence(UUID playerId);

    void recordSeen(UUID playerId, String username, PlayerPlatform platform, String serverId, Instant seenAt);

    void recordDisconnected(UUID playerId, String serverId, Instant disconnectedAt);
}
