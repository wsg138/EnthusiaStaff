package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.staff.VanishRecord;

public interface VanishStore {
    List<VanishRecord> active(int limit);

    void set(UUID staffId, StaffRank rank, boolean vanished, UUID actorId, Instant now);
}
