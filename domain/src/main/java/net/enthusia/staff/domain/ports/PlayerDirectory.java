package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPresence;
import net.enthusia.staff.domain.player.PlayerResolution;

public interface PlayerDirectory {
    Optional<PlayerIdentity> find(String uuidOrUsername);

    default PlayerResolution resolve(String uuidOrUsername) {
        return find(uuidOrUsername)
                .<PlayerResolution>map(identity -> new PlayerResolution.Resolved(
                        identity,
                        PlayerResolution.MatchKind.CURRENT_USERNAME
                ))
                .orElseGet(PlayerResolution.Missing::new);
    }

    List<PlayerIdentity> search(String prefix, int limit);

    Optional<PlayerPresence> presence(UUID playerId);

    void recordSeen(UUID playerId, String username, PlayerPlatform platform, String serverId, Instant seenAt);

    void recordDisconnected(UUID playerId, String serverId, Instant disconnectedAt);
}
