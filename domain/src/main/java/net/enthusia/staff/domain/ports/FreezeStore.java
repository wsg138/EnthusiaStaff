package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.freeze.FreezeRecord;

public interface FreezeStore {
    FreezeRecord apply(UUID playerId, UUID actorId, String reason, Instant now);

    boolean release(UUID playerId, UUID actorId, String reason, Instant now);

    boolean keepActive(UUID playerId, UUID actorId, String reason, Instant now);

    void disconnected(UUID playerId, Instant offlineExpiration, Instant now);

    Optional<FreezeRecord> readActive(UUID playerId, Instant now);

    List<FreezeRecord> listActive(Instant now, int limit);

    Optional<FreezeRecord> active(UUID playerId, Instant now);
}
