package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.staff.VanishRecord;

public interface VanishStore {
    enum WriteResult {
        COMMITTED,
        UNCHANGED,
        STAFF_SESSION_NOT_ACTIVE
    }

    enum PreferenceUpdate {
        KEEP,
        SET
    }

    List<VanishRecord> active(int limit);

    Optional<Boolean> preferred(UUID staffId);

    default WriteResult set(
            UUID staffId,
            StaffRank rank,
            boolean vanished,
            UUID actorId,
            Instant now,
            boolean requireActiveStaffSession
    ) {
        return set(
                staffId,
                rank,
                vanished,
                actorId,
                now,
                requireActiveStaffSession,
                PreferenceUpdate.KEEP
        );
    }

    WriteResult set(
            UUID staffId,
            StaffRank rank,
            boolean vanished,
            UUID actorId,
            Instant now,
            boolean requireActiveStaffSession,
            PreferenceUpdate preferenceUpdate
    );
}
