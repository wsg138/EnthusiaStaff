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

    /**
     * Records an observation whose platform argument is only an unverified compatibility hint.
     * Production persistence must store this observation as {@link PlayerPlatform#UNKNOWN}.
     */
    void recordSeen(UUID playerId, String username, PlayerPlatform platform, String serverId, Instant seenAt);

    /**
     * Records a platform value derived from verified runtime provider evidence.
     *
     * <p>The default preserves lightweight test and adapter implementations. Authoritative
     * persistence implementations must override this method to distinguish verified evidence
     * from the compatibility method above.</p>
     */
    default void recordSeenVerified(
            UUID playerId,
            String username,
            PlayerPlatform platform,
            String serverId,
            Instant seenAt
    ) {
        recordSeen(playerId, username, platform, serverId, seenAt);
    }

    void recordDisconnected(UUID playerId, String serverId, Instant disconnectedAt);
}
