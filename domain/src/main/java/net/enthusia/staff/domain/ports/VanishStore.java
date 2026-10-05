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

    default Optional<VanishRecord> active(UUID staffId) {
        if (staffId == null) {
            throw new IllegalArgumentException("staffId must be present");
        }
        return active(10_000).stream()
                .filter(record -> record.staffId().equals(staffId))
                .findFirst();
    }

    default Optional<Boolean> preferred(UUID staffId) {
        if (staffId == null) {
            throw new IllegalArgumentException("staffId must be present");
        }
        return Optional.empty();
    }

    WriteResult set(
            UUID staffId,
            StaffRank rank,
            boolean vanished,
            UUID actorId,
            Instant now,
            boolean requireActiveStaffSession
    );

    default WriteResult set(
            UUID staffId,
            StaffRank rank,
            boolean vanished,
            UUID actorId,
            Instant now,
            boolean requireActiveStaffSession,
            String selectedGameMode
    ) {
        return set(staffId, rank, vanished, actorId, now, requireActiveStaffSession);
    }

    default WriteResult set(
            UUID staffId,
            StaffRank rank,
            boolean vanished,
            UUID actorId,
            Instant now,
            boolean requireActiveStaffSession,
            String selectedGameMode,
            PreferenceUpdate preferenceUpdate
    ) {
        if (preferenceUpdate == null) {
            throw new IllegalArgumentException("preferenceUpdate must be present");
        }
        return set(staffId, rank, vanished, actorId, now, requireActiveStaffSession, selectedGameMode);
    }
}
