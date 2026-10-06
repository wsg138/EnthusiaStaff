package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable per-staff preferences that must follow the staff member across backends and restarts. */
public interface StaffPreferenceStore {
    default Optional<Boolean> toolInventoryEnabled(UUID staffId) {
        if (staffId == null) {
            throw new IllegalArgumentException("staffId must be present");
        }
        return Optional.empty();
    }

    void setToolInventoryEnabled(UUID staffId, boolean enabled, Instant now);
}
